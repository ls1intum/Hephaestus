import assert from "node:assert/strict";
import { test } from "node:test";

import { parseDiff } from "./diff-parser.ts";

void test("a removed '-- comment' and an added '++i' line keep the line numbers after them", () => {
	const diff = [
		"diff --git a/q.sql b/q.sql",
		"--- a/q.sql",
		"+++ b/q.sql",
		"@@ -1,3 +1,3 @@",
		"--- old comment",
		"+++i;",
		" select 1;",
		" select 2;",
		"",
	].join("\n");

	const file = parseDiff(diff).get("q.sql");

	assert.deepEqual([...(file?.removedLines ?? [])], [[1, "-- old comment"]]);
	assert.deepEqual([...(file?.addedLines ?? [])], [[1, "++i;"]]);
	assert.equal(file?.hunks[0]?.lines.length, 4);
});

void test("annotated lines parse like plain ones and a context line that starts with '+' stays context", () => {
	const diff = [
		"diff --git a/a.ts b/a.ts",
		"--- a/a.ts",
		"+++ b/a.ts",
		"@@ -4,2 +4,3 @@",
		"[L4]  +x",
		"[L5] +added",
		"[L5]  tail",
		String.raw`\ No newline at end of file`,
		"",
	].join("\n");

	const file = parseDiff(diff).get("a.ts");

	assert.deepEqual([...(file?.addedLines ?? [])], [[5, "added"]]);
	assert.deepEqual([...(file?.removedLines ?? [])], []);
});
