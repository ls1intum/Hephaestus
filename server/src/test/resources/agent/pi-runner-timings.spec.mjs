// pi-runner-timings.spec.mjs — how one agent budget is divided, and when the composition stage runs.
//
// The reflection lane's composition stage was gated on "the soft nudge did not fire". The nudge fires at
// 42.5% of the whole budget, so every review that took longer than that — which is every review of real
// size — silently produced no reflection message. The rule is now the leftover time itself, and the tests
// below fail if the decision ever goes back to reading the nudge as a shortage.
//
// Wired into CI via `.github/workflows/ci-quality-gates.yml` (application-server-quality step). Run
// locally with:
//   node --test server/src/test/resources/agent/pi-runner-timings.spec.mjs

import test from "node:test";
import assert from "node:assert/strict";

import {
    COMPOSITION_MIN_BUDGET_MS,
    compositionBudgetMs,
    deriveTimeouts,
    shouldCompose,
} from "../../../main/resources/agent/pi-runner-timings.mjs";

// 15 minutes: a shipped-default order of magnitude, large enough that no floor in deriveTimeouts binds.
const BUDGET_MS = 900_000;
const TIMEOUTS = deriveTimeouts(BUDGET_MS);

function budgetAfter(elapsedMs) {
    return compositionBudgetMs({
        agentBudgetMs: BUDGET_MS,
        elapsedMs,
        retryMs: TIMEOUTS.retryMs,
        compositionCeilingMs: TIMEOUTS.compositionCeilingMs,
    });
}

function composes({ elapsedMs, hardAborted = false, hasPersistedReviewState = true }) {
    return shouldCompose({ hasPersistedReviewState, hardAborted, budgetMs: budgetAfter(elapsedMs) });
}

test("the nudge is a steer, not a deadline: it lands before half the budget is gone", () => {
    assert.equal(TIMEOUTS.softNudgeMs, Math.floor(TIMEOUTS.initialMs * 0.5));
    assert.ok(
        TIMEOUTS.softNudgeMs < BUDGET_MS * 0.5,
        `nudge at ${TIMEOUTS.softNudgeMs}ms of ${BUDGET_MS}ms is under half the budget`,
    );
});

// The regression this suite exists for. A 29-practice fan-out passes the nudge and still has most of an
// hour's worth of proportion left; every one of these used to compose nothing.
test("a review that was merely nudged still composes", () => {
    for (const spent of [0.45, 0.5, 0.6, 0.7, 0.8]) {
        const elapsedMs = Math.floor(BUDGET_MS * spent);
        assert.ok(elapsedMs > TIMEOUTS.softNudgeMs, `${spent} of the budget is past the nudge`);
        assert.equal(composes({ elapsedMs }), true, `a review ${spent} of the way in must compose`);
    }
});

test("the moment composition stops starting is set by the leftover, not by the nudge", () => {
    const lastComposingMs = BUDGET_MS - TIMEOUTS.retryMs - COMPOSITION_MIN_BUDGET_MS;

    assert.equal(composes({ elapsedMs: lastComposingMs }), true);
    assert.equal(composes({ elapsedMs: lastComposingMs + 1 }), false);
    assert.ok(
        lastComposingMs > TIMEOUTS.softNudgeMs,
        "a nudged review is still well inside the window that composes",
    );
});

test("a hard abort forfeits the stage even with the whole budget untouched", () => {
    assert.equal(composes({ elapsedMs: 0, hardAborted: true }), false);
});

test("nothing durable to compose from means nothing is composed", () => {
    assert.equal(composes({ elapsedMs: 0, hasPersistedReviewState: false }), false);
});

test("the retry allowance is never spent on composition", () => {
    for (const elapsedMs of [0, 100_000, 400_000, 700_000, 764_000]) {
        const leftoverMs = BUDGET_MS - elapsedMs;
        assert.ok(
            budgetAfter(elapsedMs) <= leftoverMs - TIMEOUTS.retryMs,
            `at ${elapsedMs}ms elapsed the stage must leave the retry allowance untouched`,
        );
        assert.ok(budgetAfter(elapsedMs) <= TIMEOUTS.compositionCeilingMs);
    }
});

test("a review that spent everything is refused rather than handed a negative timer", () => {
    assert.ok(budgetAfter(BUDGET_MS) < 0);
    assert.equal(composes({ elapsedMs: BUDGET_MS }), false);
});

test("floors keep a small budget workable", () => {
    const tiny = deriveTimeouts(10_000);

    assert.deepEqual(tiny, {
        initialMs: 60_000,
        retryMs: 30_000,
        softNudgeMs: 45_000,
        compositionCeilingMs: 60_000,
    });
});
