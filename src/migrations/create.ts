import { MigrationInterface, QueryRunner } from "typeorm";

export class Create1784974444633 implements MigrationInterface {
    name = Create1784974444633.name;

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            CREATE TABLE "DbTime" (
                "time" INTEGER PRIMARY KEY NOT NULL,
                "device_lid" INTEGER NOT NULL
            )
        `);
        await queryRunner.query(`
            CREATE TABLE "DbDevice" (
                "lid" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                "uid" TEXT UNIQUE NOT NULL,
                "name" TEXT NOT NULL,
                "enabled" BOOLEAN NOT NULL
            )
        `);
        await queryRunner.query(`
            CREATE TABLE "DbKv" (
                "key" TEXT PRIMARY KEY NOT NULL,
                "value" TEXT NOT NULL
            )
        `);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            DROP TABLE "db_kv"
        `);
        await queryRunner.query(`
            DROP TABLE "db_device"
        `);
        await queryRunner.query(`
            DROP TABLE "db_time"
        `);
    }
}
