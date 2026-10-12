import { CopyCheckIcon, FileQuestionIcon } from "lucide-react";

import type {
	AgentJob,
	ReviewLineage,
	ReviewLineageFeedback,
	ReviewLineageMoment,
	ReviewLineageResult,
} from "@/api/types.gen";
import type { StatusDef } from "@/components/common/status-def";
import { REVIEW_RUN_MARKER_DEFS } from "@/components/practice-vocabulary/review-run-marker-defs";
import {
	REPLACED_REVIEW_STATUS,
	REVIEW_STATUS_DEFS,
} from "@/components/practice-vocabulary/review-status-defs";

import { holdReasonCopy } from "./job-utils";
import { type NumberedResult, numberedResults } from "./lineage-chain";
import { reviewMoments, type TraceNode } from "./review-trace";

/**
 * How the review ran, in the words of its step: "Review completed", "Review on hold". The page knows
 * the job and says more than the chain's three states can: a hold, a timeout, a cancellation, and a
 * run that assessed nothing because the evidence was missing or an earlier review had answered.
 */
export interface ReviewStepState {
	def: StatusDef;
	/** "Review completed". */
	title: string;
	/** One line under it, where the state needs one: why it waits, why it assessed nothing. */
	note?: string;
	/** The review did not get as far as observing, so an empty result step says why. */
	assessedNothing?: string;
}

const NOTHING_ASSESSED: StatusDef = {
	label: "Nothing assessed",
	icon: FileQuestionIcon,
	badgeVariant: "warning",
	description: "No model ran, because the evidence it needed was missing or unreadable.",
};

const ANSWERED_EARLIER: StatusDef = {
	label: "Answered by an earlier review",
	icon: CopyCheckIcon,
	badgeVariant: "outline",
	description:
		"No model ran, because an earlier review had answered every practice on the same code.",
};

export function reviewStepState(
	review: NonNullable<ReviewLineage["review"]>,
	outcome: AgentJob["reviewOutcome"] | undefined,
): ReviewStepState {
	const { phase, holdReason } = review;
	if (phase === "QUEUED" && holdReason !== undefined && holdReason.length > 0) {
		return {
			def: REVIEW_RUN_MARKER_DEFS.ON_HOLD,
			title: "Review on hold",
			note: holdReasonCopy(holdReason).label,
			assessedNothing: "The review waits on its hold, so it has observed nothing yet.",
		};
	}
	if (phase === "COMPLETED" && outcome === "INSUFFICIENT_EVIDENCE") {
		return {
			def: NOTHING_ASSESSED,
			title: "Review completed",
			note: "Nothing assessed: the evidence it needed was missing or unreadable",
			assessedNothing:
				"No practice was judged. This is not a review that looked and found nothing.",
		};
	}
	if (phase === "COMPLETED" && outcome === "COALESCED") {
		return {
			def: ANSWERED_EARLIER,
			title: "Review completed",
			note: "An earlier review had answered every practice on the same code",
			assessedNothing: "This review assessed nothing anew. The earlier review holds the answers.",
		};
	}
	if (phase === "COMPLETED" && outcome === "SUPERSEDED") {
		return {
			def: REPLACED_REVIEW_STATUS,
			title: "Review replaced",
			note: "A newer review of the same work replaced it while it waited",
			assessedNothing: "This review assessed nothing. The newer review carries its practices.",
		};
	}
	const def = REVIEW_STATUS_DEFS[phase];
	const assessedNothing: Partial<Record<AgentJob["status"], string>> = {
		QUEUED: "The review has not started, so it has observed nothing yet.",
		RUNNING: "Results appear as the review reaches each practice.",
		COMPLETED: "The review ran to the end and recorded no result.",
		FAILED: "The review stopped before it recorded a result.",
		TIMED_OUT: "The review ran out of time before it recorded a result.",
		CANCELLED: "The review was canceled before it recorded a result.",
	};
	return {
		def,
		title: `Review ${def.label.toLowerCase()}`,
		assessedNothing: assessedNothing[phase],
	};
}

/** The four stages the rail reads in, so no step is anonymous. */
export type TraceStage = "trigger" | "review" | "feedback" | "effect";

export const TRACE_STAGES: readonly TraceStage[] = ["trigger", "review", "feedback", "effect"];

export const TRACE_STAGE_LABELS: Record<TraceStage, string> = {
	trigger: "Trigger",
	review: "Review",
	feedback: "Feedback",
	effect: "Effect",
};

