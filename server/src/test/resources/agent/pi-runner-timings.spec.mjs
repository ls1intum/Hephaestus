// pi-runner-timings.spec.mjs — how one agent budget is divided, and when the composition stage runs.
//
// The in-app lane's composition stage was gated on "the soft nudge did not fire". The nudge fires at
// 42.5% of the whole budget, so every review that took longer than that — which is every review of real
// size — silently produced no in-app message. The rule is now the leftover time itself, and the tests
// below fail if the decision ever goes back to reading the nudge as a shortage.
//
// The tail of that same window was the second half of the defect: with initialMs + retryMs === budget,
// reserving the retry allowance unconditionally left a review that used its whole initial allowance with
// a leftover of exactly 0. The reservation is now conditional on the retry still being able to fire, and
// the `resultFileValid` tests below fail if it goes back to being unconditional.
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

function budgetAfter(elapsedMs, resultFileValid = false) {
    return compositionBudgetMs({
        agentBudgetMs: BUDGET_MS,
        elapsedMs,
        retryMs: TIMEOUTS.retryMs,
        compositionCeilingMs: TIMEOUTS.compositionCeilingMs,
        resultFileValid,
    });
}

function composes({ elapsedMs, hardAborted = false, hasPersistedReviewState = true, resultFileValid = false }) {
    return shouldCompose({
        hasPersistedReviewState,
        hardAborted,
        resultFileValid,
        budgetMs: budgetAfter(elapsedMs, resultFileValid),
    });
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

test("a hard abort forfeits the stage while the retry can still fire", () => {
    assert.equal(composes({ elapsedMs: 0, hardAborted: true }), false);
});

// Measured on the test instance, 2026-08-16: a 13-file merge request against gpt-oss-120b spent
// 1,003s of a 1,200s budget, was hard-aborted at the initial timeout exactly as designed, and left
// behind a valid result file plus 25 persisted observations — and composed nothing, because the
// forfeit fired to protect a retry that a valid result file had already ruled out. The review with
// the most to say was the one guaranteed to say nothing.
test("a hard abort that left a valid result file still composes", () => {
    const elapsedMs = Math.floor(BUDGET_MS * (1003 / 1200));

    assert.equal(composes({ elapsedMs, hardAborted: true, resultFileValid: true }), true);
    assert.ok(
        budgetAfter(elapsedMs, true) >= COMPOSITION_MIN_BUDGET_MS,
        "the leftover was there all along — only the forfeit stood in the way",
    );
    assert.equal(
        composes({ elapsedMs, hardAborted: true, resultFileValid: false }),
        false,
        "without a result file the retry is still owed its turn",
    );
});

test("a hard abort never composes out of nothing", () => {
    assert.equal(
        composes({ elapsedMs: 0, hardAborted: true, resultFileValid: true, hasPersistedReviewState: false }),
        false,
    );
});

test("nothing durable to compose from means nothing is composed", () => {
    assert.equal(composes({ elapsedMs: 0, hasPersistedReviewState: false }), false);
});

test("a review still owed a retry never has that allowance spent on composition", () => {
    for (const elapsedMs of [0, 100_000, 400_000, 700_000, 764_000]) {
        const leftoverMs = BUDGET_MS - elapsedMs;
        assert.ok(
            budgetAfter(elapsedMs) <= leftoverMs - TIMEOUTS.retryMs,
            `at ${elapsedMs}ms elapsed the stage must leave the retry allowance untouched`,
        );
        assert.ok(budgetAfter(elapsedMs) <= TIMEOUTS.compositionCeilingMs);
    }
});

// The tail regression. deriveTimeouts spends the whole budget on initial + retry, so a review that used
// its full initial allowance is at 85% elapsed with 15% left — exactly the retry's size. Reserve it and
// the leftover is 0 and composition never starts; every healthy full-length review composed nothing.
test("a review that used its whole initial allowance still composes once its result file is valid", () => {
    const elapsedMs = TIMEOUTS.initialMs;

    assert.equal(elapsedMs + TIMEOUTS.retryMs, BUDGET_MS, "initial + retry is the whole budget");
    assert.equal(budgetAfter(elapsedMs, false), 0, "reserving the retry leaves nothing at the 85% mark");
    assert.equal(composes({ elapsedMs, resultFileValid: false }), false);

    assert.equal(
        budgetAfter(elapsedMs, true),
        TIMEOUTS.compositionCeilingMs,
        "a settled review gets the full ceiling, not the slack it happened to leave",
    );
    assert.equal(composes({ elapsedMs, resultFileValid: true }), true);
});

test("a valid result file never makes composition start later or spend more than its ceiling", () => {
    for (const spent of [0, 0.25, 0.5, 0.7, 0.85, 0.95, 1]) {
        const elapsedMs = Math.floor(BUDGET_MS * spent);
        assert.ok(
            budgetAfter(elapsedMs, true) >= budgetAfter(elapsedMs, false),
            `at ${spent} of the budget, settling the result file must never shrink the leftover`,
        );
        assert.ok(
            budgetAfter(elapsedMs, true) <= TIMEOUTS.compositionCeilingMs,
            `at ${spent} of the budget, the ceiling still binds`,
        );
        assert.ok(
            budgetAfter(elapsedMs, true) <= BUDGET_MS - elapsedMs,
            `at ${spent} of the budget, the stage cannot be handed time that does not exist`,
        );
    }
});

test("a review that spent everything is refused rather than handed a negative timer", () => {
    assert.ok(budgetAfter(BUDGET_MS) < 0);
    assert.equal(composes({ elapsedMs: BUDGET_MS }), false);
    // Even with its result file settled: the whole budget is gone, so there is no turn to start.
    assert.equal(budgetAfter(BUDGET_MS, true), 0);
    assert.equal(composes({ elapsedMs: BUDGET_MS, resultFileValid: true }), false);
});

// The reservation is the pessimistic default, so a caller that forgets to answer the result-file
// question is told the smaller number rather than handed the retry's time by accident.
test("omitting the result-file answer reserves the retry", () => {
    const elapsedMs = TIMEOUTS.initialMs;

    assert.equal(
        compositionBudgetMs({
            agentBudgetMs: BUDGET_MS,
            elapsedMs,
            retryMs: TIMEOUTS.retryMs,
            compositionCeilingMs: TIMEOUTS.compositionCeilingMs,
        }),
        0,
    );
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
