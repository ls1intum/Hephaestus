import type { ReviewLineage, ReviewLineageMoment } from "@/api/types.gen";
import { durationBetween } from "@/lib/dates";

import { countOf } from "./lineage-chain";

/**
 * One step of a trace: the work, a moment on it, the review, its results together, one
 * result, one piece of feedback, or the end of the path. The record a trace stands on draws its
 * path strong and the rest muted.
 */
export type TraceNode =
	| { kind: "work" }
	| { kind: "review" }
	| { kind: "results" }
	| { kind: "end" }
	| { kind: "moment"; id: string }
	| { kind: "result"; id: string }
	| { kind: "feedback"; id: string };

/** A step the address can name as `?focus=`: any step but the review, which a trace of a review stands on. */
export type TraceFocus = Exclude<TraceNode, { kind: "review" }>;

/**
 * A step as the address writes it, `?focus=`: `work`, `results`, `end`, `moment:<id>`,
 * `result:<id>`, `feedback:<id>`, and `review`.
 */
export function traceStepKey(node: TraceNode): string {
	return "id" in node ? `${node.kind}:${node.id}` : node.kind;
}

/** A moment on the work has no id of its own: its kind and its time name it. */
export function momentNode(moment: ReviewLineageMoment): TraceNode {
	return { kind: "moment", id: `${moment.kind}-${moment.occurredAt.getTime()}` };
}

/** `?focus=` read back; undefined for a value the page cannot centre on. */
export function parseTraceFocus(raw: string | undefined): TraceFocus | undefined {
	if (raw === "work" || raw === "results" || raw === "end") {
		return { kind: raw };
	}
	const separator = raw?.indexOf(":") ?? -1;
	if (raw === undefined || separator < 1) {
		return undefined;
	}
	const kind = raw.slice(0, separator);
	const id = raw.slice(separator + 1);
	if (id.length === 0) {
		return undefined;
	}
	if (kind === "result" || kind === "feedback" || kind === "moment") {
		return { kind, id };
	}
	return undefined;
}

export function sameTraceNode(a: TraceNode | undefined, b: TraceNode | undefined): boolean {
	if (a === undefined || b === undefined || a.kind !== b.kind) {
		return false;
	}
	return !("id" in a) || !("id" in b) || a.id === b.id;
}

/** What a pinned node draws strong. Undefined draws everything strong. */
export interface Traced {
	results: ReadonlySet<string>;
	feedback: ReadonlySet<string>;
}

/**
 * The path through `node`: a result and the feedback written from it, a piece of feedback and
 * the results it was written from, a review and what it recorded itself. The work is the whole
 * chain, so it dims nothing. The work and the review stay strong on every path, since every path
 * runs through them.
 */
export function tracedBy(lineage: ReviewLineage, node: TraceNode | undefined): Traced | undefined {
	switch (node?.kind) {
		case undefined:
		case "end":
		case "moment":
		case "work": {
			return undefined;
		}
		case "review":
		case "results": {
			const reviewId = lineage.review?.id;
			return {
				results: new Set(
					lineage.results.filter((entry) => entry.agentJobId === reviewId).map((e) => e.id),
				),
				feedback: new Set(
					lineage.feedback.filter((entry) => entry.agentJobId === reviewId).map((e) => e.id),
				),
			};
		}
		case "result": {
			return {
				results: new Set([node.id]),
				feedback: new Set(
					lineage.feedback
						.filter((entry) => entry.practiceResultIds.includes(node.id))
						.map((entry) => entry.id),
				),
			};
		}
		case "feedback": {
			const feedback = lineage.feedback.find((entry) => entry.id === node.id);
			return {
				results: new Set(feedback?.practiceResultIds),
				feedback: new Set([node.id]),
			};
		}
	}
}

/**
 * One line of the review's own few moments: what started it, the merge, and the review starting
 * and ending. Every other moment on the work is the "Why it ran" section's.
 */
export type ReviewMoment =
	| { kind: "moment"; moment: ReviewLineageMoment }
	| { kind: "review-started"; at: Date }
	| { kind: "review-finished"; at: Date };

export function reviewMomentAt(entry: ReviewMoment): Date {
	return entry.kind === "moment" ? entry.moment.occurredAt : entry.at;
}

