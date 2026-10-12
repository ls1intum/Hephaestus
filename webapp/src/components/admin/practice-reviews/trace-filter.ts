import type { ReviewLineage } from "@/api/types.gen";

import { silentNotMet } from "./lineage-chain";
import { feedbackLost } from "./trace-verdict";

/**
 * What a trace shows of its chain: everything, only the feedback, only what was lost on the way,
 * or only what one practice led to.
 */
export type TraceFilter =
	| { kind: "all" }
	| { kind: "feedback" }
	| { kind: "losses" }
	| { kind: "practice"; practiceSlug: string };

export const ALL_STEPS: TraceFilter = { kind: "all" };

/** The filter as the select writes it: `all`, `feedback`, `losses`, `practice:<slug>`. */
export function traceFilterValue(filter: TraceFilter): string {
	return filter.kind === "practice" ? `practice:${filter.practiceSlug}` : filter.kind;
}

export function parseTraceFilter(value: string): TraceFilter {
	if (value === "feedback" || value === "losses") {
		return { kind: value };
	}
	return value.startsWith("practice:")
		? { kind: "practice", practiceSlug: value.slice("practice:".length) }
		: ALL_STEPS;
}

/** The practices a trace can be narrowed to: those its results answered, by name. */
export function tracePractices(lineage: ReviewLineage): { slug: string; name: string }[] {
	const practices = new Map<string, string>();
	for (const result of lineage.results) {
		practices.set(result.practiceSlug, result.practiceName);
	}
	return [...practices.entries()]
		.map(([slug, name]) => ({ slug, name }))
		.sort((a, b) => a.name.localeCompare(b.name));
}

/** What an active filter leaves out, in the words a rail says where a stage is empty under it. */
export function hiddenByFilter(filter: TraceFilter): string {
	return filter.kind === "losses" ? "Nothing lost on this path." : "Hidden by the filter.";
}

/**
 * The chain a filter leaves. The work, the moment that started the review and the review stay on
 * every one, so the rail still reads from its start; what is left out is said by its counts being
 * gone, never as more records not listed.
 *
 * - Only feedback: every piece of feedback, and no result.
 * - Only losses: feedback whose path ends lost, as its end step says (withheld, rejected, replaced,
 *   failed, withdrawn), and the not-met results that led to no feedback, with the results
 *   the lost feedback was written from. Feedback still on its way is not lost yet.
 * - One practice: its results, and the feedback written from any of them.
 */
export function filteredLineage(lineage: ReviewLineage, filter: TraceFilter): ReviewLineage {
	if (filter.kind === "all") {
		return lineage;
	}
	const startingMoments = lineage.workMoments.filter(
		(moment) => moment.startedReview || moment.joinedReview,
	);
	const narrowed = {
		...lineage,
		moreResults: 0,
		moreFeedback: 0,
		workMoments: startingMoments,
	};
	switch (filter.kind) {
		case "feedback": {
			return { ...narrowed, results: [] };
		}
		case "losses": {
			const lost = lineage.feedback.filter(feedbackLost);
			const behind = new Set(lost.flatMap((feedback) => feedback.practiceResultIds));
			const silent = new Set(silentNotMet(lineage).map((result) => result.id));
			return {
				...narrowed,
				feedback: lost,
				results: lineage.results.filter((result) => behind.has(result.id) || silent.has(result.id)),
			};
		}
		case "practice": {
			const results = lineage.results.filter(
				(result) => result.practiceSlug === filter.practiceSlug,
			);
			const ids = new Set(results.map((result) => result.id));
			return {
				...narrowed,
				results,
				feedback: lineage.feedback.filter((feedback) =>
					feedback.practiceResultIds.some((id) => ids.has(id)),
				),
			};
		}
	}
}
