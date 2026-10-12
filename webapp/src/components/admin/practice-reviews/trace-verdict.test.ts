import { describe, expect, it } from "vitest";

import type { ReviewLineage, TracedSignal } from "@/api/types.gen";

import {
	LINEAGE_FB,
	LINEAGE_OBS,
	lineageDisputedAndResolved,
	lineageObservationFocus,
	wholeReview,
} from "./lineage-fixtures";
import {
	LINEAGE_STATE_CASES,
	type LineageStateGroup,
	lineageStateCase,
	STATE_FEEDBACK_ID,
	STATE_OBSERVATION_ID,
} from "./lineage-state-fixtures";
import type { TraceNode } from "./review-trace";
import { traceVerdict, unreviewedVerdict } from "./trace-verdict";

const REVIEW: TraceNode = { kind: "review" };
const feedbackNode = (id: string): TraceNode => ({ kind: "feedback", id });
const observationNode = (id: string): TraceNode => ({ kind: "observation", id });

function verdictOf(key: string, node: TraceNode = REVIEW) {
	const { lineage, reviewOutcome } = lineageStateCase(key);
	return traceVerdict(lineage, node, reviewOutcome);
}

/** Every record a case's chain lists, so every verdict the story can draw is read. */
function nodesOf(lineage: ReviewLineage): TraceNode[] {
	return [
		REVIEW,
		{ kind: "work" },
		...lineage.observations.map((entry) => observationNode(entry.id)),
		...lineage.feedback.map((entry) => feedbackNode(entry.id)),
	];
}

describe("traceVerdict on every state", () => {
	const cases = Object.entries(LINEAGE_STATE_CASES).flatMap(([group, entries]) =>
		entries.map((entry) => [group, entry.key, entry] as const),
	);

	it.each(cases)("%s / %s says one sentence and ends the path", (_group, _key, entry) => {
		for (const node of nodesOf(entry.lineage)) {
			const { sentence, end } = traceVerdict(entry.lineage, node, entry.reviewOutcome);
			expect(sentence).toMatch(/^[A-Z].*\.$/u);
			// One sentence: no full stop before the last one.
			expect(sentence.slice(0, -1)).not.toMatch(/\.\s/u);
			expect(end.reason.length).toBeGreaterThan(0);
		}
	});

	it("says the same for the same chain, with no clock and no model", () => {
		const { lineage } = lineageStateCase("responses");
		expect(traceVerdict(lineage, REVIEW)).toStrictEqual(traceVerdict(lineage, REVIEW));
	});
});

