import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import Ajv2020 from "ajv/dist/2020.js";

import { definePractices } from "./e2e-setup.ts";
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
