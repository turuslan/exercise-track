import { MigrationInterface, QueryRunner } from "typeorm";

export class Migrate1784974444633 implements MigrationInterface {
    name = "Migrate1784974444633";

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            CREATE TABLE "db_device" (
                "lid" integer PRIMARY KEY AUTOINCREMENT NOT NULL,
                "uid" text UNIQUE NOT NULL,
                "name" text NOT NULL
            )
        `);
        await queryRunner.query(`
            CREATE TABLE "db_kv" (
                "key" text PRIMARY KEY NOT NULL,
                "value" text NOT NULL
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
    }
}