/** One check per state group, with the sentence the decision named where it named one. */
describe("traceVerdict by state group", () => {
	const checks: Record<LineageStateGroup, () => void> = {
		review: () => {
			expect(verdictOf("queued").sentence).toBe(
				"This review waits in the queue and has observed nothing yet.",
			);
			expect(verdictOf("on-hold").sentence).toBe(
				"This review is on hold (over the AI budget) and has observed nothing yet.",
			);
			expect(verdictOf("running").end).toMatchObject({ kind: "open", label: "Open" });
			expect(verdictOf("completed").sentence).toBe(
				"This review ran 9 min after a 10 s wait and composed 1 piece of feedback; it reached the developer.",
			);
			expect(verdictOf("failed").sentence).toBe(
				"This review failed after 9 min and recorded 1 observation, but composed no feedback.",
			);
			expect(verdictOf("timed-out").end.kind).toBe("lost");
			expect(verdictOf("cancelled").sentence).toBe(
				"This review was canceled after 50 s with nothing recorded.",
			);
			expect(verdictOf("insufficient-evidence").sentence).toBe(
				"This review assessed nothing: the evidence it needed was missing or unreadable.",
			);
			expect(verdictOf("coalesced").sentence).toContain("it was coalesced with an earlier review");
			expect(verdictOf("record-gone").end.label).toBe("Not known");
		},
		trigger: () => {
			// What started the review is the rail's first step, not the verdict's subject.
			expect(verdictOf("trigger-merged").sentence).toBe(verdictOf("trigger-ready").sentence);
		},
		merge: () => {
			expect(
				verdictOf("merged-during", feedbackNode(lastFeedbackId("merged-during"))).sentence,
			).toBe(
				"This feedback did not reach the developer: the work was merged 7 min before it was composed.",
			);
			expect(verdictOf("started-by-merge", feedbackNode(STATE_FEEDBACK_ID)).sentence).toBe(
				"This feedback did not reach the developer, by design: the merge started this review, so the work was already merged.",
			);
			expect(verdictOf("merged-before", feedbackNode(STATE_FEEDBACK_ID)).end).toMatchObject({
				kind: "lost",
				reason: "withheld (already merged)",
			});
		},
		observations: () => {
			expect(verdictOf("not-met-silent", observationNode(STATE_OBSERVATION_ID)).sentence).toBe(
				"This observation was not met but led to no feedback, so the developer was not told.",
			);
			expect(verdictOf("marked-incorrect", observationNode(STATE_OBSERVATION_ID)).sentence).toBe(
				"This observation was marked incorrect and led to 2 pieces of feedback; one was delivered and has no answer yet, and one was withheld (observation marked incorrect).",
			);
			expect(verdictOf("later-review-wrote", observationNode(STATE_OBSERVATION_ID)).end.label).toBe(
				"Elsewhere",
			);
			expect(verdictOf("empty").sentence).toBe(
				"This review ran 9 min after a 10 s wait and recorded no observation.",
			);
		},
		location: () => {
			const { lineage } = lineageStateCase("locations");
			const slack = lineage.feedback.find((entry) => entry.location === "SLACK");
			expect(slack).toBeDefined();
			expect(verdictOf("locations", feedbackNode(slack?.id ?? "")).sentence).toBe(
				"This feedback reached the developer with Heph in Slack 20 s after it was composed and has no answer yet.",
			);
		},
		delivery: () => {
			expect(verdictOf("shown-then-withheld", feedbackNode(STATE_FEEDBACK_ID)).sentence).toBe(
				"This feedback reached the developer on their practice pages, then was withheld: the author, repository, or base branch is no longer covered by practice review.",
			);
			// Ten different ends are counted by kind, not listed one by one.
			expect(verdictOf("delivery-states", observationNode(STATE_OBSERVATION_ID)).sentence).toBe(
				"This observation led to 10 pieces of feedback; four are still open, five did not arrive, and one has no known end.",
			);
		},
		withholding: () => {
			const { lineage } = lineageStateCase("withholding-families");
			const optedOut = lineage.feedback.find(
				(entry) => entry.suppressionReason === "RECIPIENT_OPTED_OUT",
			);
			expect(verdictOf("withholding-families", feedbackNode(optedOut?.id ?? "")).sentence).toBe(
				"This feedback did not reach the developer: the developer has opted out of AI feedback.",
			);
			expect(verdictOf("withholding-families").end).toMatchObject({ kind: "lost" });
		},
		decision: () => {
			const { lineage } = lineageStateCase("decisions");
			const [awaiting, , rejected, withdrawn] = lineage.feedback;
			expect(verdictOf("decisions", feedbackNode(awaiting?.id ?? "")).end).toMatchObject({
				kind: "open",
				reason: "it awaits an admin’s approval",
			});
			expect(verdictOf("decisions", feedbackNode(rejected?.id ?? "")).sentence).toBe(
				"This feedback did not reach the developer: an admin rejected it.",
			);
			expect(verdictOf("decisions", feedbackNode(withdrawn?.id ?? "")).sentence).toBe(
				"This feedback reached the developer on their practice pages, then an admin withdrew it.",
			);
		},
		response: () => {
			const { lineage } = lineageStateCase("responses");
			const [answered, , disputed] = lineage.feedback;
			// An admin learns that the developer answered, never how they rated it.
			expect(verdictOf("responses", feedbackNode(answered?.id ?? ""))).toMatchObject({
				sentence:
					"This feedback reached the developer on GitHub 30 s after it was composed, and they answered it; their answer stays private to them.",
				end: { kind: "answered", reason: "the developer answered it; how stays private to them" },
			});
			expect(verdictOf("responses", feedbackNode(disputed?.id ?? "")).sentence).toBe(
				"This feedback reached the developer on GitHub 30 s after it was composed, but they disputed it.",
			);
			expect(verdictOf("responses").end.kind).toBe("worked");
		},
		volume: () => {
			expect(verdictOf("many-feedback").sentence).toBe(
				"This review ran 9 min after a 10 s wait and composed 10 pieces of feedback; all 10 reached the developer.",
			);
		},
	};

	it.each(Object.entries(checks))("%s", (_group, check) => {
		expect(check).not.toThrow();
	});
});

function lastFeedbackId(key: string): string {
	const { lineage } = lineageStateCase(key);
	return lineage.feedback.at(-1)?.id ?? "";
}

describe("traceVerdict on the review the stories tell", () => {
	it("tells the whole review with its wait, its run and how much reached the developer", () => {
		expect(traceVerdict(wholeReview, REVIEW).sentence).toBe(
			"This review ran 1 h 27 min after a 20 s wait and composed 5 pieces of feedback; 3 reached the developer.",
		);
	});

	it("says a review a newer one replaced assessed nothing, rather than that it found nothing", () => {
		const { sentence, end } = traceVerdict(wholeReview, REVIEW, "SUPERSEDED");
		expect(sentence).toBe(
			"This review assessed nothing: a newer review of the same work replaced it while it waited, and that review carries its practices.",
		);
		expect(end.kind).toBe("none");
	});

	it("tells an observation by what became of each piece written from it", () => {
		expect(
			traceVerdict(lineageObservationFocus, observationNode(LINEAGE_OBS.cache404)).sentence,
		).toBe("This observation led to 1 piece of feedback; it was delivered and answered.");
		const { sentence, end } = traceVerdict(
			lineageDisputedAndResolved,
			observationNode(LINEAGE_OBS.cache404),
		);
		expect(sentence).toBe(
			"This observation led to 2 pieces of feedback; one was delivered and disputed, and one was delivered and resolved by the work.",
		);
		// The best end of any piece names the path.
		expect(end.kind).toBe("worked");
	});

	it("says when the work was merged against when a withheld piece was written", () => {
		expect(traceVerdict(wholeReview, feedbackNode(LINEAGE_FB.afterMerge)).sentence).toBe(
			"This feedback did not reach the developer: the work was merged 7 min before it was composed.",
		);
	});

	it("keeps a delivered piece open since it was delivered", () => {
		const { end } = traceVerdict(wholeReview, feedbackNode(LINEAGE_FB.praise));
		expect(end).toMatchObject({ kind: "open", reason: "delivered, no answer yet" });
		expect(end.since?.toISOString()).toBe("2026-07-28T13:43:00.000Z");
	});

	it("falls back to the review for a record the chain does not list", () => {
		expect(traceVerdict(wholeReview, feedbackNode("missing"))).toStrictEqual(
			traceVerdict(wholeReview, REVIEW),
		);
	});
});

