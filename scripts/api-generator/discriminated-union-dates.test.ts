import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { stripTypeScriptTypes } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { createClient } from "@hey-api/openapi-ts";

import { discriminatedUnionDates } from "./discriminated-union-dates.ts";

function isTransformer(value: unknown): value is (input: unknown) => unknown {
	return typeof value === "function";
}

await test("generated scan alternatives convert nested dates and preserve nullable values", async () => {
	const directory = await mkdtemp(path.join(tmpdir(), "hephaestus-union-dates-"));
	try {
		await createClient({
			input: fileURLToPath(new URL("../../server/openapi.yaml", import.meta.url)),
			output: directory,
			logs: { level: "silent", file: false },
			plugins: [
				"@hey-api/typescript",
				{
					name: "@hey-api/transformers",
					dates: true,
					bigInt: false,
					transformers: [discriminatedUnionDates],
				},
			],
		});
		const emitted = await readFile(path.join(directory, "transformers.gen.ts"), "utf8");
		assert.doesNotMatch(emitted, /value: any/u);
		const source = stripTypeScriptTypes(emitted);
		const generated: unknown = await import(
			`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`
		);
		assert.ok(
			generated !== null &&
				typeof generated === "object" &&
				"listScanReportItemsResponseTransformer" in generated,
		);
		const { listScanReportItemsResponseTransformer: transform } = generated;
		assert.ok(isTransformer(transform));
		const at = "2026-10-14T12:00:00Z";
		for (const status of ["JUDGED", "REUSED", "WITHHELD", "LISTED", "NOT_REVIEWED"]) {
			for (const nullable of [false, true]) {
				const value = {
					content: [
						{
							status,
							item: { id: "9007199254740993", removedAt: nullable ? null : at },
							recordedAt: at,
							lastResult: nullable ? null : { recordedAt: at, endedAt: null },
						},
					],
					nextCursor: null,
					hasNext: false,
				};
				const resultDate = status === "LISTED" || status === "NOT_REVIEWED" ? new Date(at) : at;
				assert.deepEqual(
					await transform(value),
					{
						content: [
							{
								status,
								item: { id: "9007199254740993", removedAt: nullable ? null : new Date(at) },
								recordedAt: status === "JUDGED" || status === "REUSED" ? new Date(at) : at,
								lastResult: nullable ? null : { recordedAt: resultDate, endedAt: null },
							},
						],
						nextCursor: null,
						hasNext: false,
					},
					`${status}, nullable=${nullable}`,
				);
			}
		}
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});
