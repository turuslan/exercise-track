import * as TypeOrm from "typeorm";
import * as Url from "node:url";

const db_path = Url.fileURLToPath(
  Url.resolve(import.meta.url, "../exercise.db"),
);

@TypeOrm.Entity()
class DbTime {
  @TypeOrm.PrimaryColumn("integer")
  time_ms: number = undefined!;
}

const db = new TypeOrm.DataSource({
  type: "better-sqlite3",
  database: db_path,
  entities: [DbTime],
  synchronize: true,
});
const repo = {
  Time: db.getRepository(DbTime),
};
const db_ready = db.initialize();

export async function dbInsertTime(time: Date) {
  await db_ready;

  const row = new DbTime();
  row.time_ms = time.getTime();
  await repo.Time.insert(row);
}

export async function dbSelectTimeRange(min: Date, max: Date): Promise<Date[]> {
  await db_ready;

  const rows = await repo.Time.find({
    where: { time_ms: TypeOrm.Between(min.getTime(), max.getTime()) },
  });
  return rows.map((row) => new Date(row.time_ms));
}
