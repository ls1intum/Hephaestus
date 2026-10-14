import type { ItemEvent, ReviewDetail, ReviewRef } from "@/api/types.gen";
import type { StatusDef } from "@/components/common/status-def";
import {
	REVIEW_STATUS_DEFS,
	REVIEW_STOP_REASON_SENTENCES,
	type ReviewStatus,
} from "@/components/practice-vocabulary/review-status-defs";

import {
	countOf,
	type LineageFeedback,
	type LineageRecords,
	type LineageResult,
	type NumberedResult,
	numberedResults,
} from "./lineage-chain";
import { chainReview, ownResults, type TraceNode } from "./review-trace";

/**
 * How the review ran, in the words of its step: "Review complete", "Review failed". The review's
 * own read adds what the graph does not hold: why it stopped and which practices it withheld.
 */
export interface ReviewStepState {
	def: StatusDef;
	/** "Review complete". */
	title: string;
	/** One line under it, where the review recorded why it stopped. */
	note?: string;
	/** Why an empty result step is empty. */
	assessedNothing: string;
}

const ASSESSED_NOTHING: Record<ReviewStatus, string> = {
	QUEUED: "The review has not started, so it has recorded nothing yet.",
	RUNNING: "Results appear as the review reaches each practice.",
	COMPLETE: "The review ran to the end and recorded no result.",
	PARTIAL: "The review closed before it recorded a result.",
	FAILED: "The review stopped before it recorded a result.",
	CANCELLED: "The review was cancelled before it recorded a result.",
};

export function reviewStepState(
	review: ReviewRef,
	detail?: Pick<ReviewDetail, "stopReason" | "practices">,
): ReviewStepState {
	const def = REVIEW_STATUS_DEFS[review.status];
	const stopReason = detail?.stopReason ?? null;
	const withheld =
		detail?.practices.filter((practice) => practice.status === "WITHHELD").length ?? 0;
	return {
		def,
		title: `Review ${def.label.toLowerCase()}`,
		note: stopReason === null ? undefined : REVIEW_STOP_REASON_SENTENCES[stopReason],
		// A withheld practice records its reason and no result, so an empty step says that and
		// never reads as a review that looked and saw nothing.
		assessedNothing:
			withheld > 0
				? `${countOf(withheld, "practice was", "practices were")} withheld with a recorded reason, so no result stands for ${withheld === 1 ? "it" : "them"}.`
				: ASSESSED_NOTHING[review.status],
	};
}

/** The four stages the rail reads in, so no step is anonymous. */
export type TraceStage = "start" | "review" | "feedback" | "effect";

export const TRACE_STAGE_LABELS: Record<TraceStage, string> = {
	start: "Started by",
	review: "Review",
	feedback: "Feedback",
	effect: "Effect",
};

/**
 * One step on the rail. What started the review heads it: the event on the work, or the review's
 * origin where no event did. The review and its results are the review stage. The feedback and the
 * line of siblings are the feedback stage. The end of the record's path is the effect.
 */
export type RailItem =
	| {
			kind: "start";
			key: "start";
			at: Date;
			origin: ReviewRef["origin"];
			event: ItemEvent | undefined;
	  }
	| { kind: "review"; key: "review"; at: Date | undefined }
	| { kind: "results"; key: "results"; at: Date | undefined }
	| { kind: "feedback"; key: string; at: Date | undefined; feedback: LineageFeedback }
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

/** The siblings the simple story folds into one line. */
export interface StorySiblings {
	results: number;
	feedback: number;
}

export interface StoryRailOptions {
	/** The event that started the review, from the review's own read. */
	startedBy?: ItemEvent | null;
	siblings?: StorySiblings;
}

/**
 * The rail's steps by stage. `feedback` is what the story tells in full, in the order it is given;
 * `siblings`, where any, is the one line that counts the rest. The graph records no time a piece of
 * feedback was written, so a piece steps at its delivery or at no time.
 */
