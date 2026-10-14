import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import { PGlite } from "@electric-sql/pglite";

import Ajv2020 from "ajv/dist/2020.js";

import { definePractices, seedIdentity } from "./e2e-setup.ts";
import { asArray, asRecord, parseJson } from "./lib/json.ts";

void test("E2E setup writes complete final definitions and preserves the optimistic version string", async () => {
	const resources = "../server/application/src/main/resources/practices/";
	const schema = asRecord(
		parseJson(
			readFileSync(new URL(`${resources}default-catalog.schema.json`, import.meta.url), "utf8"),
		),
		"Schema",
	);
	const catalog = asRecord(
		parseJson(readFileSync(new URL(`${resources}default-catalog.json`, import.meta.url), "utf8")),
		"Catalog",
	);
	const validate = new Ajv2020({ strict: true, allErrors: true }).compile(schema);
	const writes: { method: string; path: string; body: unknown }[] = [];
	await definePractices(
		async (method, path, body) => {
			if (method === "GET") {
				return [{ slug: "submit-reviewable-work", currentVersion: { id: "9007199254740993" } }];
			}
			writes.push({ method, path, body });
			return {};
		},
		{ workspaceSlug: "test" },
	);
	const definitions = writes.filter(
		(write) => write.method === "POST" || !write.path.endsWith("/feedback-mode"),
	);
	assert.equal(definitions.length, 3);
	for (const write of definitions) {
		const body = asRecord(write.body, "Request");
		const copy = structuredClone(catalog);
		const group = asRecord(asArray(copy.groups, "Groups")[0], "Group");
		const entry = asRecord(asArray(group.practices, "Practices")[0], "Practice");
		entry.kind = "scm.pull_request";
		entry.definition = body.definition;
		assert.equal(validate(copy), true, JSON.stringify(validate.errors));
		assert.equal("bindings" in body, false);
		assert.equal("criteria" in body, false);
	}
	const update = asRecord(definitions[0]?.body, "Update");
	assert.equal(update.expectedVersionId, "9007199254740993");
	const modes = writes
		.filter((write) => write.path.endsWith("/feedback-mode"))
		.map((write) => asRecord(write.body, "Mode").feedbackMode);
	assert.deepEqual(modes, ["OFF", "AUTOMATIC", "AUTOMATIC", "AUTOMATIC"]);
});

void test("E2E identity seed preserves active ownership and refuses disabled, erased and foreign identities", async () => {
	for (const provider of ["github", "gitlab"] as const) {
		const database = new PGlite();
		try {
			await database.exec(`
	CREATE TABLE account(id bigint PRIMARY KEY,status text NOT NULL);
	CREATE TABLE identity_provider(id serial PRIMARY KEY,type text,server_url text,created_at timestamptz,UNIQUE(type,server_url));
	CREATE TABLE "user"(id serial PRIMARY KEY,native_id bigint,provider_id integer,login text,type text,avatar_url text,html_url text,created_at timestamptz,updated_at timestamptz,UNIQUE(provider_id,native_id));
	CREATE TABLE identity(id serial PRIMARY KEY,account_id bigint,provider_id integer,provider_user_key text,team_key text,linked_at timestamptz,linked_via text,disabled_at timestamptz);
	CREATE UNIQUE INDEX uk_identity_provider_key ON identity(provider_id,provider_user_key,team_key) WHERE disabled_at IS NULL;
	CREATE TABLE identity_restriction(provider_id integer,provider_user_key text,team_key text,kind text);
	INSERT INTO account VALUES(1,'ACTIVE'),(2,'ACTIVE');
	`);
			const identity = {
				scmOrigin: provider === "github" ? "https://github.com" : "https://gitlab.example",
				scmId: 4242,
				scmLogin: "alice",
			};
			const userId = await seedIdentity(database, { provider }, 1, identity);
			assert.equal(await seedIdentity(database, { provider }, 1, identity), userId);
			const seeded = await database.query("SELECT account_id::text,linked_via FROM identity");
			assert.deepEqual(seeded.rows, [{ account_id: "1", linked_via: "SIGN_IN" }]);
			await assert.rejects(seedIdentity(database, { provider }, 2, identity), /different account/u);
			await database.exec("UPDATE identity SET disabled_at='2026-01-01'");
			await assert.rejects(seedIdentity(database, { provider }, 1, identity), /disabled/u);
			const disabled = await database.query(
				"SELECT count(*)::int AS count FROM identity WHERE disabled_at IS NULL",
			);
			assert.deepEqual(disabled.rows, [{ count: 0 }]);
			await database.exec(
				"DELETE FROM identity; INSERT INTO identity_restriction VALUES(1,'4242','','ERASED')",
			);
			await assert.rejects(seedIdentity(database, { provider }, 1, identity), /suppressed/u);
			const erased = await database.query("SELECT count(*)::int AS count FROM identity");
			assert.deepEqual(erased.rows, [{ count: 0 }]);
		} finally {
			await database.close();
		}
	}
});
