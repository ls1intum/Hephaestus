import { assert, describe, expect, it } from "vitest";

import type { ReviewLineage, ReviewLineageFeedback } from "@/api/types.gen";
import { withholdingReasonSentence } from "@/components/practice-vocabulary/withholding-defs";

import { at } from "./lineage-fixtures";
import {
	LINEAGE_STATE_CASES,
	type LineageStateCase,
	lineageStateCase,
	STATE_FEEDBACK_ID,
} from "./lineage-state-fixtures";
import type { TraceNode } from "./review-trace";
import {
	bottleneckText,
	feedbackLoss,
	mergedAtOf,
	traceBottleneck,
	traceSpans,
} from "./trace-bottleneck";
import { traceVerdict } from "./trace-verdict";

type Reason = NonNullable<ReviewLineageFeedback["suppressionReason"]>;

const REVIEW: TraceNode = { kind: "review" };
const PIECE: TraceNode = { kind: "feedback", id: STATE_FEEDBACK_ID };
const NOW = at("18:00:00");

/** The merged case with the review finished a minute before the merge, so delivery came after. */
function finishedBeforeMerge() {
	const { lineage, job } = lineageStateCase("withheld-artifact-merged");
	const merged = mergedAtOf(lineage, job);
	if (merged === undefined || lineage.review === undefined) {
		throw new Error("The case carries a merge and a review");
	}
	const finishedFirst: ReviewLineage = {
		...lineage,
		review: { ...lineage.review, finishedAt: new Date(merged.getTime() - 60_000) },
	};
	return { finishedFirst, job };
}

function bottleneckOf(key: string, node: TraceNode = REVIEW) {
	const { lineage, job } = lineageStateCase(key);
	return traceBottleneck(lineage, node, job, NOW);
}

const REASON_CASES = LINEAGE_STATE_CASES.withholding.filter((entry) =>
	entry.key.startsWith("withheld-"),
);

/** The reason of a case's one piece. */
function reasonOf(entry: LineageStateCase): Reason {
	const reason = entry.lineage.feedback[0]?.suppressionReason;
	if (reason === undefined) {
		throw new Error(`${entry.key} has no withheld piece`);
	}
	return reason;
}

/** The first piece in a state, as a step of the trace. */
function pieceIn(key: string, state: ReviewLineageFeedback["deliveryState"]): TraceNode {
	const found = lineageStateCase(key).lineage.feedback.find(
		(entry) => entry.deliveryState === state,
	);
	if (found === undefined) {
		throw new Error(`${key} has no ${state} piece`);
	}
	return { kind: "feedback", id: found.id };
}