export function storyRail(
	records: LineageRecords,
	feedback: readonly LineageFeedback[],
	{ startedBy, siblings }: StoryRailOptions = {},
): RailStage[] {
	const review = chainReview(records);
	const start: RailItem[] =
		review === undefined
			? []
			: [
					{
						kind: "start",
						key: "start",
						at: startedBy?.occurredAt ?? review.createdAt,
						origin: review.origin,
						event: startedBy ?? undefined,
					},
				];
	const reviewStage: RailItem[] = [
		// No review to stand on: its step says the record is gone.
		{ kind: "review", key: "review", at: review?.createdAt },
		{
			kind: "results",
			key: "results",
			at: earliest(ownResults(records).map((entry) => entry.recordedAt)),
		},
	];
	const feedbackStage: RailItem[] = feedback.map((entry) => ({
		kind: "feedback",
		key: `feedback:${entry.id}`,
		at: entry.deliveredAt ?? undefined,
		feedback: entry,
	}));
	if (siblings !== undefined && siblings.results + siblings.feedback > 0) {
		feedbackStage.push({ kind: "siblings", key: "siblings", at: undefined, ...siblings });
	}
	return [
		{ stage: "start", items: start },
		{ stage: "review", items: reviewStage },
		{ stage: "feedback", items: feedbackStage },
		{ stage: "effect", items: [{ kind: "end", key: "end", at: undefined }] },
	];
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

/** A result another review recorded: one this review did not ask again, since that one had answered. */
export function answeredEarlier(
	result: Pick<LineageResult, "reviewId">,
	review: ReviewRef | undefined,
): boolean {
	return review !== undefined && result.reviewId !== review.id;
}

/**
 * The story around the record the reader came from. A review or the work tells the whole review. A
 * result tells the feedback written from it, and a piece of feedback tells itself; every other piece
 * is a sibling, behind one line.
 */
export interface StoryFocus {
	/** The pieces the story tells in full, by letter. */
	shown: LineageFeedback[];
	/** The rest of the review's pieces, behind "N other pieces of feedback". */
	siblings: LineageFeedback[];
}

export function storyFeedback(
	records: Pick<LineageRecords, "feedback">,
	here: TraceNode | undefined,
	expanded: boolean,
): StoryFocus {
	const byLetter = [...records.feedback].sort(letterOrder);
	if (expanded || (here?.kind !== "result" && here?.kind !== "feedback")) {
		return { shown: byLetter, siblings: [] };
	}
	const told = (entry: LineageFeedback) =>
		here.kind === "result" ? entry.basis.includes(here.id) : entry.id === here.id;
	return {
		shown: byLetter.filter(told),
		siblings: byLetter.filter((entry) => !told(entry)),
	};
}

/**
 * The results numbered as everywhere on the chain, with the one the reader came from apart.
 * The numbers come from the whole chain, `full`, so a filter hides results but never renumbers
 * them.
 */
export function storyResults(
	records: Pick<LineageRecords, "results">,
	here: TraceNode | undefined,
	full: Pick<LineageRecords, "results"> = records,
) {
	const shown = new Set(records.results.map((entry) => entry.id));
	const numbered = numberedResults(full.results).filter((entry) => shown.has(entry.result.id));
	const self =
		here?.kind === "result" ? numbered.find((entry) => entry.result.id === here.id) : undefined;
	return { numbered, self };
}

/**
 * The order of the feedback letters. The graph records no time a piece was written, so the order
 * rests on the id alone: a piece keeps its letter in every trace of its review.
 */
function letterOrder(a: LineageFeedback, b: LineageFeedback): number {
	return a.id.localeCompare(b.id, "en");
}

/** The feedback letter, "A" to "Z" and then "AA". */
export function feedbackLetters(feedback: readonly LineageFeedback[]): Map<string, string> {
	const byLetter = [...feedback].sort(letterOrder);
	return new Map(byLetter.map((entry, index) => [entry.id, letterAt(index)]));
}

function letterAt(index: number): string {
	const letter = String.fromCodePoint(65 + (index % 26));
	return index < 26 ? letter : `${letterAt(Math.floor(index / 26) - 1)}${letter}`;
}

/** The results a piece of feedback rests on that the chain lists, by number. */
export function sourcesOf(
	feedback: Pick<LineageFeedback, "basis">,
	numbered: readonly NumberedResult[],
): NumberedResult[] {
	return numbered
		.filter((entry) => feedback.basis.includes(entry.result.id))
		.sort((a, b) => a.number - b.number);
}
