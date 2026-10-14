import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { Ajv } from "ajv";
import { fullFormats } from "ajv-formats/dist/formats.js";

function record(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}

await test("the exporter protocol fixture conforms to the official SARIF 2.1.0 schema", async () => {
	const official: unknown = JSON.parse(
		await readFile(new URL("schema.json", import.meta.url), "utf8"),
	);
	assert.ok(record(official));
	assert.equal(official.$schema, "http://json-schema.org/draft-04/schema#");
	assert.ok(typeof official.id === "string");
	const { id, $schema: _meta, ...assertions } = official;
	const schema = { ...assertions, $id: id, $schema: "http://json-schema.org/draft-07/schema#" };
	const ajv = new Ajv({ allErrors: true, strict: false, formats: fullFormats });
	const validate = ajv.compile(schema);
	const exported: unknown = JSON.parse(
		await readFile(new URL("expected.json", import.meta.url), "utf8"),
	);
	assert.ok(validate(exported), JSON.stringify(validate.errors));
});
