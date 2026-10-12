import { assert, describe, expect, it } from "vitest";

import { branchOutcome, silentNotMet, stageCounts } from "./lineage-chain";
import {
	LINEAGE_FB,
	LINEAGE_OBS,
	wholeReview,
	wholeReviewStartedByMerge,
} from "./lineage-fixtures";
import { lineageStateCase } from "./lineage-state-fixtures";
import { mergeNote, parseTraceFocus, reviewMoments, traceStepKey, tracedBy } from "./review-trace";

describe("the record a review's page opens on", () => {
	it("reads back what it writes", () => {
		for (const focus of [
			{ kind: "work" as const },
			{ kind: "results" as const },
			{ kind: "result" as const, id: LINEAGE_OBS.cache404 },
			{ kind: "feedback" as const, id: LINEAGE_FB.card },
		]) {
			expect(parseTraceFocus(traceStepKey(focus))).toStrictEqual(focus);
		}
	});

	it("drops a value it cannot pin", () => {
		expect(parseTraceFocus("review:1")).toBeUndefined();
		expect(parseTraceFocus("result:")).toBeUndefined();
		expect(parseTraceFocus(undefined)).toBeUndefined();
	});
});

describe("the path a pinned node draws strong", () => {
	it("runs from a result to the feedback written from it", () => {
		expect(tracedBy(wholeReview, { kind: "result", id: LINEAGE_OBS.cache404 })).toStrictEqual({
			results: new Set([LINEAGE_OBS.cache404]),
			feedback: new Set([LINEAGE_FB.onGitHub, LINEAGE_FB.card, LINEAGE_FB.afterMerge]),
		});
	});

	it("leaves out what another review wrote when the review is pinned", () => {
		const traced = tracedBy(wholeReview, { kind: "review" });
		expect(traced?.feedback.has(LINEAGE_FB.earlier)).toBe(false);
		expect(traced?.feedback.has(LINEAGE_FB.card)).toBe(true);
	});

	it("dims nothing for the work", () => {
		expect(tracedBy(wholeReview, { kind: "work" })).toBeUndefined();
	});
});

describe("the merge and the review", () => {
	it("says a review the merge started is by design", () => {
		expect(mergeNote(wholeReviewStartedByMerge)).toStrictEqual({
			tone: "by-design",
			text: "The merge started this review. Practices that look at a merge run then by design. 1 piece of feedback was withheld as already merged.",
		});
	});

	it("says a review that ran into the merge is a problem", () => {
		expect(mergeNote(wholeReview)).toStrictEqual({
			tone: "problem",
			text: "The work merged while this review ran, 1 h 16 min after it started. 1 piece of feedback was withheld as already merged.",
		});
	});

	it("lists what started the review, a push folded into it, the merge, and its end in order", () => {
		expect(reviewMoments(wholeReview)).toMatchObject([
			{ kind: "moment", moment: { kind: "READY", startedReview: true } },
			{ kind: "review-started" },
			{ kind: "moment", moment: { kind: "PUSHED", joinedReview: true } },
			{ kind: "moment", moment: { kind: "MERGED" } },
			{ kind: "review-finished" },
		]);
	});

	it("tells a merge while the review waited in the queue from one while it ran", () => {
		expect(mergeNote(lineageStateCase("merged-in-queue").lineage)?.text).toBe(
			"The work merged while this review waited in the queue, 8 min before it started. 1 piece of feedback was withheld as already merged.",
		);
		expect(mergeNote(lineageStateCase("merged-before").lineage)?.text).toMatch(
			/^The work merged 1 h 25 min before this review was asked for\./u,
		);
		expect(mergeNote(lineageStateCase("merged-after").lineage)?.tone).toBe("plain");
		expect(mergeNote(lineageStateCase("never-merged").lineage)).toBeUndefined();
	});

	it("counts only a merge moment that started the review as the review's start", () => {
		expect(mergeNote(lineageStateCase("merged-during").lineage)?.tone).toBe("problem");
	});
});

function outcomes(key: string): string[] {
	return lineageStateCase(key).lineage.feedback.map(
		(entry) => branchOutcome(entry, undefined).text,
	);
}

describe("what became of a piece of feedback", () => {
	it("names an admin's decision and a withdrawal before the stored state", () => {
		expect(outcomes("decisions")).toStrictEqual([
			"Awaiting approval",
			"Delivered on GitHub",
			"Rejected by an admin",
			"Withdrawn by an admin",
		]);
	});

	it("says a withdrawn piece was delivered only where a delivery is recorded", () => {
		const withdrawn = lineageStateCase("decisions").lineage.feedback.find(
			(entry) => entry.withdrawnAt !== undefined,
		);
		assert(withdrawn?.deliveredAt !== undefined);
		expect(branchOutcome(withdrawn, undefined).detail).toMatch(
			/^Delivered .+, then taken off them\.$/u,
		);
		const prepared = { ...withdrawn, deliveryState: "PREPARED" as const, deliveredAt: undefined };
		expect(branchOutcome(prepared, undefined)).toMatchObject({
			text: "Withdrawn by an admin",
			detail: "Taken back before it reached the developer.",
		});
	});

	it("says a card was shown before it was withheld", () => {
		expect(outcomes("shown-then-withheld")).toStrictEqual([
			"Delivered, later withheld: no longer covered",
		]);
	});

	it("dates a replaced piece by the piece that replaced it", () => {
		const [, replaced] = lineageStateCase("replaced").lineage.feedback;
		assert(replaced?.deliveryState === "SUPERSEDED");
		expect(branchOutcome(replaced, undefined).at).toStrictEqual(replaced.replacedAt);
	});

	it("has a short reason for every withholding reason", () => {
		expect(outcomes("withholding-families")).toStrictEqual([
			"Withheld: work closed",
			"Withheld: over the volume limit",
			"Withheld: developer opted out",
			"Withheld: result marked incorrect",
			"Withheld: near duplicate",
		]);
	});
});

describe("what the chain lost", () => {
	it("counts a not-met result with no feedback of its own review, and nothing met", () => {
		expect(silentNotMet(lineageStateCase("not-met-silent").lineage)).toHaveLength(1);
		expect(silentNotMet(lineageStateCase("later-review-wrote").lineage)).toHaveLength(1);
	});

	it("counts only the feedback the review itself wrote", () => {
		const counts = stageCounts(wholeReview);
		const own = wholeReview.feedback.filter((entry) => entry.agentJobId === wholeReview.review?.id);
		expect(own.length).toBeLessThan(wholeReview.feedback.length);
		expect(counts.feedback).toBe(own.length);
	});

	it("keeps omitted chain records separate from the selected review's listed records", () => {
		const lineage = { ...wholeReview, moreResults: 9, moreFeedback: 7 };
		const counts = stageCounts(lineage);
		expect(counts.feedback).toBe(
			wholeReview.feedback.filter((entry) => entry.agentJobId === wholeReview.review?.id).length,
		);
		expect(counts.results).toBe(
			wholeReview.results.filter((entry) => entry.agentJobId === wholeReview.review?.id).length,
		);
	});

	it("counts an answer apart from work that resolved it", () => {
		expect(stageCounts(lineageStateCase("responses").lineage)).toStrictEqual({
			results: 3,
			feedback: 8,
			reached: 7,
			answered: 5,
			resolvedByWork: 1,
		});
	});
});