/**
 * One step on the rail. The work heads the rail and has no time. The moment that started the
 * review is the start; what happened before it folds into one "Before this review" step. The
 * review, a later moment folded into it and its results are the review stage. The feedback,
 * the merge where it fell after the start and the line of siblings are the feedback stage. The end
 * of the record's path is the effect.
 */
export type RailItem =
	| { kind: "moment"; key: string; at: Date; moment: ReviewLineageMoment }
	| { kind: "review"; key: "review"; at: Date }
	| { kind: "results"; key: "results"; at: Date | undefined }
	| { kind: "feedback"; key: string; at: Date; feedback: ReviewLineageFeedback }
	| { kind: "before"; key: "before"; at: undefined; items: RailItem[] }
	| {
			kind: "siblings";
			key: "siblings";
			at: undefined;
			results: number;
			feedback: number;
	  }
	| { kind: "end"; key: "end"; at: undefined };

export interface RailStage {
	stage: TraceStage;
	items: RailItem[];
}

const RANK: Record<RailItem["kind"], number> = {
	before: -1,
	moment: 0,
	review: 1,
	results: 2,
	feedback: 3,
	siblings: 4,
	end: 5,
};

/**
 * When this review's story starts: the moment on the work that started it, or the review itself
 * where no moment names it.
 */
export function traceStart(lineage: ReviewLineage): Date | undefined {
	for (const entry of reviewMoments(lineage)) {
		if (entry.kind === "moment" && entry.moment.startedReview) {
			return entry.moment.occurredAt;
		}
	}
	return lineage.review?.createdAt;
}

/** The siblings the simple story folds into one line. */
export interface StorySiblings {
	results: number;
	feedback: number;
}

/**
 * The rail's steps by stage, each stage in time order. `feedback` is what the story tells in full;
 * `siblings`, where any, is the one line that counts the rest.
 */
export function storyRail(
	lineage: ReviewLineage,
	feedback: readonly ReviewLineageFeedback[],
	siblings?: StorySiblings,
): RailStage[] {
	const start = traceStart(lineage)?.getTime();
	const before: RailItem[] = [];
	const staged: Record<TraceStage, RailItem[]> = {
		trigger: [],
		review: [],
		feedback: [],
		effect: [],
	};
	const earlier = (at: Date) => start !== undefined && at.getTime() < start;
	// The rail reads in time order across its stages: a moment folded into the review after its
	// first piece of feedback was written sits among the feedback, so the times never run back.
	const firstFeedback = earliest(
		feedback.map((entry) => entry.createdAt).filter((at) => !earlier(at)),
	)?.getTime();
	const inReviewStage = (at: Date) => firstFeedback === undefined || at.getTime() <= firstFeedback;

	for (const entry of reviewMoments(lineage)) {
		if (entry.kind !== "moment") {
			if (entry.kind === "review-started") {
				staged.review.push({ kind: "review", key: "review", at: entry.at });
			}
			continue;
		}
		const { moment } = entry;
		const item: RailItem = {
			kind: "moment",
			key: `moment:${moment.kind}:${moment.occurredAt.toISOString()}`,
			at: moment.occurredAt,
			moment,
		};
		if (moment.startedReview) {
			staged.trigger.push(item);
		} else if (earlier(moment.occurredAt)) {
			before.push(item);
		} else if (moment.joinedReview && inReviewStage(moment.occurredAt)) {
			staged.review.push(item);
		} else {
			staged.feedback.push(item);
		}
	}
	if (lineage.review === undefined) {
		// No review to stand on: its step says the record is gone.
		staged.review.push({ kind: "review", key: "review", at: new Date(0) });
	}
	const reviewId = lineage.review?.id;
	const own = lineage.results.filter(
		(entry) => reviewId === undefined || entry.agentJobId === reviewId,
	);
	staged.review.push({
		kind: "results",
		key: "results",
		at: earliest(own.map((entry) => entry.observedAt)),
	});
	for (const entry of feedback) {
		const item: RailItem = {
			kind: "feedback",
			key: `feedback:${entry.id}`,
			at: entry.createdAt,
			feedback: entry,
		};
		(earlier(entry.createdAt) ? before : staged.feedback).push(item);
	}
	if (siblings !== undefined && siblings.results + siblings.feedback > 0) {
		staged.feedback.push({ kind: "siblings", key: "siblings", at: undefined, ...siblings });
	}
	staged.effect.push({ kind: "end", key: "end", at: undefined });
	if (before.length > 0) {
		staged.trigger.unshift({
			kind: "before",
			key: "before",
			at: undefined,
			items: inTimeOrder(before),
		});
	}
	return TRACE_STAGES.map((stage) => ({
		stage,
		items: stage === "trigger" ? staged.trigger : inTimeOrder(staged[stage]),
	}));
}

