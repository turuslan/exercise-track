import * as TypeOrm from "typeorm";
import * as Url from "node:url";
import { debugDb, debugTypeormSynchronize, debugUid } from "./debug";
import { filterGroup, mapGroup, TimeByDevice, TimesByDevice } from "./types";
import { cmp } from "./cmp";

let this_device: DbDevice = null!;

const db_path = debugDb() ?? Url.fileURLToPath(
  Url.resolve(import.meta.url, "../exercise.db"),
);
const migrations_path = Url.fileURLToPath(
  Url.resolve(import.meta.url, "migrations/*.ts"),
);

function makeUid() {
  return `_${Math.floor(0x100000000 * Math.random())}`;
}

async function findOrInsert<T, K extends keyof T>(
  tx: TypeOrm.EntityManager,
  type: new () => T,
  key: K,
  value: T[K],
  init: (entity: T) => void | Promise<void>,
) {
  let entity = await tx.findOne(type, { where: { [key]: value } }) as T;
  if (entity === null) {
    entity = new type();
    entity[key] = value;
    await init(entity);
    await tx.insert(type, entity as any);
  }
  return entity;
}

@TypeOrm.Entity()
class DbTime {
  @TypeOrm.PrimaryColumn("integer")
  time_ms: number = undefined!;
  @TypeOrm.Column("integer")
  device_lid: number = undefined!;
}

@TypeOrm.Entity()
class DbDevice {
  @TypeOrm.PrimaryGeneratedColumn()
  lid: number = undefined!;
  @TypeOrm.Column("text", { unique: true })
  uid: string = undefined!;
  @TypeOrm.Column("text")
  name: string = undefined!;
}

@TypeOrm.Entity()
class DbKv {
  @TypeOrm.PrimaryColumn("text")
  key: string = undefined!;
  @TypeOrm.Column("text")
  value: string = undefined!;
}

const db = new TypeOrm.DataSource({
  type: "better-sqlite3",
  database: db_path,
  entities: [DbTime, DbDevice, DbKv],
  migrations: [migrations_path],
  synchronize: debugTypeormSynchronize(),
});
export const AppDataSource = db;
let _db_ready: Promise<void> = null!;
async function dbReady() {
  _db_ready ??= db.initialize().then(() =>
    db.transaction(async (tx) => {
      const this_device_uid_kv = await findOrInsert(
        tx,
        DbKv,
        "key",
        "this_device_uid",
        (kv) => {
          kv.value = debugUid() ?? makeUid();
        },
      );
      this_device = await findOrInsert(
        tx,
        DbDevice,
        "uid",
        this_device_uid_kv.value,
        (device) => {
          device.uid = this_device_uid_kv.value;
          device.name = "this device";
        },
      );
    })
  );
  await _db_ready;
}

async function whereDeviceUid(uid: string | null) {
  await dbReady();

  if (uid === null) return {};
  const device = uid !== null
    ? await db.manager.findOne(DbDevice, { where: { uid } })
    : null;
  if (device === null) return null;
  return { device_lid: device.lid };
}

export async function dbInsertTime(time: number) {
  await dbReady();

  const row = new DbTime();
  row.time_ms = time;
  row.device_lid = this_device.lid;
  await db.manager.insert(DbTime, row);
}

export async function dbSelectTimeRange(
  min: number,
  max: number | null,
  device_uid: string | null,
): Promise<TimesByDevice> {
  await dbReady();

  max ??= Date.now();
  const where_device = await whereDeviceUid(device_uid);
  if (where_device === null) return {};
  const rows = await db.manager.find(DbTime, {
    where: {
      time_ms: TypeOrm.Between(min, max),
      ...where_device,
    },
  });
  const lid_times = Object.groupBy(
    rows.map((row) => row.time_ms),
    (row, i) => rows[i].device_lid,
  ) as Record<string, number[]>;
  const devices = await db.manager.find(DbDevice, {
    where: { lid: TypeOrm.In(Object.keys(lid_times)) },
  });
  const lid_uid = Object.fromEntries(
    devices.map((device) => [device.lid, device.uid]),
  );
  const group = filterGroup(
    mapGroup(
      lid_times,
      (lid, times) => [lid_uid[lid] ?? null!, times.sort(cmp)],
    ),
    (uid) => uid !== null,
  );
  if (device_uid !== null) group[device_uid] ??= [];
  return group;
}

export async function dbSelectTimeRangeMany(devices: TimeByDevice) {
  await dbReady();

  const times: TimesByDevice = {};
  for (const [device_uid, min] of Object.entries(devices)) {
    const device_times = await dbSelectTimeRange(min, null, device_uid);
    Object.assign(times, device_times);
  }
  return times;
}

export async function dbMaxTime(
  device_uid: string,
): Promise<number | null> {
  await dbReady();

  const where_device = await whereDeviceUid(device_uid);
  if (where_device === null) return null;
  return await db.manager.maximum(DbTime, "time_ms");
}

export async function dbMaxTimeAll(): Promise<TimeByDevice> {
  await dbReady();

  const devices: TimeByDevice = {};
  for (const device of await dbDevices()) {
    devices[device.uid] = await dbMaxTime(device.uid) ?? 0;
  }
  return devices;
}

export async function dbMergeTime(device_uid: string, times: number[]) {
  await dbReady();

  if (times.length === 0) return [];
  const where_device = await whereDeviceUid(device_uid);
  if (where_device === null) return [];
  const min = Math.min(...times);
  const max = Math.max(...times);
  const existing = await dbSelectTimeRange(min, max, device_uid);
  times = times.filter((time) => !existing[device_uid].includes(time));
  if (times.length === 0) return [];
  await db.manager.insert(
    DbTime,
    times.map((time) => {
      const db_time = new DbTime();
      db_time.time_ms = time;
      db_time.device_lid = where_device.device_lid!;
      return db_time;
    }),
  );
  return times;
}

export async function dbMergeTimeMany(
  devices: TimesByDevice,
): Promise<TimesByDevice> {
  await dbReady();
  const added: TimesByDevice = {};
  for (const [device_uid, times] of Object.entries(devices)) {
    added[device_uid] = await dbMergeTime(device_uid, times);
  }
  return added;
}

export async function thisDeviceUid() {
  await dbReady();

  return this_device.uid;
}

export async function dbDevices() {
  await dbReady();

  const devices = await db.manager.find(
    DbDevice,
    { where: { uid: TypeOrm.Not(this_device.uid) } },
  );
  return devices.map((device) => ({ uid: device.uid, name: device.name }));
}

export async function dbUpsertDevice(uid: string, name: string) {
  await dbReady();

  await db.manager.upsert(DbDevice, { uid, name }, ["uid"]);
}

export async function dbRemoveDevice(uid: string) {
  await dbReady();

  await db.manager.delete(DbDevice, { uid });
}
