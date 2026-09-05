import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import { readGrepParams, searchFiles } from "../../../main/resources/agent/pi-grep-tool.ts";

function workspace(): string {
	const root = mkdtempSync(join(tmpdir(), "grep-tool-"));
	mkdirSync(join(root, "inputs", "context"), { recursive: true });
	mkdirSync(join(root, "inputs", "sources", "scm", "repo", "src"), { recursive: true });
	mkdirSync(join(root, ".sessions"), { recursive: true });
	writeFileSync(
		join(root, "inputs", "context", "diff.patch"),
		"+API_KEY = 'sk-live'\n context\n+print(API_KEY)\n",
	);
	writeFileSync(
		join(root, "inputs", "sources", "scm", "repo", "src", "app.py"),
		"def main():\n    return API_KEY\n",
	);
	writeFileSync(
		join(root, "inputs", "sources", "scm", "repo", "src", "blob.bin"),
		Buffer.from([0, 65, 80, 73, 95, 75, 69, 89]),
	);
	writeFileSync(join(root, ".sessions", "s.jsonl"), '{"text":"API_KEY"}\n');
	return root;
}

void test("matches carry the SDK grep tool's path:line: shape and skip runtime state and binaries", () => {
	const root = workspace();
	const { text, details } = searchFiles(root, { pattern: "API_KEY" });
	assert.deepEqual(text.split("\n"), [
		"inputs/context/diff.patch:1: +API_KEY = 'sk-live'",
		"inputs/context/diff.patch:3: +print(API_KEY)",
		"inputs/sources/scm/repo/src/app.py:2:     return API_KEY",
	]);
	assert.deepEqual(details, { matches: 3, truncated: false });
});

void test("path, glob, literal, ignoreCase and context narrow and widen the output as the SDK tool's do", () => {
	const root = workspace();
	assert.equal(
		searchFiles(root, {
			pattern: "api_key",
			ignoreCase: true,
			path: "inputs/sources",
			glob: "*.py",
		}).text,
		"scm/repo/src/app.py:2:     return API_KEY",
	);
	assert.equal(
		searchFiles(root, {
			pattern: "API_KEY = 'sk-live'",
			literal: true,
			glob: "**/*.patch",
		}).text.split("\n").length,
		1,
	);
	assert.deepEqual(
		searchFiles(root, {
			pattern: "print",
			path: "inputs/context/diff.patch",
			context: 1,
		}).text.split("\n"),
		["diff.patch-2-  context", "diff.patch:3: +print(API_KEY)", "--"],
	);
});

void test("a limit truncates with a note, an invalid pattern and a path outside the workspace are answered, not thrown", () => {
	const root = workspace();
	const limited = searchFiles(root, { pattern: "API_KEY", limit: 1 });
	assert.equal(limited.details.truncated, true);
	assert.match(limited.text, /truncated at 1 matches/);
	assert.match(searchFiles(root, { pattern: "(" }).text, /^Invalid pattern/);
	assert.match(searchFiles(root, { pattern: "x", path: "../.." }).text, /outside the workspace/);
	assert.equal(searchFiles(root, { pattern: "nothing-here" }).text, "No matches found");
});

void test("arguments of the wrong shape are dropped rather than trusted", () => {
	assert.deepEqual(readGrepParams({ pattern: "x", limit: "5", ignoreCase: 1, path: 3 }), {
		pattern: "x",
		path: undefined,
		glob: undefined,
		ignoreCase: undefined,
		literal: undefined,
		context: undefined,
		limit: undefined,
	});
	assert.equal(readGrepParams(null).pattern, "");
});
