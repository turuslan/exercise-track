import * as TypeOrm from "typeorm";
import * as Url from "node:url";

let this_device: DbDevice = null!;

const db_path = Url.fileURLToPath(
  Url.resolve(import.meta.url, "../exercise.db"),
);
const migrations_path = Url.fileURLToPath(
  Url.resolve(import.meta.url, "migrations/*.ts"),
);

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
          kv.value = `_${Math.floor(0x100000000 * Math.random())}`;
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
  if (uid === null) return {};
  const device = uid !== null
    ? await db.manager.findOne(DbDevice, { where: { uid } })
    : null;
  if (device === null) return null;
  return { device_lid: device.lid };
}

export async function dbInsertTime(time: Date) {
  await dbReady();

  const row = new DbTime();
  row.time_ms = time.getTime();
  row.device_lid = this_device.lid;
  await db.manager.insert(DbTime, row);
}

export async function dbSelectTimeRange(
  min: Date,
  max: Date,
  device_uid: string | null,
): Promise<Date[]> {
  await dbReady();

  const where_device = await whereDeviceUid(device_uid);
  if (where_device === null) return [];
  const rows = await db.manager.find(DbTime, {
    where: {
      time_ms: TypeOrm.Between(min.getTime(), max.getTime()),
      ...where_device,
    },
  });
  return rows.map((row) => new Date(row.time_ms));
}

export async function dbMaxTime(
  device_uid: string,
): Promise<Date | null> {
  const where_device = await whereDeviceUid(device_uid);
  if (where_device === null) return null;
  const time_ms = await db.manager.maximum(DbTime, "time_ms");
  return time_ms !== null ? new Date(time_ms) : null;
}

export async function dbMergeTime(device_uid: string, times: Date[]) {
  if (times.length === 0) return [];
  const where_device = await whereDeviceUid(device_uid);
  if (where_device === null) return [];
  const min = new Date(Math.min(...times.map((time) => time.getTime())));
  const max = new Date(Math.max(...times.map((time) => time.getTime())));
  const existing = (await dbSelectTimeRange(min, max, device_uid)).map((time) =>
    time.getTime()
  );
  times = times.filter((time) => !existing.includes(time.getTime()));
  if (times.length === 0) return [];
  await db.manager.insert(
    DbTime,
    times.map((time) => {
      const db_time = new DbTime();
      db_time.time_ms = time.getTime();
      db_time.device_lid = where_device.device_lid!;
      return db_time;
    }),
  );
  return times;
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
