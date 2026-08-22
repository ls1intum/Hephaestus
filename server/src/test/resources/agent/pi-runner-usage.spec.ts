// pi-runner-usage.spec.ts — what a session is reported to have spent, and why it is not a walk of
// session.messages.
//
// The defect these exist for: usage.json was derived by iterating the live session message list, and
// compaction is enabled for every session the runner creates. A compacted assistant message is gone
// from that list, and its tokens went with it. On a live 29-practice review the proxy had already
// forwarded 143 calls and 4,274,916 input tokens while the runner reported 26 and 969,765 — and the
// server preferred the runner's number, so the ledger booked a quarter of the spend and the monthly
// budget cap was computed from it.
//
// Run locally with:
//   pnpm run test:agents

import assert from "node:assert/strict";
import test from "node:test";
import {
	type AssistantMessage,
	addAssistantUsage,
	extractUsageFromSession,
	newUsageLedger,
	type SessionMessage,
} from "../../../main/resources/agent/pi-runner-usage.ts";

/**
 * A real assistant message, in the shape the SDK actually delivers one.
 *
 * These fixtures used to be written by hand from memory, and two fields that memory invented cost real
 * money: the dedupe key was `id`, which an assistant message does not have, and a `usage.reasoning`
 * bucket, which no provider emits. Both passed here and did nothing in production. Building them as
 * `AssistantMessage` is what stops that from being possible again.
 */
function assistant(
	responseId: string,
	{ input = 0, output = 0, cacheRead = 0, cacheWrite = 0, cost = 0 } = {},
): AssistantMessage {
	return {
		responseId,
		role: "assistant",
		content: [],
		api: "openai-completions",
		provider: "hephaestus",
		model: "gpt-5",
		stopReason: "stop",
		usage: {
			input,
			output,
			cacheRead,
			cacheWrite,
			totalTokens: input + output + cacheRead + cacheWrite,
			cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, total: cost },
		},
		timestamp: 0,
	};
}

/** A session whose message list still holds everything that happened. */
function sessionOf(...messages: SessionMessage[]) {
	return { messages };
}

// `void`: node:test's own runner owns the promise each test hands back, and awaiting one here
// would register the next test only after the previous had finished.
void test("a ledger with nothing in it reports nothing", () => {
	const ledger = newUsageLedger();

	assert.equal(ledger.totalCalls, 0);
	assert.equal(ledger.inputTokens, 0);
});

void test("every bucket of an assistant message lands on the ledger", () => {
	const ledger = newUsageLedger();

	addAssistantUsage(
		ledger,
		assistant("a", { input: 100, output: 20, cacheRead: 7, cacheWrite: 3, cost: 0.5 }),
	);

	assert.equal(ledger.totalCalls, 1);
	assert.equal(ledger.inputTokens, 100);
	assert.equal(ledger.outputTokens, 20);
	assert.equal(ledger.cacheReadTokens, 7);
	assert.equal(ledger.cacheWriteTokens, 3);
	assert.equal(ledger.costUsd, 0.5);
	assert.equal(ledger.model, "gpt-5");
});

void test("only assistant messages that carry usage are billable", () => {
	const ledger = newUsageLedger();
	// AssistantMessage declares `usage` required; ReportedMessage is the shape the ledger is actually
	// handed, which is what makes the case the guard exists for — a turn that failed before the
	// provider answered — expressible at all.
	const { usage: _unused, ...assistantWithoutUsage } = assistant("a");

	// A guard that checked only for a usage block, ignoring role, would bill this one. Built by
	// assignment rather than as one literal, because a usage block is not part of a user message.
	const userMessage: SessionMessage = { role: "user", content: "not a bill", timestamp: 0 };
	addAssistantUsage(
		ledger,
		Object.assign(userMessage, {
			usage: { input: 999, output: 0, cacheRead: 0, cacheWrite: 0, totalTokens: 999, cost: 0 },
		}),
	);
	addAssistantUsage(ledger, assistantWithoutUsage);
	addAssistantUsage(ledger, null);
	addAssistantUsage(ledger, undefined);

	assert.equal(ledger.totalCalls, 0);
	assert.equal(ledger.inputTokens, 0);
});

// Over-billing is the one error the maximum-taking on the server side could introduce, so the source
// that feeds it must not be able to count the same call twice.
void test("the same message counted twice is billed once", () => {
	const ledger = newUsageLedger();
	const message = assistant("a", { input: 100 });

	addAssistantUsage(ledger, message);
	addAssistantUsage(ledger, message);

	assert.equal(ledger.totalCalls, 1);
	assert.equal(ledger.inputTokens, 100);
});

// THE regression. The ledger was fed at message_end, so the call is on it before compaction can drop
// the message; a walk of what compaction left behind sees only the survivor.
void test("a call compaction removed from the session is still billed", () => {
	const ledger = newUsageLedger();
	const compacted = assistant("dropped", { input: 4_000_000, output: 300_000 });
	const survivor = assistant("kept", { input: 900_000, output: 60_000 });

	addAssistantUsage(ledger, compacted);
	addAssistantUsage(ledger, survivor);

	// What the session can still show afterwards: one message.
	const reported = extractUsageFromSession(sessionOf(survivor), ledger);

	assert.equal(reported.totalCalls, 2);
	assert.equal(reported.inputTokens, 4_900_000);
	assert.equal(reported.outputTokens, 360_000);

	// And what the old derivation would have reported for the same run.
	const walkOnly = extractUsageFromSession(sessionOf(survivor));
	assert.equal(walkOnly.totalCalls, 1);
	assert.ok(
		reported.inputTokens > walkOnly.inputTokens * 4,
		"the compacted call is the difference between the real bill and a quarter of it",
	);
});

// The stream ledger assumes the SDK puts a usage block on the message_end event. If it ever stops
// doing so the ledger goes empty, and the report must fall back to the walk rather than to zero.
void test("an empty stream ledger never makes the report smaller than the message walk", () => {
	const messages = [assistant("a", { input: 100 }), assistant("b", { input: 250, output: 30 })];

	const reported = extractUsageFromSession(sessionOf(...messages), newUsageLedger());

	assert.equal(reported.totalCalls, 2);
	assert.equal(reported.inputTokens, 350);
	assert.equal(reported.outputTokens, 30);
});

void test("with no ledger at all the report is exactly the message walk", () => {
	const reported = extractUsageFromSession(sessionOf(assistant("a", { input: 100, output: 5 })));

	assert.equal(reported.totalCalls, 1);
	assert.equal(reported.inputTokens, 100);
	assert.equal(reported.outputTokens, 5);
});

void test("every bucket is taken from whichever view saw more, independently", () => {
	const walked = assistant("kept", { input: 10, output: 9_000, cacheWrite: 40 });
	const ledger = newUsageLedger();
	addAssistantUsage(ledger, assistant("dropped", { input: 5_000, cacheRead: 70 }));

	const reported = extractUsageFromSession(sessionOf(walked), ledger);

	assert.equal(reported.inputTokens, 5_000);
	assert.equal(reported.outputTokens, 9_000);
	assert.equal(reported.cacheReadTokens, 70);
	assert.equal(reported.cacheWriteTokens, 40);
});

void test("a session that never ran reports zero rather than throwing", () => {
	const reported = extractUsageFromSession({}, newUsageLedger());

	assert.equal(reported.totalCalls, 0);
	assert.equal(reported.inputTokens, 0);
	assert.equal(reported.model, null);
});