/** A step with no time of its own keeps its place by kind: results first, the siblings last. */
function inTimeOrder(items: readonly RailItem[]): RailItem[] {
	return [...items].sort((a, b) => {
		const at = a.at?.getTime();
		const bt = b.at?.getTime();
		if (at !== undefined && bt !== undefined && at !== bt) {
			return at - bt;
		}
		return RANK[a.kind] - RANK[b.kind];
	});
}

function earliest(times: readonly Date[]): Date | undefined {
	let first: Date | undefined;
	for (const at of times) {
		if (first === undefined || at.getTime() < first.getTime()) {
			first = at;
		}
	}
	return first;
}

/**
 * Who wrote a piece the chain lists, where it is not the chain's own review. Feedback composed before
 * the review started is an earlier review's; any other is a later one's.
 */
export function writtenBy(
	feedback: Pick<ReviewLineageFeedback, "agentJobId" | "createdAt">,
	review: ReviewLineage["review"],
): "an earlier review" | "a later review" | undefined {
	if (review === undefined || feedback.agentJobId === review.id) {
		return undefined;
	}
	return feedback.createdAt.getTime() < review.createdAt.getTime()
		? "an earlier review"
		: "a later review";
}

/** A result another review recorded: one this review did not ask again, since that one had answered. */
export function answeredEarlier(
	result: Pick<ReviewLineageResult, "agentJobId">,
	review: ReviewLineage["review"],
): boolean {
	return review !== undefined && result.agentJobId !== review.id;
}

/**
 * The story around the record the reader came from. A review or the work tells the whole review. An
 * result tells the feedback written from it, and a piece of feedback tells itself and the
 * pieces it replaced or that replaced it; every other piece is a sibling, behind one line.
 */
export interface StoryFocus {
	/** The pieces the story tells in full, in time order. */
	shown: ReviewLineageFeedback[];
	/** The rest of the review's pieces, behind "N other pieces of feedback". */
	siblings: ReviewLineageFeedback[];
}

export function storyFeedback(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	expanded: boolean,
): StoryFocus {
	const byTime = [...lineage.feedback].sort(
		(a, b) => a.createdAt.getTime() - b.createdAt.getTime(),
	);
	if (expanded || here === undefined || here.kind === "work" || here.kind === "review") {
		return { shown: byTime, siblings: [] };
	}
	if (here.kind === "results" || here.kind === "end" || here.kind === "moment") {
		return { shown: byTime, siblings: [] };
	}
	const told = new Set<string>();
	if (here.kind === "result") {
		for (const entry of byTime) {
			if (entry.practiceResultIds.includes(here.id)) {
				told.add(entry.id);
			}
		}
	} else {
		const self = byTime.find((entry) => entry.id === here.id);
		if (self !== undefined) {
			told.add(self.id);
			for (const id of [self.replacesId, self.replacedById]) {
				if (id !== undefined) {
					told.add(id);
				}
			}
		}
	}
	return {
		shown: byTime.filter((entry) => told.has(entry.id)),
		siblings: byTime.filter((entry) => !told.has(entry.id)),
	};
}

/**
 * The results numbered as everywhere on the chain, with the one the reader came from apart.
 * The numbers come from the whole chain, `full`, so a filter hides results but never renumbers
 * them.
 */
export function storyResults(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	full: ReviewLineage = lineage,
) {
	const shown = new Set(lineage.results.map((entry) => entry.id));
	const numbered = numberedResults(full.results).filter((entry) => shown.has(entry.result.id));
	const self =
		here?.kind === "result" ? numbered.find((entry) => entry.result.id === here.id) : undefined;
	return { numbered, self };
}

/** The feedback letter, "A" to "Z" and then "AA", in the order the pieces were written. */
export function feedbackLetters(feedback: readonly ReviewLineageFeedback[]): Map<string, string> {
	const byTime = [...feedback].sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());
	return new Map(byTime.map((entry, index) => [entry.id, letterAt(index)]));
}

function letterAt(index: number): string {
	const letter = String.fromCodePoint(65 + (index % 26));
	return index < 26 ? letter : `${letterAt(Math.floor(index / 26) - 1)}${letter}`;
}

/** The results a piece of feedback rests on that the chain lists, by number. */
export function sourcesOf(
	feedback: Pick<ReviewLineageFeedback, "practiceResultIds">,
	numbered: readonly NumberedResult[],
): NumberedResult[] {
	return numbered
		.filter((entry) => feedback.practiceResultIds.includes(entry.result.id))
		.sort((a, b) => a.number - b.number);
}