describe("traceBottleneck for withheld feedback", () => {
	it("has a case for every reason the API names", () => {
		expect(REASON_CASES).toHaveLength(24);
	});

	it.each(REASON_CASES.map((entry) => [entry.key, entry] as const))(
		"%s names the loss in a plain sentence",
		(_key, entry) => {
			const bottleneck = traceBottleneck(entry.lineage, PIECE, entry.job, NOW);
			expect(bottleneck.kind).toBe("lost");
			expect(bottleneck.stage).toBe("feedback");
			expect(bottleneck.title).toBe("Withheld");
			expect(bottleneck.text).toMatch(/^[A-Z].*\.$/u);
			// Prose, not a dash between clauses.
			expect(bottleneck.text).not.toMatch(/ [-–—] /u);
		},
	);

	it.each(
		REASON_CASES.filter((entry) => reasonOf(entry) !== "ARTIFACT_MERGED").map(
			(entry) => [entry.key, entry, withholdingReasonSentence(reasonOf(entry))] as const,
		),
	)("%s says the reason's own sentence", (_key, entry, sentence) => {
		expect(traceBottleneck(entry.lineage, PIECE, entry.job, NOW).text).toBe(sentence);
	});

	it("says where in the review's run the work merged", () => {
		const bottleneck = bottleneckOf("withheld-artifact-merged", PIECE);
		expect(bottleneck.text).toBe(
			"The work was merged 2 min before the comment was ready: it merged while the review ran.",
		);
		expect(bottleneck.short).toBe("merged while the review ran");
	});

	it("says the work merged after the review finished when it merged before delivery", () => {
		const { finishedFirst, job } = finishedBeforeMerge();
		const bottleneck = traceBottleneck(finishedFirst, PIECE, job, NOW);
		expect(bottleneck.short).toBe("merged after the review finished");
		expect(bottleneck.text).toMatch(/: it merged after the review finished\.$/u);
	});

	it("never repeats the verdict: where it already says the reason, only the few words stand", () => {
		const { lineage, job } = lineageStateCase("withheld-artifact-merged");
		const bottleneck = traceBottleneck(lineage, PIECE, job, NOW);
		const { sentence } = traceVerdict(lineage, PIECE);
		expect(sentence).toContain("merged");
		expect(bottleneckText(bottleneck, sentence)).toBe("merged while the review ran");

		const composer = lineageStateCase("withheld-composer-withheld");
		const held = traceBottleneck(composer.lineage, PIECE, composer.job, NOW);
		expect(bottleneckText(held, traceVerdict(composer.lineage, PIECE).sentence)).toBe(
			"held back by the review",
		);
	});

	it("tells the same merge time in the verdict when only the merge moment carries it", () => {
		const { lineage } = lineageStateCase("withheld-artifact-merged");
		// The case's own merge time, which only the moment now carries.
		const merged = at("13:39:30");
		expect(lineage.workMergedAt).toStrictEqual(merged);
		const fromMoment: ReviewLineage = {
			...lineage,
			workMergedAt: undefined,
			workMoments: [
				...lineage.workMoments,
				{ kind: "MERGED", occurredAt: merged, startedReview: false, joinedReview: false },
			],
		};
		expect(traceBottleneck(fromMoment, PIECE, undefined, NOW).short).toBe(
			"merged while the review ran",
		);
		expect(traceVerdict(fromMoment, PIECE).sentence).toBe(
			"This feedback did not reach the developer: the work was merged 2 min before it was composed.",
		);
	});

	it("counts the reasons when several pieces were lost", () => {
		const { lineage } = lineageStateCase("withholding-families");
		const bottleneck = traceBottleneck(lineage, REVIEW, undefined, NOW);
		expect(bottleneck.title).toBe("None arrived");
		expect(bottleneck.text).toMatch(/^5 pieces of feedback did not reach the developer: /u);
		expect(bottleneck.text).toContain("work closed (1)");
	});

	it("takes the tone of what the loss asks of an admin, as the overview splits withheld feedback", () => {
		// Timing cost it: the work moved on before the comment was ready.
		expect(bottleneckOf("withheld-artifact-merged", PIECE).tone).toBe("warning");
		expect(bottleneckOf("withheld-stale-rollout-revision", PIECE).tone).toBe("warning");
		// The review, the developer or an admin decided: nothing went wrong.
		expect(bottleneckOf("withheld-composer-withheld", PIECE).tone).toBe("secondary");
		expect(bottleneckOf("withheld-recipient-opted-out", PIECE).tone).toBe("secondary");
		expect(bottleneckOf("withheld-observation-invalidated", PIECE).tone).toBe("secondary");
		// Sending failed: red.
		const { lineage: states, job } = lineageStateCase("delivery-states");
		expect(traceBottleneck(states, pieceIn("delivery-states", "FAILED"), job, NOW).tone).toBe(
			"destructive",
		);
		// Several pieces lost take the strongest tone among them.
		const { lineage } = lineageStateCase("withholding-families");
		expect(traceBottleneck(lineage, REVIEW, undefined, NOW).tone).toBe("warning");
	});

	it("tells a partial delivery whose rest was withheld from one still on its way", () => {
		const bottleneck = bottleneckOf("partly-withheld", PIECE);
		expect(bottleneck.title).toBe("Partly withheld");
		expect(bottleneck.text).toBe(
			`Part of it was delivered, the rest was withheld. ${withholdingReasonSentence("ARTIFACT_DRAFT")}`,
		);
		expect(traceVerdict(lineageStateCase("partly-withheld").lineage, PIECE).end.kind).toBe("lost");
	});
});

