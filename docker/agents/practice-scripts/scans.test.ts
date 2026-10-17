import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import { z } from "zod";
import type { DiffFile } from "./kits/scm/types.ts";
import { MAX_READ_BYTES } from "./lib/core/evidence-reader.ts";
import { UnratedError } from "./lib/core/practice-script.ts";
import { runBundled, runBundledProgram, scan } from "./run-bundled.ts";

const CASES = [
	[
		"avoids-insecure-defaults-and-over-broad-permissions",
		"app/config.ts",
		"export const options = { rejectUnauthorized: false };",
	],
	[
		"avoids-unsafe-panics-and-chosen-crashes",
		"App/Store.swift",
		'func load() { fatalError("failed") }',
	],
	["handles-errors-instead-of-swallowing-them", "app/a.ts", "try { run(); } catch {\n}"],
	[
		"keeps-views-free-of-networking-and-persistence",
		"App/View.swift",
		"struct Screen: View {\n func load() { URLSession.shared.dataTask(with: url) }\n}",
	],
	[
		"leaves-the-code-clean-with-intent-revealing-comments",
		"app/a.ts",
		"// TODO clean up\nconsole.log(value);",
	],
	["logs-through-the-platform-logger", "app/a.ts", "console.log(value);"],
	[
		"makes-ui-accessible-by-default",
		"App/View.swift",
		'struct Screen: View {\nvar body: some View { Image(systemName: "star") }\n}',
	],
	[
		"owns-state-at-the-right-level",
		"App/View.swift",
		"struct Screen: View {\n @ObservedObject var model = Model()\n}",
	],
	[
		"uses-adaptive-colors-for-every-appearance",
		"App/View.swift",
		'struct Screen: View {\nvar body: some View { Text("Hi").foregroundStyle(Color.white) }\n}',
	],
	[
		"uses-structured-concurrency-safely",
		"App/Store.swift",
		"func load() { Task.detached { await run() } }",
	],
	[
		"validates-inputs-and-edge-cases-at-the-boundary",
		"app/a.ts",
		"export function read(items, n) { return items[n]; }",
	],
] as const;

for (const [slug, path, text] of CASES) {
	void test(`${slug} inspects unchanged occurrences in its assigned scan file`, async () => {
		const review = await runBundled(slug, {
			inputs: scan(path),
			repo: { [path]: text },
			change: null,
		});
		assert.ok((review.leads?.length ?? 0) > 0, JSON.stringify(review));
		assert.equal(
			review.leads?.every((lead) => "file" in lead.at && lead.at.file === path),
			true,
		);
	});
}

for (const [slug, path, text] of CASES) {
	void test(`${slug} reviews source changes beside a binary image`, async () => {
		const review = await runBundled(slug, {
			repo: { [path]: text, "logo.png": "\u0000binary" },
			change: new Map([
				[
					path,
					{
						path,
						addedLines: new Map(text.split("\n").map((line, i) => [i + 1, line])),
						removedLines: new Map(),
						hunks: [],
					},
				],
				[
					"logo.png",
					{ path: "logo.png", addedLines: new Map(), removedLines: new Map(), hunks: [] },
				],
			]),
		});
		assert.ok((review.leads?.length ?? 0) > 0, JSON.stringify(review));
		assert.equal(
			review.leads?.every((lead) => "file" in lead.at && lead.at.file === path),
			true,
		);
	});
}

void test("source selection skips unrelated languages, tests, and paths without added text before reading", async () => {
	const source = "console.log(value);";
	const changed = (path: string, added: boolean): [string, DiffFile] => [
		path,
		{
			path,
			addedLines: new Map<number, string>(added ? [[1, source]] : []),
			removedLines: new Map(),
			hunks: [],
		},
	];
	const review = await runBundled("logs-through-the-platform-logger", {
		repo: {
			"app/a.ts": source,
			"logo.png": "\u0000binary",
			"tests/a.ts": "\u0000binary",
			"app/unchanged.ts": "\u0000binary",
			"dependencies.lock": "x".repeat(MAX_READ_BYTES + 1),
		},
		change: new Map([
			changed("app/a.ts", true),
			changed("logo.png", true),
			changed("tests/a.ts", true),
			changed("app/unchanged.ts", false),
			changed("app/deleted.ts", false),
			changed("dependencies.lock", true),
		]),
	});
	assert.equal(review.leads?.length, 1);
	assert.deepEqual(review.leads[0]?.at, { file: "app/a.ts", line: 1, endLine: 1 });
});

void test("an unavailable or oversized required scan file withholds instead of returning a semantic result", async () => {
	for (const repo of [
		{},
		{ "app/a.ts": "\u0000binary" },
		{ "app/a.ts": "x".repeat(MAX_READ_BYTES + 1) },
	]) {
		const result = await runBundledProgram("handles-errors-instead-of-swallowing-them", {
			inputs: scan("app/a.ts"),
			repo,
			change: null,
		});
		assert.ok("error" in result.outcome);
		assert.ok(result.outcome.error instanceof UnratedError);
		assert.deepEqual(result.reviews, []);
	}
});

void test("the catalog advertises exactly the 13 approved scan practices", async () => {
	const value: unknown = JSON.parse(
		await readFile(
			new URL(
				"../../../server/application/src/main/resources/practices/default-catalog.json",
				import.meta.url,
			),
			"utf8",
		),
	);
	const catalog = z
		.object({
			criteriaPreambles: z.record(z.string(), z.string()),
			groups: z.array(
				z.object({
					practices: z.array(
						z.object({
							preamble: z.string().optional(),
							definition: z.object({ scan: z.unknown().nullish(), criteria: z.string() }),
						}),
					),
				}),
			),
		})
		.parse(value);
	const numbers: number[] = [];
	let number = 0;
	for (const group of catalog.groups) {
		for (const practice of group.practices) {
			number += 1;
			if (practice.definition.scan != null) {
				const criteria = [
					practice.preamble === undefined ? "" : catalog.criteriaPreambles[practice.preamble],
					practice.definition.criteria,
				].join("\n\n");
				numbers.push(number);
				assert.match(criteria, /## On a change/u);
				assert.match(criteria, /## In a scan/u);
				assert.doesNotMatch(criteria, /single most consequential/u);
			}
		}
	}
	assert.deepEqual(numbers, [19, 20, 21, 22, 23, 24, 26, 33, 43, 44, 45, 46, 49]);
});
