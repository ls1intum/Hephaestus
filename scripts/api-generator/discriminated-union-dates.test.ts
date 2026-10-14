import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { stripTypeScriptTypes } from "node:module";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { createClient } from "@hey-api/openapi-ts";
import { discriminatedUnionDates } from "./discriminated-union-dates.ts";

test("generated scan alternatives convert nested dates and preserve nullable values", async () => {
	const directory = await mkdtemp(join(tmpdir(), "hephaestus-union-dates-"));
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
		const source = stripTypeScriptTypes(
			await readFile(join(directory, "transformers.gen.ts"), "utf8"),
		);
		const generated: unknown = await import(
			`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`
		);
		assert(generated !== null && typeof generated === "object");
		assert("listScanReportItemsResponseTransformer" in generated);
		const transform = generated.listScanReportItemsResponseTransformer;
		assert.equal(typeof transform, "function");
		assert(typeof transform === "function");
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
				const output: unknown = await transform(value);
				assert(output !== null && typeof output === "object" && "content" in output);
				assert(Array.isArray(output.content));
				const row: unknown = output.content[0];
				assert(row !== null && typeof row === "object" && "item" in row);
				const item = row.item;
				assert(item !== null && typeof item === "object" && "removedAt" in item && "id" in item);
				assert.equal(item.id, "9007199254740993");
				if (nullable) assert.equal(item.removedAt, null);
				else assert(item.removedAt instanceof Date, `${status}: item.removedAt`);
				if (status === "JUDGED" || status === "REUSED") {
					assert("recordedAt" in row && row.recordedAt instanceof Date, `${status}: recordedAt`);
				}
				if ((status === "LISTED" || status === "NOT_REVIEWED") && !nullable) {
					assert("lastResult" in row);
					const result = row.lastResult;
					assert(result !== null && typeof result === "object" && "recordedAt" in result);
					assert(result.recordedAt instanceof Date, `${status}: lastResult.recordedAt`);
				}
			}
		}
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});
