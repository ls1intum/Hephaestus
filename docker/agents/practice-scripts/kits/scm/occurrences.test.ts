import assert from "node:assert/strict";
import { test } from "node:test";
import {
	occurrences,
	placeOccurrences,
	selectAddedOccurrences,
	type SourceFile,
} from "./occurrences.ts";
import type { DiffFile } from "./types.ts";

void test("whole-file matching keeps repeated and multi-line occurrences at their own extent", () => {
	const file: SourceFile = {
		path: "src/a.ts",
		language: "typescript",
		text: "call(); call();\ntry {\n work();\n} catch {\n}\n// call();\nconst note = 'call();';",
	};
	const patterns = [
		{ label: "call", test: /call\(\)/u },
		{ label: "empty handler", test: /catch\s*\{\s*\}/u },
	];
	assert.deepEqual(
		occurrences(file, patterns).map(({ label, startLine, endLine }) => [label, startLine, endLine]),
		[
			["call", 1, 1],
			["call", 1, 1],
			["empty handler", 4, 5],
		],
	);
});

void test("regex state is reset for each file and flags are preserved", () => {
	const expression = /CALL/giu;
	expression.lastIndex = 4;
	const patterns = [{ label: "call", test: expression }];
	for (const path of ["a.ts", "b.ts"]) {
		assert.equal(
			occurrences({ path, language: "typescript", text: "call call" }, patterns).length,
			2,
		);
	}
	assert.equal(expression.lastIndex, 4);
});

void test("ITEM selection keeps a multi-line occurrence only when its own extent intersects additions", () => {
	const file: SourceFile = {
		path: "src/a.ts",
		language: "typescript",
		text: "function work() {\n call();\n another();\n}\ncatch {\n}",
	};
	const found = occurrences(file, [
		{ label: "call", test: /call\(\)/u },
		{ label: "handler", test: /catch\s*\{\s*\}/u },
	]);
	const change: DiffFile = {
		path: file.path,
		addedLines: new Map([
			[3, " another();"],
			[6, "}"],
		]),
		removedLines: new Map(),
		hunks: [],
	};
	assert.deepEqual(
		selectAddedOccurrences(found, new Map([[file.path, change]])).map((row) => row.label),
		["handler"],
	);
});

void test("declaration location uses full pinned source and does not enlarge a call's extent", () => {
	const file: SourceFile = {
		path: "Screen.swift",
		language: "swift",
		text: "struct Screen: View {\n func load() {\n  URLSession.shared.dataTask(with: url)\n }\n}\nclass Store {\n func load() { URLSession.shared.dataTask(with: url) }\n}",
	};
	const found = occurrences(file, [{ label: "request", test: /URLSession\b/u }]);
	const placed = placeOccurrences(file, found, { scope: /\bView\b/u, onlyInScope: true });
	assert.equal(placed.length, 1);
	assert.equal(placed[0]?.startLine, 3);
	assert.equal(placed[0].endLine, 3);
	assert.equal(placed[0].facts.enclosing, "struct Screen");
});

void test("comment patterns opt into comments without joining distant lines", () => {
	const file: SourceFile = {
		path: "a.ts",
		language: "typescript",
		text: "// TODO tracked work\ncatch {\n run();\n}\n// catch {}",
	};
	assert.equal(
		occurrences(file, [{ label: "note", test: /TODO/u }], { includeComments: true }).length,
		1,
	);
	assert.equal(occurrences(file, [{ label: "handler", test: /catch\s*\{\s*\}/u }]).length, 0);
});

void test("Python and Ruby literals and comments are excluded from code occurrences", () => {
	for (const language of ["python", "ruby"]) {
		const found = occurrences(
			{ path: "source", language, text: "# call()\nvalue = 'call()'\ncall()\ncall() # call()" },
			[{ label: "call", test: /call\(\)/u }],
		);
		assert.deepEqual(
			found.map((row) => row.startLine),
			[3, 4],
		);
	}
});

void test("a consuming site's extent includes arguments changed below its callee", () => {
	const file = {
		path: "a.ts",
		language: "typescript",
		text: "load(\n  source,\n  options\n);\nother();",
	};
	const found = occurrences(file, [{ label: "load", test: /load\s*\(/u }]);
	assert.deepEqual(
		found.map((row) => [row.startLine, row.endLine]),
		[[1, 4]],
	);
	const changed: DiffFile = {
		path: file.path,
		addedLines: new Map([[3, "  options"]]),
		removedLines: new Map(),
		hunks: [],
	};
	assert.equal(selectAddedOccurrences(found, new Map([[file.path, changed]])).length, 1);
	changed.addedLines = new Map([[5, "other();"]]);
	assert.equal(selectAddedOccurrences(found, new Map([[file.path, changed]])).length, 0);
});

void test("executed expressions inside Swift and JavaScript interpolation remain code", () => {
	const patterns = [{ label: "load", test: /load\s*\(/u }];
	for (const { language, text } of [
		{ language: "swift", text: String.raw`Text("literal load() and \(load("name"))")` },
		{ language: "typescript", text: `const text = \`literal load() and \${load("name")}\`` },
	]) {
		const found = occurrences({ path: "source", language, text }, patterns);
		assert.equal(found.length, 1);
	}
});