describe("traceBottleneck for the review itself", () => {
	it("gives a failed review's error and its attempts", () => {
		const bottleneck = bottleneckOf("failed-with-error");
		expect(bottleneck).toMatchObject({ kind: "failed", stage: "review", title: "Review failed" });
		expect(bottleneck.text).toBe(
			"The model output did not match the review schema twice: observations[2].evidence[0].quote was not found in the diff. It was tried 2 times.",
		);
	});

	it("says a failed review without a known error plainly", () => {
		const { lineage } = lineageStateCase("failed");
		expect(traceBottleneck(lineage, REVIEW, undefined, NOW).text).toBe(
			"It stopped on an error; the error is not recorded here.",
		);
	});

	it("names why a review was canceled", () => {
		expect(bottleneckOf("cancelled-declined").text).toBe(
			"A developer on the work declined AI review, or has not made the choice the workspace requires.",
		);
	});

	it("names a hold and how long it has held", () => {
		const bottleneck = bottleneckOf("on-hold");
		expect(bottleneck).toMatchObject({ kind: "waiting", title: "On hold", span: "queue" });
	});

	it("says that not-met observations led to no feedback", () => {
		const bottleneck = bottleneckOf("not-met-silent");
		expect(bottleneck).toMatchObject({ kind: "lost", title: "Not told" });
		expect(bottleneck.text).toMatch(/not met, but the review composed no feedback/u);
	});
});

describe("traceBottleneck where nothing was lost", () => {
	it("names the queue where it was the longest wait", () => {
		const bottleneck = bottleneckOf("slow-queue", PIECE);
		expect(bottleneck).toMatchObject({ kind: "slow", title: "Slow queue", span: "queue" });
		expect(bottleneck.text).toBe("The review waited 2 h 10 min in the queue for a worker.");
	});

	it("says a card on the practice pages is not opened yet", () => {
		expect(bottleneckOf("in-app-unopened", PIECE)).toMatchObject({
			kind: "waiting",
			title: "Not opened yet",
			stage: "effect",
		});
	});

	it("says a brief waits for a conversation, and for how long", () => {
		const bottleneck = bottleneckOf("in-chat-waiting", PIECE);
		expect(bottleneck.title).toBe("Waiting for a conversation");
		expect(bottleneck.text).toMatch(/^Prepared 4 h 19 min ago/u);
	});

	it("says an admin's approval is awaited", () => {
		const { lineage } = lineageStateCase("decisions");
		const node = pieceIn("decisions", "AWAITING_APPROVAL");
		expect(traceBottleneck(lineage, node, undefined, NOW).title).toBe("Awaiting approval");
	});

	it("does not count feedback replaced after it reached the developer as lost", () => {
		const { lineage } = lineageStateCase("delivery-states");
		const node = pieceIn("delivery-states", "SUPERSEDED");
		expect(traceBottleneck(lineage, node, undefined, NOW).kind).not.toBe("lost");
	});

	it("says a prepared piece is not sent yet", () => {
		const { lineage } = lineageStateCase("delivery-states");
		const node = pieceIn("delivery-states", "PREPARED");
		expect(traceBottleneck(lineage, node, undefined, NOW)).toMatchObject({
			kind: "waiting",
			title: "Not sent yet",
		});
	});

	it("says nothing had to be sent where every practice was met", () => {
		const { lineage } = lineageStateCase("observations-no-feedback");
		expect(traceBottleneck(lineage, REVIEW, undefined, NOW).text).toBe(
			"Nothing on this path had to be sent.",
		);
	});

	it("finds no bottleneck on a quick delivered path", () => {
		// The ordinary case takes no colour.
		expect(bottleneckOf("completed", PIECE)).toMatchObject({
			kind: "none",
			title: "No bottleneck",
			tone: "secondary",
		});
	});
});

