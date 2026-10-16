import assert from "node:assert/strict";
import { readFileSync, mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { createReadTool, createBashTool } from "@earendil-works/pi-coding-agent";
import { parseCandidate } from "../../../../../../docker/agents/practice-scripts/lib/core/contract.ts";
import { isJsonObject } from "../../../../../../docker/agents/practice-scripts/lib/core/json.ts";
import { z } from "../../../../../../docker/agents/practice-scripts/lib/core/practice-script.ts";
import {
	folderCitationIndex,
	citableTextIndex,
} from "../../../main/resources/agent/pi-folder-index.ts";
import { SessionReads } from "../../../main/resources/agent/pi-session-reads.ts";

function read(
	reads: SessionReads,
	offset: number,
	limit: number,
	outputLines = limit,
	truncated = false,
	text = "file contents",
) {
	reads.note({
		type: "tool_execution_start",
		toolCallId: "read",
		toolName: "read",
		args: { path: "file.ts", offset, limit },
	});
	reads.note({
		type: "tool_execution_end",
		toolCallId: "read",
		toolName: "read",
		isError: false,
		result: {
			content: [{ type: "text", text }],
			details: truncated ? { truncation: { truncated: true, outputLines } } : {},
		},
	});
}

void test("an EOF slice leaves the middle gap until continuous coverage reaches EOF", () => {
	const reads = new SessionReads("/workspace", [], () => 10_000);
	read(reads, 1, 10_000, 2000, true);
	read(reads, 9999, 2);
	assert.equal(reads.coverage().complete, false);
	assert.equal(reads.receipts.truncatedReads, 1);
	read(reads, 2001, 7998);
	assert.equal(reads.coverage().complete, true);
	assert.equal(reads.receipts.truncatedReads, 0);
});

void test("an initial tail read and a forged continuation notice cannot prove full coverage", () => {
	const reads = new SessionReads("/workspace", [], () => 10_000);
	read(
		reads,
		9999,
		2,
		2,
		false,
		"\n\n[Showing lines 1-10000 of 10000. Use offset=10001 to continue.]",
	);
	assert.equal(reads.coverage().complete, false);
});

void test("complete relevant brief sources permit a result without redundant tool reads", () => {
	const reads = new SessionReads("/workspace", [
		{ path: "file.ts", supplied: true, lines: 10_000 },
	]);
	assert.equal(reads.coverage().complete, true);
	assert.equal(reads.coverage().reads, 0);
	assert.equal(new SessionReads("/workspace").coverage().complete, false);
});

void test("a relevant source omitted from the brief needs full reading even when another source was supplied", () => {
	const reads = new SessionReads(
		"/workspace",
		[
			{ path: "description.md", supplied: true, lines: 1 },
			{ path: "file.ts", supplied: false },
		],
		() => 4,
	);
	assert.equal(reads.coverage().complete, false);
	read(reads, 3, 2);
	assert.equal(reads.coverage().complete, false);
	read(reads, 1, 2);
	assert.equal(reads.coverage().complete, true);
});

void test("absolute and relative paths cover the same pinned file", () => {
	const reads = new SessionReads("/workspace", [{ path: "file.ts", supplied: false }], () => 1);
	reads.note({
		type: "tool_execution_start",
		toolCallId: "r",
		toolName: "read",
		args: { path: "/workspace/file.ts" },
	});
	reads.note({
		type: "tool_execution_end",
		toolCallId: "r",
		toolName: "read",
		isError: false,
		result: { content: [{ type: "text", text: "content" }], details: {} },
	});
	assert.equal(reads.coverage().complete, true);
});

for (const isError of [false, true]) {
	void test(`shell access has unknown coverage when isError=${isError}`, () => {
		const reads = new SessionReads("/workspace", [{ path: "file.ts", supplied: true, lines: 1 }]);
		reads.note({
			type: "tool_execution_start",
			toolCallId: "s",
			toolName: "bash",
			args: { command: "rg pattern file.ts | head" },
		});
		reads.note({
			type: "tool_execution_end",
			toolCallId: "s",
			toolName: "bash",
			isError,
			result: { content: [{ type: "text", text: "one hit" }], details: {} },
		});
		const coverage = reads.coverage();
		assert.equal(coverage.complete, false);
		assert.equal(coverage.failedReads, isError ? 1 : 0);
		assert.ok(
			coverage.omitted.some((omission) => omission.what === "evidence coverage is unknown"),
		);
	});
}

void test("the candidate contract refuses MET over missing brief material", () => {
	const reads = new SessionReads("/workspace", [{ path: "file.ts", supplied: false }]);
	const parsed = parseCandidate({
		outcome: "MET",
		summary: "Criterion met",
		explanation: "The described behavior meets the criterion.",
		citations: [
			{
				sourceKind: "scm.pull-request.core",
				sourcePath: "record.md",
				path: "record.md",
				startLine: 1,
				endLine: 1,
				quote: "work",
			},
		],
		evidence: {
			search: {
				consulted: ["scm.pull-request.core"],
				lookedFor: "criterion",
				boundary: "the item",
			},
		},
		coverage: reads.coverage(),
	});
	assert.equal(parsed.ok, false);
	assert.equal(parsed.reasonCode, "met_coverage_incomplete");
});

void test("shared unknown-coverage proof admits a qualified outcome and refuses MET", () => {
	const candidate: unknown = JSON.parse(
		readFileSync(
			new URL("../practice-results/host-coverage-unknown.json", import.meta.url),
			"utf8",
		),
	);
	assert.ok(isJsonObject(candidate));
	const qualified = parseCandidate(candidate);
	assert.ok(qualified.ok, qualified.ok ? "" : qualified.message);
	assert.ok(isJsonObject(candidate.evidence));
	delete candidate.evidence.openQuestions;
	const met = parseCandidate({ ...candidate, outcome: "MET" });
	assert.ok(!met.ok);
	assert.equal(met.reasonCode, "met_coverage_incomplete");
});

void test("discovery and search alone do not imply that relevant content was reviewed", () => {
	for (const toolName of ["grep", "find", "ls"]) {
		const reads = new SessionReads("/workspace");
		reads.note({ type: "tool_execution_start", toolCallId: "r", toolName, args: {} });
		reads.note({
			type: "tool_execution_end",
			toolCallId: "r",
			toolName,
			isError: false,
			result: { content: [{ type: "text", text: "no matches" }], details: {} },
		});
		assert.equal(reads.coverage().complete, false, toolName);
	}
});

void test("pending evidence work prevents complete coverage", () => {
	const reads = new SessionReads("/workspace", [{ path: "file.ts", supplied: true, lines: 1 }]);
	reads.note({
		type: "tool_execution_start",
		toolCallId: "r",
		toolName: "read",
		args: { path: "other.ts" },
	});
	assert.equal(reads.coverage().complete, false);
});

void test("native SDK pagination settles coverage only after the missing pages are read", async (t) => {
	const root = mkdtempSync(path.join(tmpdir(), "native-read-coverage-"));
	t.after(() => rmSync(root, { recursive: true, force: true }));
	writeFileSync(
		path.join(root, "file.ts"),
		Array.from({ length: 10_000 }, (_, i) => `line ${i + 1}`).join("\n"),
	);
	const reads = new SessionReads(root, [{ path: "file.ts", supplied: false }]);
	const tool = createReadTool(root);
	const inspect = async (offset: number, limit?: number) => {
		const args = { path: "file.ts", offset, ...(limit === undefined ? {} : { limit }) };
		reads.note({ type: "tool_execution_start", toolCallId: "read", toolName: "read", args });
		const result = await tool.execute("read", args);
		reads.note({
			type: "tool_execution_end",
			toolCallId: "read",
			toolName: "read",
			result,
			isError: false,
		});
	};
	await inspect(1);
	await inspect(9999);
	assert.equal(reads.coverage().complete, false);
	for (const offset of [2001, 4001, 6001, 8001]) {
		await inspect(offset, 2000);
	}
	assert.equal(reads.coverage().complete, true);
});

void test("Java-captured record aliases and extracted strings share complete parent coverage", () => {
	const fixture = z
		.object({ contents: z.record(z.string(), z.string()), manifest: z.unknown() })
		.parse(
			JSON.parse(readFileSync(new URL("record-coverage-capture.json", import.meta.url), "utf8")),
		);
	const { sourceFileSources } = folderCitationIndex(fixture.manifest);
	const texts = citableTextIndex(fixture.manifest);
	const sources = [...sourceFileSources.keys()].map((target) => ({
		path: target,
		supplied: false,
	}));
	const supplied = new SessionReads(
		"/workspace",
		sources.map((source) =>
			source.path === "evidence/metadata.json" ? { ...source, supplied: true, lines: 1 } : source,
		),
		undefined,
		texts,
	);
	assert.equal(supplied.coverage().complete, true);
	const reads = new SessionReads(
		"/workspace",
		sources,
		(target) => fixture.contents[target]?.split("\n").length ?? assert.fail(target),
		texts,
	);
	assert.equal(reads.coverage().complete, false, "omitted parent content stays unread");
	const record = (offset: number, limit: number, truncated: boolean) => {
		reads.note({
			type: "tool_execution_start",
			toolName: "read",
			toolCallId: "record",
			args: { path: "evidence/metadata-alias.json", offset, limit },
		});
		reads.note({
			type: "tool_execution_end",
			toolName: "read",
			toolCallId: "record",
			isError: false,
			result: {
				content: [{ type: "text", text: "captured record" }],
				details: truncated ? { truncation: { truncated: true, outputLines: 0 } } : {},
			},
		});
	};
	record(1, 1, true);
	assert.equal(
		reads.coverage().complete,
		false,
		"a truncated parent does not cover its extracted copies",
	);
	record(1, 1, false);
	assert.equal(
		reads.coverage().complete,
		true,
		"a complete alias read covers the same canonical record and its fields",
	);
});

void test("change consultation belongs only to the session that received or read it", () => {
	const supplied = new SessionReads("/workspace", [{ path: "file.ts", supplied: true, lines: 10 }]);
	const reader = new SessionReads("/workspace", [{ path: "file.ts", supplied: false }], () => 10);
	const sibling = new SessionReads("/workspace", [{ path: "file.ts", supplied: false }], () => 10);
	assert.equal(supplied.hasRead("file.ts"), true);
	assert.equal(reader.hasRead("file.ts"), false);
	read(reader, 1, 1);
	assert.equal(reader.hasRead("file.ts"), true);
	assert.equal(reader.hasRead("other.ts"), false);
	assert.equal(sibling.hasRead("file.ts"), false);
});

function bash(reads: SessionReads, command: string, text: string, cut = false, exit = 0) {
	reads.note({
		type: "tool_execution_start",
		toolCallId: "bash",
		toolName: "bash",
		args: { command },
	});
	reads.note({
		type: "tool_execution_end",
		toolCallId: "bash",
		toolName: "bash",
		isError: exit !== 0,
		result: {
			content: [{ type: "text", text }],
			details: cut ? { truncation: { truncated: true } } : {},
			structuredContent: { exit_code: exit, truncated: cut },
		},
	});
}

void test("literal shell reads cover only returned ranges and merge with native reads", (t) => {
	const root = mkdtempSync(path.join(tmpdir(), "bash-coverage-"));
	t.after(() => rmSync(root, { recursive: true, force: true }));
	writeFileSync(path.join(root, "file.ts"), "one\ntwo\nthree\nfour\nfive\n");
	const reads = new SessionReads(root, [{ path: "file.ts", supplied: false }]);
	bash(reads, "head -n 1 file.ts", "one\n");
	bash(reads, "tail -n 1 file.ts", "five\n");
	assert.equal(reads.coverage().complete, false);
	bash(reads, "sed -n '2,3p' file.ts", "two\nthree\n");
	assert.equal(reads.coverage().complete, false);
	read(reads, 4, 1, 1, false, "four");
	assert.equal(reads.coverage().complete, true);
});

void test("cat and nl return full coverage for exact pinned text including blank lines", (t) => {
	const root = mkdtempSync(path.join(tmpdir(), "bash-full-coverage-"));
	t.after(() => rmSync(root, { recursive: true, force: true }));
	writeFileSync(path.join(root, "file name.ts"), "one\n\nthree\n");
	for (const [command, output] of [
		["cat 'file name.ts'", "one\n\nthree\n"],
		['nl "file name.ts"', "     1\tone\n       \n     2\tthree\n"],
	] as const) {
		const reads = new SessionReads(root, [{ path: "file name.ts", supplied: false }]);
		bash(reads, command, output);
		assert.equal(reads.coverage().complete, true, command);
		assert.equal(reads.hasRead("file name.ts"), true);
	}
});

void test("numbered shell searches cover matched lines and never fill intervening gaps", (t) => {
	const root = mkdtempSync(path.join(tmpdir(), "bash-search-coverage-"));
	t.after(() => rmSync(root, { recursive: true, force: true }));
	writeFileSync(path.join(root, "file.ts"), "one\ntwo\nthree");
	for (const tool of ["rg", "grep"]) {
		const reads = new SessionReads(root, [{ path: "file.ts", supplied: false }]);
		bash(reads, `${tool} -n 'one|three' file.ts`, "1:one\n3:three\n");
		assert.equal(reads.coverage().complete, false);
		assert.equal(reads.hasRead("file.ts"), true);
		bash(reads, `${tool} -n two file.ts`, "2:two\n");
		assert.equal(reads.coverage().complete, true);
	}
});

void test("ambiguous, truncated, failed and forged shell reads cannot certify coverage", (t) => {
	const root = mkdtempSync(path.join(tmpdir(), "bash-unknown-coverage-"));
	t.after(() => rmSync(root, { recursive: true, force: true }));
	writeFileSync(path.join(root, "file.ts"), "one\ntwo\nthree");
	for (const [command, output, cut, exit] of [
		["cat file.ts | head", "one\n", false, 0],
		["cat file.ts > output", "", false, 0],
		["cat *.ts", "one\ntwo\nthree", false, 0],
		["cat $(echo file.ts)", "one\ntwo\nthree", false, 0],
		["cat file.ts; ls", "one\ntwo\nthree", false, 0],
		["cat\nfile.ts", "one\ntwo\nthree", false, 0],
		["cat file.ts file.ts", "one\ntwo\nthree", false, 0],
		["cat file.ts", "one\ntwo\nthree", true, 0],
		["cat file.ts", "one", false, 2],
		["cat file.ts", "one\n[Showing all lines]", false, 0],
		["rg -n one file.ts", "2:one\n", false, 0],
		["grep one file.ts", "one\n", false, 0],
	] as const) {
		const reads = new SessionReads(root);
		bash(reads, command, output, cut, exit);
		const coverage = reads.coverage();
		assert.equal(coverage.complete, false, command);
		assert.ok(
			coverage.omitted.some((omission) => omission.what === "evidence coverage is unknown"),
			command,
		);
	}
	const repaired = new SessionReads(root);
	bash(repaired, "cat file.ts", "one\n", true);
	bash(repaired, "cat file.ts", "one\ntwo\nthree");
	assert.equal(repaired.coverage().complete, true);
	const ambiguous = new SessionReads(root, [{ path: "file.ts", supplied: true, lines: 3 }]);
	bash(ambiguous, "cat file.ts | head", "one\n");
	assert.equal(
		ambiguous.coverage().complete,
		false,
		"known content cannot certify an ambiguous command's other access",
	);
});

void test("an empty numbered search is neither a failed read nor complete file coverage", (t) => {
	const root = mkdtempSync(path.join(tmpdir(), "bash-empty-search-"));
	t.after(() => rmSync(root, { recursive: true, force: true }));
	writeFileSync(path.join(root, "file.ts"), "one\n");
	const reads = new SessionReads(root);
	bash(reads, "rg -n absent file.ts", "(no output)\n\nCommand exited with code 1", false, 1);
	assert.equal(reads.coverage().complete, false);
	assert.equal(reads.coverage().failedReads, 0);
});

void test("native bash output receives receipts for each supported single-file form", async (t) => {
	const root = mkdtempSync(path.join(tmpdir(), "native-bash-coverage-"));
	t.after(() => rmSync(root, { recursive: true, force: true }));
	writeFileSync(path.join(root, "file.ts"), "one\ntwo\nthree");
	const tool = createBashTool(root);
	for (const command of [
		"cat file.ts",
		"head -n 3 file.ts",
		"tail -n 3 file.ts",
		"sed -n '1,3p' file.ts",
		"nl file.ts",
		"rg -n '.' file.ts",
		"grep -n '.' file.ts",
	]) {
		const reads = new SessionReads(root, [{ path: "file.ts", supplied: false }]);
		reads.note({
			type: "tool_execution_start",
			toolCallId: "native",
			toolName: "bash",
			args: { command },
		});
		const result = await tool.execute("native", { command });
		reads.note({
			type: "tool_execution_end",
			toolCallId: "native",
			toolName: "bash",
			result,
			isError: false,
		});
		assert.equal(reads.coverage().complete, true, `${command}: ${JSON.stringify(result)}`);
	}
});
