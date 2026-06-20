import { DatabaseSync } from "node:sqlite";

const db = new DatabaseSync("exercise.db");
db.exec(`
  CREATE TABLE IF NOT EXISTS time (
    time_ms INTEGER UNIQUE
  );
`);
const db_insert_time = db.prepare(`
  INSERT INTO time (time_ms) VALUES (?)
`);
const db_select_time_range = db.prepare(`
  SELECT time_ms FROM time
  WHERE ? <= time_ms AND time_ms <= ?
  ORDER BY time_ms
`);

export function dbInsertTime(time: Date) {
  db_insert_time.run(time.getTime());
}

export function dbSelectTimeRange(min: Date, max: Date) {
  return db_select_time_range.all(min.getTime(), max.getTime())
    .map((row) => new Date(row.time_ms as number));
}
