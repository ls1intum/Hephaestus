// Run locally with:
//   pnpm run test:agents

import assert from "node:assert/strict";
import test from "node:test";
import {
	type ComposedFeedbackEnvelope,
	type ComposedFeedbackUnit,
	undeliverableUnits,
} from "../../../main/resources/agent/pi-runner-composition.ts";

const supersede = (threadKey: string): ComposedFeedbackUnit => ({
	action: "SUPERSEDE",
	channel: "IN_CONTEXT",
	practiceSlug: "writes-focused-pull-requests",
	supersedesThreadKey: threadKey,
});

// `void`: node:test's own runner owns the promise each test hands back, and awaiting one here
// would register the next test only after the previous had finished.
void test("an envelope that lists the threads its units supersede delivers all of them", () => {
	const envelope = {
		preparedThreadKeys: ["t-1", "t-2"],
		units: [supersede("t-1"), supersede("t-2")],
	};

	assert.deepEqual(undeliverableUnits(envelope), []);
});

void test("a superseding unit is undeliverable when the envelope lists no threads", () => {
	// The incident: the tool was given the staged keys and accepted the unit, the envelope was not.
	const envelope = { preparedThreadKeys: [], units: [supersede("t-1")] };

	assert.deepEqual(undeliverableUnits(envelope), [supersede("t-1")]);
});

void test("only the unit naming an unlisted thread is undeliverable", () => {
	const envelope = { preparedThreadKeys: ["t-1"], units: [supersede("t-1"), supersede("t-9")] };

	assert.deepEqual(undeliverableUnits(envelope), [supersede("t-9")]);
});

void test("units that supersede nothing are unaffected by an empty thread list", () => {
	const envelope: ComposedFeedbackEnvelope = {
		preparedThreadKeys: [],
		units: [
			{ action: "NEW", channel: "IN_APP", practiceSlug: "p" },
			{ action: "WITHHOLD", channel: "IN_CHAT", practiceSlug: "p", withholdReason: "ALREADY_SAID" },
		],
	};

	assert.deepEqual(undeliverableUnits(envelope), []);
});

void test("an envelope missing the fields entirely reports nothing rather than throwing", () => {
	assert.deepEqual(undeliverableUnits({}), []);
	assert.deepEqual(undeliverableUnits(undefined), []);
});
