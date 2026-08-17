import test from "node:test";
import assert from "node:assert/strict";
import { deriveTimeouts } from "../../../main/resources/agent/pi-runner-timings.mjs";

test("review budget reserves fifteen percent for a retry", () => {
    assert.deepEqual(deriveTimeouts(900_000), {
        initialMs: 765_000,
        retryMs: 135_000,
        softNudgeMs: 382_500,
        compositionMs: 0,
    });
});

test("floors keep a small review budget workable", () => {
    assert.deepEqual(deriveTimeouts(10_000), {
        initialMs: 60_000,
        retryMs: 30_000,
        softNudgeMs: 45_000,
        compositionMs: 0,
    });
});

test("a composing review reserves time for intervention before detection starts", () => {
    assert.deepEqual(deriveTimeouts(900_000, true), {
        initialMs: 650_250,
        retryMs: 114_750,
        softNudgeMs: 325_125,
        compositionMs: 135_000,
    });
});