describe("traceVerdict on a prepared piece, by its channel", () => {
	const pieceId = lineageObservationFocus.feedback[0]?.id ?? "";
	const prepared = (channel: "IN_CONTEXT" | "IN_APP" | "IN_CHAT"): ReviewLineage => ({
		...lineageObservationFocus,
		feedback: lineageObservationFocus.feedback.map((entry) =>
			entry.id === pieceId
				? {
						...entry,
						channel,
						deliveryState: "PREPARED",
						deliveredAt: undefined,
						response: undefined,
					}
				: entry,
		),
	});

	it.each([
		["IN_CONTEXT", "it is prepared to be posted on the work", "prepared to be posted on the work"],
		["IN_APP", "it is prepared for their practice pages", "prepared for their practice pages"],
		[
			"IN_CHAT",
			"it is prepared for their next conversation with Heph",
			"prepared for a conversation",
		],
	] as const)("names where a %s piece goes", (channel, sentence, clause) => {
		const lineage = prepared(channel);
		expect(traceVerdict(lineage, feedbackNode(pieceId)).sentence).toBe(
			`This feedback has not reached the developer yet: ${sentence}.`,
		);
		expect(traceVerdict(lineage, observationNode(LINEAGE_OBS.cache404)).sentence).toContain(clause);
	});
});

it("never says a prepared piece on the work or the practice pages goes to a conversation", () => {
	for (const channel of ["IN_CONTEXT", "IN_APP"] as const) {
		const lineage: ReviewLineage = {
			...lineageObservationFocus,
			feedback: lineageObservationFocus.feedback.map((entry) => ({
				...entry,
				channel,
				deliveryState: "PREPARED",
				deliveredAt: undefined,
				response: undefined,
			})),
		};
		for (const node of nodesOf(lineage)) {
			expect(traceVerdict(lineage, node).sentence).not.toMatch(/conversation/u);
		}
	}
});

/** A moment of work no review started on, as the recorded work names its latest. */
function moment(overrides: Partial<TracedSignal>): TracedSignal {
	return {
		discoveredVia: "EVENT",
		displayName: "Marked ready for review",
		id: "signal-1",
		occurredAt: new Date("2026-10-08T08:00:00Z"),
		revision: "abc",
		signal: "READY",
		state: "SUPPRESSED",
		...overrides,
	};
}

describe("unreviewedVerdict", () => {
	const off = {
		stateReason: "PRACTICES_DISABLED",
		stateReasonDescription:
			"Practice reviews are turned off for this workspace. Hephaestus tries again when they are turned on.",
	} as const;

	it("says a queued moment waits, not that reviews are off, while they are on", () => {
		const verdict = unreviewedVerdict(moment({ state: "PENDING", ...off }), true);
		expect(verdict.sentence).toBe(
			"No review has started on this work yet: it is queued for review.",
		);
		expect(verdict.end).toMatchObject({ kind: "open", label: "Queued for review" });
		expect(verdict.end.reason).not.toMatch(/turned off/u);
	});

	it("says why a queued moment waits where the reason still holds", () => {
		const verdict = unreviewedVerdict(moment({ state: "PENDING", ...off }), false);
		expect(verdict.sentence).toBe(
			"No review has started on this work yet: it is queued for review.",
		);
		expect(verdict.end.reason).toMatch(/^Practice reviews are turned off/u);
	});

	it("says reviews were off for a refused moment once they are on again", () => {
		const verdict = unreviewedVerdict(moment(off), true);
		expect(verdict.sentence).toBe(
			"No review started on this work: practice reviews were turned off for this workspace when it happened.",
		);
		expect(verdict.end.kind).toBe("lost");
	});

	it("says reviews are off while they are off", () => {
		expect(unreviewedVerdict(moment(off), false).sentence).toBe(
			"No review started on this work: practice reviews are turned off for this workspace.",
		);
	});

	it("says a deferred moment waits to see if the work keeps changing", () => {
		expect(unreviewedVerdict(moment({ state: "DEFERRED" }), true).sentence).toBe(
			"No review has started on this work yet: Hephaestus waits to see if it keeps changing.",
		);
	});
});
