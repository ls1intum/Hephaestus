import assert from "node:assert/strict";
import test from "node:test";
import {
	deriveTimeouts,
	deriveTurnTiming,
} from "../../../main/resources/agent/pi-runner-timings.ts";

// `void`: node:test's own runner owns the promise each test hands back, and awaiting one here
// would register the next test only after the previous had finished.
void test("review budget reserves fifteen percent for a retry", () => {
	assert.deepEqual(deriveTimeouts(900_000), {
		initialMs: 765_000,
		retryMs: 135_000,
		compositionMs: 0,
	});
});

void test("a small review never allocates more time than it owns", () => {
	assert.deepEqual(deriveTimeouts(10_000), {
		initialMs: 8_500,
		retryMs: 1_500,
		compositionMs: 0,
	});
});

void test("a composing review reserves time for intervention before detection starts", () => {
	assert.deepEqual(deriveTimeouts(900_000, true), {
		initialMs: 650_250,
		retryMs: 114_750,
		compositionMs: 135_000,
	});
});

void test("composition and retry stay inside a small budget", () => {
	assert.deepEqual(deriveTimeouts(10_000, true), {
		initialMs: 7_225,
		retryMs: 1_275,
		compositionMs: 1_500,
	});
});

void test("the next turn adapts to the remaining time and practice batches", () => {
	assert.deepEqual(deriveTurnTiming(600_000, 5), {
		fairShareMs: 120_000,
		softNudgeMs: 72_000,
	});
	assert.deepEqual(deriveTurnTiming(119_999, 2), {
		fairShareMs: 59_999,
		softNudgeMs: 35_999,
	});
});

for (const invalid of [0, -1, 1.5, Number.NaN]) {
	void test(`rejects invalid remaining turn count ${invalid}`, () => {
		assert.throws(() => deriveTurnTiming(1_000, invalid), /positive integer/);
	});
}