/** `minutes` after the trace's review was picked up. */
function afterPickUp(lineage: ReviewLineage, minutes: number): Date {
	return new Date((lineage.review?.startedAt?.getTime() ?? 0) + minutes * 60_000);
}

describe("traceSpans", () => {
	it("measures each stage from the record, in order, and marks a long wait", () => {
		const { lineage, job } = lineageStateCase("slow-queue");
		const spans = traceSpans(lineage, PIECE, job);
		expect(spans.map((entry) => [entry.kind, entry.text, entry.slow])).toStrictEqual([
			["asked", "10 s", false],
			["queue", "2 h 10 min", true],
			["run", "7 min", false],
			["delivery", "20 s", false],
			["read", "39 min", true],
		]);
	});

	it("splits the run into Preparation and Execution where the review recorded the moment", () => {
		const { lineage, job } = lineageStateCase("slow-queue");
		const spans = traceSpans(lineage, PIECE, {
			...job,
			executionStartedAt: afterPickUp(lineage, 2),
		});
		expect(spans.map((entry) => [entry.kind, entry.label, entry.text])).toStrictEqual([
			["asked", "Until asked", "10 s"],
			["queue", "Queue", "2 h 10 min"],
			["preparation", "Preparation", "2 min"],
			["execution", "Execution", "5 min"],
			["delivery", "To developer", "20 s"],
			["read", "First read", "39 min"],
		]);
		// A moment after the end is out of order: the run stays one span.
		expect(
			traceSpans(lineage, PIECE, { ...job, executionStartedAt: afterPickUp(lineage, 60) }).map(
				(entry) => entry.kind,
			),
		).toContain("run");
	});

	it("leaves out what the record cannot tell", () => {
		const { lineage } = lineageStateCase("queued");
		expect(traceSpans(lineage, REVIEW).map((entry) => entry.kind)).toStrictEqual(["asked"]);
	});
});

describe("mergedAtOf", () => {
	it("falls back to the review's record, then to the merge moment", () => {
		const { lineage } = lineageStateCase("completed");
		expect(mergedAtOf(lineage)).toBeUndefined();
		expect(mergedAtOf(lineage, { workMergedAt: at("14:00:00") })).toStrictEqual(at("14:00:00"));
		const withMoment = {
			...lineage,
			workMoments: [
				...lineage.workMoments,
				{
					kind: "MERGED" as const,
					occurredAt: at("14:10:00"),
					startedReview: false,
					joinedReview: false,
				},
			],
		};
		expect(mergedAtOf(withMoment)).toStrictEqual(at("14:10:00"));
	});
});

describe("feedbackLoss", () => {
	it("leaves a piece that stands or waits alone", () => {
		const { lineage } = lineageStateCase("completed");
		for (const piece of lineage.feedback) {
			expect(feedbackLoss(piece, lineage)).toBeUndefined();
		}
	});

	it("says a withdrawn piece was delivered only where a delivery is recorded", () => {
		const { lineage } = lineageStateCase("decisions");
		const withdrawn = lineage.feedback.find((entry) => entry.withdrawnAt !== undefined);
		assert(withdrawn?.deliveredAt !== undefined);
		expect(feedbackLoss(withdrawn, lineage)?.text).toBe(
			"An admin withdrew it after it was delivered.",
		);
		// A prepared card on the practice pages can be withdrawn before it was delivered.
		const prepared = { ...withdrawn, deliveryState: "PREPARED" as const, deliveredAt: undefined };
		expect(feedbackLoss(prepared, lineage)).toMatchObject({
			title: "Withdrawn",
			text: "An admin withdrew it before it reached the developer.",
		});
	});
});
