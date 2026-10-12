import { describe, expect, it } from "vitest";

import type { PracticeScriptModelUse, PracticeScriptRun } from "@/api/types.gen";

import {
	practiceScriptModelsToCheck,
	practiceScriptNotRated,
} from "./practice-script-not-rated-defs";
import {
	PRACTICE_SCRIPT_RUN_STATUS_DEFS,
	practiceScriptFix,
	practiceScriptRunSentence,
} from "./practice-script-run-status-defs";

const run = (over: Partial<PracticeScriptRun>): PracticeScriptRun => ({
	status: "OK",
	programKind: "CUSTOM",
	models: [],
	...over,
});

const model = (over: Partial<PracticeScriptModelUse>): PracticeScriptModelUse => ({
	purpose: "PRACTICE_DECISION",
	need: "REQUIRED",
	bound: true,
	notRated: [],
	...over,
});

const sentence = practiceScriptRunSentence;
const result = (value: PracticeScriptRun) =>
	PRACTICE_SCRIPT_RUN_STATUS_DEFS[value.status].result(value);

describe("how a practice program run ended", () => {
	it.each([
		[run({}), "Practice script returned a result."],
		[run({ path: "CODE" }), "Practice script returned a result from its code alone."],
		[run({ path: "MIXED" }), "Practice script returned a result with its models and the review."],
		[
			run({ programKind: "BUILTIN", path: "REVIEW" }),
			"Built-in review returned a result through the review.",
		],
		[run({ status: "FAILED" }), "Practice script stopped without a result."],
		[
			run({ status: "FAILED", stopReason: "DEADLINE" }),
			"Practice script stopped without a result: it ran out of time.",
		],
		[
			run({ status: "FAILED", stopReason: "INVALID_PROGRAM" }),
			"Practice script stopped without a result: it is not a program the review can run.",
		],
		[run({ status: "NOT_REACHED" }), "The review ended before the practice script started."],
		[
			run({ status: "NOT_REACHED", programKind: "BUILTIN" }),
			"The review ended before the built-in review started.",
		],
		[run({ status: "NOT_FINISHED" }), "Practice script did not finish."],
	])("says %o as %s", (value, expected) => {
		expect(sentence(value)).toBe(expected);
	});

	it.each([
		[run({}), "Returned a result"],
		[run({ path: "MODELS" }), "Result from its models"],
		[run({ status: "SKIPPED" }), "Did not run"],
		[run({ status: "FAILED" }), "Failed"],
		[run({ status: "FAILED", stopReason: "BUDGET" }), "Failed: out of budget"],
		[run({ status: "NOT_REACHED" }), "Not reached"],
		[run({ status: "NOT_FINISHED" }), "Did not finish"],
	])("gives %o the result %s", (value, expected) => {
		expect(result(value)).toBe(expected);
	});

	it("names the required models a skipped script had none for, and never counts places", () => {
		const skipped = run({
			status: "SKIPPED",
			models: [
				model({ bound: false }),
				model({ purpose: "PRACTICE_EMBEDDING", need: "OPTIONAL", bound: false }),
				model({ purpose: "PRACTICE_RERANKING", bound: false }),
			],
		});
		expect(sentence(skipped)).toBe(
			"Practice script did not run: no decision model or reranking model was set for this review.",
		);
		expect(sentence(run({ status: "SKIPPED" }))).toBe("Practice script did not run.");
	});
});

describe("what a practice script run did not rate", () => {
	it("counts calls, and says nothing when every call was rated", () => {
		expect(sentence(run({ path: "MODELS", models: [model({})] }))).toBe(
			"Practice script returned a result with its models.",
		);
		expect(
			sentence(
				run({
					path: "MODELS",
					models: [
						model({ notRated: [{ reason: "DEADLINE", count: 1 }] }),
						model({
							purpose: "PRACTICE_EMBEDDING",
							notRated: [{ reason: "UNAVAILABLE", count: 3 }],
						}),
					],
				}),
			),
		).toBe("Practice script returned a result with its models. 4 calls were not rated.");
		const unrated = model({ notRated: [{ reason: "DEADLINE", count: 1 }] });
		expect(sentence(run({ status: "FAILED", models: [unrated] }))).toBe(
			"Practice script stopped without a result. 1 call was not rated.",
		);
	});

	it("does not count what a skipped script never asked", () => {
		expect(
			sentence(
				run({
					status: "SKIPPED",
					models: [model({ bound: false, notRated: [{ reason: "UNAVAILABLE", count: 2 }] })],
				}),
			),
		).toBe("Practice script did not run: no decision model was set for this review.");
	});

	it("sums each reason across models, in registry order, and skips zero counts", () => {
		expect(
			practiceScriptNotRated([
				model({
					notRated: [
						{ reason: "OFF_FORMAT", count: 1 },
						{ reason: "DEADLINE", count: 2 },
					],
				}),
				model({
					purpose: "PRACTICE_RERANKING",
					notRated: [
						{ reason: "DEADLINE", count: 3 },
						{ reason: "BUDGET", count: 0 },
					],
				}),
			]),
		).toStrictEqual({
			count: 6,
			reasons: ["5 too slow", "1 wrong format"],
			outsideTheModel: true,
		});
		expect(practiceScriptNotRated([model({})])).toStrictEqual({
			count: 0,
			reasons: [],
			outsideTheModel: false,
		});
	});
});

