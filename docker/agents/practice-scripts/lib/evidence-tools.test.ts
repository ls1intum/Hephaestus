import assert from "node:assert/strict";
import { describe, test } from "node:test";

import { evidenceTools } from "./evidence-tools.ts";
import { UnratedError, type FileTree, type GrepOptions } from "./practice-script.ts";

const FILES: Record<string, string> = {
	"src/cart.ts": "export class Cart {\n  count += 1;\n}",
	"src/large.ts": Array.from({ length: 300 }, (_, i) => `line ${String(i + 1)}`).join("\n"),
};

/** A tree over `FILES` that records what the tools asked it, as the host would answer. */
function tree() {
	const asked: unknown[] = [];
	const files: FileTree = {
		revision: "a".repeat(40),
		read: async (path) => {
			asked.push({ read: path });
			if (path === "logo.png") {
				return { status: "BINARY" };
			}
			if (path === "broken.ts") {
				throw new UnratedError("unavailable", "the read failed");
			}
			const text = FILES[path];
			return text === undefined
				? { status: "ABSENT" }
				: { status: "FOUND", text, truncated: path === "src/large.ts" };
		},
		list: async (glob) => {
			asked.push({ list: glob });
			return { paths: Object.keys(FILES), truncated: glob === "**" };
		},
		grep: async (pattern: string, options?: GrepOptions) => {
			asked.push({ grep: pattern, ...options });
			return {
				matches: [{ file: "src/cart.ts", line: 2, content: "count += 1;" }],
				truncated: false,
			};
		},
		cite: async () => {
			throw new UnratedError("refused");
		},
	};
	return { files, asked };
}

/** Run the tool `name` over `files` with `input`, as an agent's step calls it. */
async function run(files: FileTree, name: string, input: unknown): Promise<unknown> {
	const tool = evidenceTools(files)[name];
	assert.ok(tool !== undefined && "execute" in tool && tool.execute !== undefined, `${name} runs`);
	return tool.execute(input, { toolCallId: "call", messages: [], context: {} });
}

void describe("the read tools of an agent", () => {
	void test("grep marks a shortened matched line whose match lies beyond the shown prefix", async () => {
		const { files } = tree();
		const matched = {
			...files,
			grep: async () => ({
				matches: [{ file: "src/cart.ts", line: 2, content: `${"x".repeat(300)}needle` }],
				truncated: false,
			}),
		};
		assert.equal(
			await run(matched, "grep", { pattern: "needle" }),
			`src/cart.ts:2: ${"x".repeat(299)}…`,
		);
	});
	void test("readFile numbers the lines of a file, bounds them, and names what it cannot show", async () => {
		const { files } = tree();
		const read = async (input: { path: string; startLine?: number; endLine?: number }) =>
			run(files, "readFile", input);
		assert.equal(
			await read({ path: "src/cart.ts" }),
			"1: export class Cart {\n2:   count += 1;\n3: }",
		);
		assert.equal(await read({ path: "src/cart.ts", startLine: 2, endLine: 2 }), "2:   count += 1;");
		assert.equal(await read({ path: "src/cart.ts", startLine: 9 }), "(no lines in that range)");
		const large = await read({ path: "src/large.ts", startLine: 50 });
		assert.ok(typeof large === "string");
		assert.match(large, /^50: line 50\n/u);
		assert.match(large, /\n249: line 249\n\(the file is cut at the read limit\)$/u);
		assert.equal(await read({ path: "absent.ts" }), "absent.ts is not a file of the repository");
		assert.equal(await read({ path: "logo.png" }), "logo.png is a binary file");
		// A read that fails stops the agent: it is never shown as an absent file.
		await assert.rejects(read({ path: "broken.ts" }), {
			name: "UnratedError",
			reason: "unavailable",
		});
	});

	void test("listFiles lists a folder through the tree and says when the list is cut", async () => {
		const { files, asked } = tree();
		assert.equal(await run(files, "listFiles", { dir: "./src/" }), "src/cart.ts\nsrc/large.ts");
		assert.equal(
			await run(files, "listFiles", { dir: "" }),
			"src/cart.ts\nsrc/large.ts\n(more files are not listed)",
		);
		assert.deepEqual(asked, [{ list: "src/**" }, { list: "**" }]);
	});

	void test("grep searches through the tree with a bound and prints path:line: text", async () => {
		const { files, asked } = tree();
		assert.equal(
			await run(files, "grep", { pattern: "count", glob: "*.ts" }),
			"src/cart.ts:2: count += 1;",
		);
		assert.deepEqual(asked, [{ grep: "count", glob: "*.ts", maxResults: 50 }]);
	});

	void test("work without a tree has no tools", () => {
		assert.deepEqual(evidenceTools(null), {});
	});

	void test("grep names a cut search even when the returned matches are empty", async () => {
		const { files } = tree();
		const cut = { ...files, grep: async () => ({ matches: [], truncated: true }) };
		assert.equal(
			await run(cut, "grep", { pattern: "count" }),
			"(no matches)\n(more matches are not listed)",
		);
		const matched = {
			...files,
			grep: async () => ({
				matches: [{ file: "src/cart.ts", line: 2, content: "count += 1;" }],
				truncated: true,
			}),
		};
		assert.equal(
			await run(matched, "grep", { pattern: "count" }),
			"src/cart.ts:2: count += 1;\n(more matches are not listed)",
		);
	});
});
