import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { test } from "node:test";

import { Client } from "pg";

import { seedIdentity } from "./e2e-setup.ts";
import { asString } from "./lib/json.ts";

void test("identity seeding uses the migrated PostgreSQL ownership and erasure constraints", async (context) => {
	const target = new URL(asString(process.env.E2E_TEST_DATABASE_URL, "database URL"));
	target.username = asString(process.env.E2E_TEST_DATABASE_USER, "database user");
	target.password = asString(process.env.E2E_TEST_DATABASE_PASSWORD, "database password");
	const database = new Client({ connectionString: target.href });
	try {
		await database.connect();
		await database.query(`
			INSERT INTO account(id,display_name,status,app_role)
			VALUES(986601,'Seed owner','ACTIVE','USER'),(986602,'Other owner','ACTIVE','USER')
		`);
		const provider = await database.query<{ id: string }>(
			"SELECT id::text FROM identity_provider WHERE type='GITHUB' AND server_url='https://github.com'",
		);
		const providerId = provider.rows[0]?.id;
		assert.ok(providerId !== undefined);
		await context.test("links an available identity to the selected account", async () => {
			const userId = await seedIdentity(database, { provider: "github" }, 986_601, {
				scmOrigin: "https://github.com",
				scmId: 986_619,
				scmLogin: "available-actor",
			});
			const linked = await database.query<{ account_id: string }>(
				"SELECT account_id::text FROM identity WHERE provider_id=$1 AND provider_user_key='986619' AND disabled_at IS NULL",
				[providerId],
			);
			assert.deepEqual(linked.rows, [{ account_id: "986601" }]);
			const actor = await database.query<{ login: string }>(
				'SELECT login FROM "user" WHERE id=$1',
				[userId],
			);
			assert.deepEqual(actor.rows, [{ login: "available-actor" }]);
		});
		for (const [index, refusal] of ["disabled", "erased", "foreign"].entries()) {
			await context.test(
				`refuses ${refusal} identities without changing their stored facts`,
				async () => {
					const scmId = 986_610 + index;
					await database.query(
						`INSERT INTO "user"(native_id,provider_id,login,type) VALUES($1,$2,$3,'USER')`,
						[scmId, providerId, `retained-${scmId}`],
					);
					if (refusal === "erased") {
						await database.query(
							"INSERT INTO identity_restriction(id,provider_id,provider_user_key,kind) VALUES($1,$2,$3,'ERASED')",
							[randomUUID(), providerId, String(scmId)],
						);
					} else {
						await database.query(
							`INSERT INTO identity(account_id,provider_id,provider_user_key,linked_via,disabled_at)
						 VALUES($1,$2,$3,'SIGN_IN',CASE WHEN $4 THEN now() END)`,
							[
								refusal === "foreign" ? 986_602 : 986_601,
								providerId,
								String(scmId),
								refusal === "disabled",
							],
						);
					}
					const facts = async () =>
						database.query<{ kind: string; facts: string }>(`
					SELECT 'identity' AS kind,to_jsonb(i)::text AS facts FROM identity i
					UNION ALL SELECT 'restriction',to_jsonb(r)::text FROM identity_restriction r
					UNION ALL SELECT 'actor',to_jsonb(u)::text FROM "user" u
					ORDER BY kind,facts
				`);
					const before = await facts();
					await assert.rejects(
						seedIdentity(database, { provider: "github" }, 986_601, {
							scmOrigin: "https://github.com",
							scmId,
							scmLogin: "changed-actor",
						}),
					);
					const after = await facts();
					assert.deepEqual(after.rows, before.rows);
				},
			);
		}
	} finally {
		await database.end();
	}
});
