import assert from "node:assert/strict";
import { readFileSync, mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { createReadTool } from "@earendil-works/pi-coding-agent";
import { parseCandidate } from "../../../../../../docker/agents/practice-scripts/lib/core/contract.ts";
import { isJsonObject } from "../../../../../../docker/agents/practice-scripts/lib/core/json.ts";
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
