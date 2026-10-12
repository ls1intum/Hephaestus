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

/** The practices a trace can be narrowed to: those its observations answered, by name. */
export function tracePractices(lineage: ReviewLineage): { slug: string; name: string }[] {
	const practices = new Map<string, string>();
	for (const observation of lineage.observations) {
		practices.set(observation.practiceSlug, observation.practiceName);
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
 * - Only feedback: every piece of feedback, and no observation.
 * - Only losses: feedback whose path ends lost, as its end step says (withheld, rejected, replaced,
 *   failed, withdrawn), and the not-met observations that led to no feedback, with the observations
 *   the lost feedback was written from. Feedback still on its way is not lost yet.
 * - One practice: its observations, and the feedback written from any of them.
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
		moreObservations: 0,
		moreFeedback: 0,
		workMoments: startingMoments,
	};
	switch (filter.kind) {
		case "feedback": {
			return { ...narrowed, observations: [] };
		}
		case "losses": {
			const lost = lineage.feedback.filter(feedbackLost);
			const behind = new Set(lost.flatMap((feedback) => feedback.observationIds));
			const silent = new Set(silentNotMet(lineage).map((observation) => observation.id));
			return {
				...narrowed,
				feedback: lost,
				observations: lineage.observations.filter(
					(observation) => behind.has(observation.id) || silent.has(observation.id),
				),
			};
		}
		case "practice": {
			const observations = lineage.observations.filter(
				(observation) => observation.practiceSlug === filter.practiceSlug,
			);
			const ids = new Set(observations.map((observation) => observation.id));
			return {
				...narrowed,
				observations,
				feedback: lineage.feedback.filter((feedback) =>
					feedback.observationIds.some((id) => ids.has(id)),
				),
			};
		}
	}
}