/** A moment and the review's start at the same instant: the moment comes first, since it caused it. */
function rank(entry: ReviewMoment): number {
	return entry.kind === "moment" ? 0 : 1;
}

/** A merge recorded as a moment within this long of the merge time is the same merge. */
const SAME_MERGE_MS = 60_000;

/**
 * The moments that answer whether the merge and the review collided, oldest first: the moment that
 * started the review, any later one folded into it, the merge — even where no moment recorded it —
 * and the review's start and end.
 */
export function reviewMoments(lineage: ReviewLineage): ReviewMoment[] {
	const { workMergedAt, review } = lineage;
	const moments = lineage.workMoments.filter(
		(moment) =>
			moment.startedReview ||
			moment.joinedReview ||
			(moment.kind === "MERGED" &&
				(workMergedAt === undefined ||
					Math.abs(moment.occurredAt.getTime() - workMergedAt.getTime()) <= SAME_MERGE_MS)),
	);
	if (workMergedAt !== undefined && !moments.some((moment) => moment.kind === "MERGED")) {
		moments.push({
			kind: "MERGED",
			occurredAt: workMergedAt,
			startedReview:
				lineage.reviewTrigger === "MERGED" && !moments.some((moment) => moment.startedReview),
			joinedReview: false,
		});
	}
	const entries: ReviewMoment[] = moments.map((moment) => ({ kind: "moment", moment }));
	if (review !== undefined) {
		entries.push({ kind: "review-started", at: review.createdAt });
		if (review.finishedAt !== undefined) {
			entries.push({ kind: "review-finished", at: review.finishedAt });
		}
	}
	return entries.sort(
		(a, b) => reviewMomentAt(a).getTime() - reviewMomentAt(b).getTime() || rank(a) - rank(b),
	);
}

/** How the merge bears on the review, the one question the page answers first. */
export interface MergeNote {
	/** By design: the merge started the review. A problem: the review ran into the merge. */
	tone: "by-design" | "problem" | "plain";
	text: string;
}

/**
 * Whether the merge started the review, which is by design, or the review ran into a merge, which
 * is a problem: it was asked for after the merge, the merge came while it waited in the queue, or
 * while it ran. A review runs from when a worker picked it up, not from when it was asked for.
 * Feedback the merge held back is counted, since that is what the problem cost.
 */
export function mergeNote(lineage: ReviewLineage): MergeNote | undefined {
	const { workMergedAt: merged, review } = lineage;
	if (merged === undefined || review === undefined) {
		return undefined;
	}
	const withheld = lineage.feedback.filter(
		(entry) => entry.agentJobId === review.id && entry.suppressionReason === "ARTIFACT_MERGED",
	).length;
	const cost =
		withheld > 0
			? ` ${countOf(withheld, "piece", "pieces")} of feedback ${withheld === 1 ? "was" : "were"} withheld as already merged.`
			: "";
	const startedByMerge =
		lineage.reviewTrigger === "MERGED" ||
		lineage.workMoments.some((moment) => moment.startedReview && moment.kind === "MERGED");
	if (startedByMerge) {
		return {
			tone: "by-design",
			text: `The merge started this review. Practices that look at a merge run then by design.${cost}`,
		};
	}
	if (merged.getTime() < review.createdAt.getTime()) {
		return {
			tone: "problem",
			text: `The work merged ${durationBetween(merged, review.createdAt)} before this review was asked for.${cost}`,
		};
	}
	const ranFrom = review.startedAt ?? review.createdAt;
	if (review.startedAt !== undefined && merged.getTime() < ranFrom.getTime()) {
		return {
			tone: "problem",
			text: `The work merged while this review waited in the queue, ${durationBetween(merged, ranFrom)} before it started.${cost}`,
		};
	}
	if (review.finishedAt === undefined || merged.getTime() <= review.finishedAt.getTime()) {
		return {
			tone: "problem",
			text: `The work merged while this review ran, ${durationBetween(ranFrom, merged)} after it started.${cost}`,
		};
	}
	return {
		tone: "plain",
		text: `The work merged ${durationBetween(review.finishedAt, merged)} after this review finished.${cost}`,
	};
}
