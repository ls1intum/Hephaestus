import assert from "node:assert/strict";
import { test } from "node:test";

import { PGlite } from "@electric-sql/pglite";

import { preservationSql } from "./lib/release-upgrade-fingerprint.ts";

void test("people ids and semantic membership survive table contraction while unclaimed accounts are added", async () => {
	const database = new PGlite();
	try {
		await database.exec(`
CREATE TABLE account(id bigint,display_name text,app_role text,status text);
CREATE TABLE "user"(id bigint,provider_id bigint,native_id bigint,login text,type text);
CREATE TABLE workspace(id bigint,slug text,display_name text,account_login text,account_type text,status text);
CREATE TABLE connection(id bigint,workspace_id bigint,kind text,state text);
CREATE TABLE identity_link(id bigint,account_id bigint,provider_id bigint,subject text,team_id text,linked_at timestamptz,last_login_at timestamptz,disabled_at timestamptz,linked_via text);
CREATE TABLE workspace_membership(workspace_id bigint,user_id bigint,role text,hidden boolean);
INSERT INTO account VALUES(1,'Alice','USER','ACTIVE');
INSERT INTO "user" VALUES(91,8,4242,'alice','USER'),(92,8,4243,'bob','USER'),(93,8,4244,'robot','BOT');
INSERT INTO workspace VALUES(4,'team','Team','team','ORG','ACTIVE');
INSERT INTO connection VALUES(5,4,'GITHUB','ACTIVE');
INSERT INTO identity_link VALUES(2,1,8,'4242',NULL,'2026-01-01',NULL,'2026-01-02','MANUAL_LINK');
INSERT INTO workspace_membership VALUES(4,91,'OWNER',false),(4,92,'MEMBER',true),(4,93,'MEMBER',false);
`);
		const before = await database.query(preservationSql("released"));
		await database.exec(`
CREATE TABLE identity(id bigint,account_id bigint,provider_id bigint,provider_user_key text,team_key text,linked_at timestamptz,last_login_at timestamptz,disabled_at timestamptz,linked_via text);
CREATE TABLE membership(workspace_id bigint,account_id bigint,role text,hidden boolean,left_at timestamptz);
INSERT INTO account VALUES(10,'Bob','USER','UNCLAIMED');
INSERT INTO identity VALUES(2,1,8,'4242','','2026-01-01',NULL,'2026-01-02','MANUAL'),(11,10,8,'4243','','2026-01-01',NULL,NULL,'ROSTER');
INSERT INTO membership VALUES(4,1,'OWNER',false,NULL),(4,10,'MEMBER',true,NULL);
DROP TABLE identity_link,workspace_membership;
`);
		const candidate = preservationSql("candidate", { accounts: ["1"], identities: ["2"] });
		const upgraded = await database.query(candidate);
		assert.deepEqual(upgraded.rows, before.rows);
		await database.exec("UPDATE membership SET role='MEMBER' WHERE account_id=1");
		const changedRole = await database.query(candidate);
		assert.notDeepEqual(changedRole.rows, before.rows);
		await database.exec(
			"UPDATE membership SET role='OWNER' WHERE account_id=1; DELETE FROM identity WHERE id=2",
		);
		const deletedIdentity = await database.query(candidate);
		assert.notDeepEqual(deletedIdentity.rows, before.rows);
	} finally {
		await database.close();
	}
});

void test("candidate comparison requires exact released ids and rejects SQL-shaped ids", () => {
	assert.throws(() => preservationSql("candidate"), /released people ids/u);
	assert.throws(
		() => preservationSql("candidate", { accounts: ["1); DROP TABLE account"], identities: [] }),
		/decimal/u,
	);
});