describe("the one fix a run offers", () => {
	it("assigns the first required model a skipped script had none for", () => {
		expect(
			practiceScriptFix(
				run({
					status: "SKIPPED",
					models: [
						model({ purpose: "PRACTICE_EMBEDDING", need: "OPTIONAL", bound: false }),
						model({ purpose: "PRACTICE_RERANKING", bound: false }),
					],
				}),
			),
		).toStrictEqual({ kind: "ASSIGN", purpose: "PRACTICE_RERANKING" });
		expect(practiceScriptFix(run({ status: "SKIPPED" }))).toBeUndefined();
	});

	it("edits a script that failed, even when its models also went unrated", () => {
		expect(
			practiceScriptFix(
				run({ status: "FAILED", models: [model({ notRated: [{ reason: "REFUSED", count: 1 }] })] }),
			),
		).toStrictEqual({ kind: "EDIT" });
		expect(practiceScriptFix(run({ status: "FAILED", stopReason: "ERROR" }))).toStrictEqual({
			kind: "EDIT",
		});
	});

	it("offers no edit for the built-in review or a cancelled review", () => {
		expect(
			practiceScriptFix(run({ status: "FAILED", programKind: "BUILTIN", stopReason: "ERROR" })),
		).toBeUndefined();
		expect(practiceScriptFix(run({ status: "FAILED", stopReason: "CANCELLED" }))).toBeUndefined();
	});

	it.each(["DEADLINE", "BUDGET"] as const)(
		"sends a program that failed on %s to the guide on programs that stop early",
		(stopReason) => {
			expect(practiceScriptFix(run({ status: "FAILED", stopReason }))).toStrictEqual({
				kind: "GUIDE",
				guide: "STOPPED_EARLY",
			});
		},
	);

	it.each([
		["UNAVAILABLE", ["PRACTICE_DECISION"]],
		["BUDGET", []],
		["DEADLINE", []],
		["TOO_LARGE", []],
		["OFF_FORMAT", ["PRACTICE_DECISION"]],
		["REFUSED", ["PRACTICE_DECISION"]],
		["ERROR", ["PRACTICE_DECISION"]],
	] as const)("offers to check the model for %s: %o", (reason, offered) => {
		const models = [model({ notRated: [{ reason, count: 1 }] })];
		expect(practiceScriptModelsToCheck(models)).toStrictEqual(offered);
	});

	it("checks a model only where the model is the way out", () => {
		const models = [
			model({ notRated: [{ reason: "DEADLINE", count: 5 }] }),
			model({ purpose: "PRACTICE_EMBEDDING", notRated: [{ reason: "REFUSED", count: 1 }] }),
			model({ purpose: "PRACTICE_RERANKING", notRated: [{ reason: "BUDGET", count: 2 }] }),
		];
		expect(practiceScriptModelsToCheck(models)).toStrictEqual(["PRACTICE_EMBEDDING"]);
		expect(practiceScriptFix(run({ models }))).toStrictEqual({
			kind: "CHECK",
			purpose: "PRACTICE_EMBEDDING",
		});
		expect(practiceScriptFix(run({ path: "CODE" }))).toBeUndefined();
	});

	it.each(["BUDGET", "DEADLINE", "TOO_LARGE"] as const)(
		"sends %s, which no model change fixes, to the guide that says who can",
		(reason) => {
			const models = [model({ notRated: [{ reason, count: 2 }] })];
			expect(practiceScriptFix(run({ models }))).toStrictEqual({
				kind: "GUIDE",
				guide: "UNRATED_CALLS",
			});
		},
	);

	it.each(["NOT_REACHED", "NOT_FINISHED"] as const)(
		"sends a program that is %s to the guide on programs that stop early, before any model to check",
		(status) => {
			const refused = model({ notRated: [{ reason: "REFUSED", count: 1 }] });
			const stoppedEarly = { kind: "GUIDE", guide: "STOPPED_EARLY" };
			expect(practiceScriptFix(run({ status }))).toStrictEqual(stoppedEarly);
			expect(practiceScriptFix(run({ status, models: [refused] }))).toStrictEqual(stoppedEarly);
		},
	);
});
