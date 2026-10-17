import type { ReviewLineage } from "@/api/types.gen";
import { OUTCOME_DEFS, type Outcome } from "@/components/practice-vocabulary/outcome-defs";

import { type LineageRecords, lineageRecords, silentNotMet, verdictCounts } from "./lineage-chain";
import { feedbackLoss } from "./trace-bottleneck";

/**
 * What a trace shows of its chain: everything, only the feedback, only what was lost on the way,
 * or only what one practice or one outcome led to.
 */
export type TraceFilter =
	| { kind: "all" }
	| { kind: "feedback" }
	| { kind: "losses" }
	| { kind: "practice"; practiceSlug: string }
	| { kind: "outcome"; outcome: Outcome };

export const ALL_STEPS: TraceFilter = { kind: "all" };

/**
 * The filter as the select writes it: `all`, `feedback`, `losses`, `practice:<slug>`,
 * `outcome:<OUTCOME>`.
 */
export function traceFilterValue(filter: TraceFilter): string {
	switch (filter.kind) {
		case "practice": {
			return `practice:${filter.practiceSlug}`;
		}
		case "outcome": {
			return `outcome:${filter.outcome}`;
		}
		case "all":
		case "feedback":
		case "losses": {
			return filter.kind;
		}
	}
}

function isOutcome(value: string): value is Outcome {
	return Object.hasOwn(OUTCOME_DEFS, value);
}

export function parseTraceFilter(value: string): TraceFilter {
	if (value === "feedback" || value === "losses") {
		return { kind: value };
	}
	if (value.startsWith("practice:")) {
		return { kind: "practice", practiceSlug: value.slice("practice:".length) };
	}
	const outcome = value.startsWith("outcome:") ? value.slice("outcome:".length) : "";
	return isOutcome(outcome) ? { kind: "outcome", outcome } : ALL_STEPS;
}

/** The outcomes the trace's results have, with how many of each, in the verdict's order. */
export function traceOutcomes(lineage: ReviewLineage): { outcome: Outcome; count: number }[] {
	return verdictCounts(lineageRecords(lineage).results);
}

export function tracePractices(lineage: ReviewLineage): { slug: string; name: string }[] {
	const practices = new Map<string, string>();
	for (const result of lineageRecords(lineage).results) {
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
 * The records a filtered trace shows. The filter only picks which records show: a piece of feedback
 * it keeps still rests on every result and problem it rests on in the whole chain, hidden or not,
 * because `filteredLineage` drops the edges from the results it hides.
 */
export function shownRecords(whole: LineageRecords, shown: ReviewLineage): LineageRecords {
	const records = lineageRecords(shown);
	const keptFeedbackIds = new Set(records.feedback.map((entry) => entry.id));
	return { ...records, feedback: whole.feedback.filter((entry) => keptFeedbackIds.has(entry.id)) };
}

export function filteredLineage(lineage: ReviewLineage, filter: TraceFilter): ReviewLineage {
	if (filter.kind === "all") {
		return lineage;
	}
	const records = lineageRecords(lineage);
	const { results, feedback } = kept(records, filter);
	const keptResults = new Set(
		lineage.nodes
			.filter((node) => node.result !== null && results.has(node.result.id))
			.map((node) => node.id),
	);
	const problems = new Set(
		lineage.edges.filter((edge) => keptResults.has(edge.from)).map((edge) => edge.to),
	);
	const nodes = lineage.nodes.filter(
		(node) =>
			node.id === lineage.rootId ||
			node.kind === "ITEM" ||
			node.kind === "REVIEW" ||
			(node.result !== null && results.has(node.result.id)) ||
			(node.feedback !== null && feedback.has(node.feedback.id)) ||
			(node.kind === "PROBLEM" && problems.has(node.id)),
	);
	const ids = new Set(nodes.map((node) => node.id));
	return {
		...lineage,
		nodes,
		edges: lineage.edges.filter((edge) => ids.has(edge.from) && ids.has(edge.to)),
	};
}

const ids = (entries: readonly { id: string }[]) => new Set(entries.map((entry) => entry.id));

function kept(
	records: LineageRecords,
	filter: Exclude<TraceFilter, { kind: "all" }>,
): { results: ReadonlySet<string>; feedback: ReadonlySet<string> } {
	switch (filter.kind) {
		case "feedback": {
			return { results: new Set(), feedback: ids(records.feedback) };
		}
		case "losses": {
			const lost = records.feedback.filter((entry) => feedbackLoss(entry) !== undefined);
			const behind = new Set(lost.flatMap((entry) => entry.basis));
			const silent = ids(silentNotMet(records));
			return {
				results: ids(
					records.results.filter((result) => behind.has(result.id) || silent.has(result.id)),
				),
				feedback: ids(lost),
			};
		}
		case "practice": {
			return withTheirFeedback(
				records,
				records.results.filter((result) => result.practiceSlug === filter.practiceSlug),
			);
		}
		case "outcome": {
			return withTheirFeedback(
				records,
				records.results.filter((result) => result.outcome === filter.outcome),
			);
		}
	}
}

function withTheirFeedback(
	records: LineageRecords,
	chosen: readonly { id: string }[],
): { results: ReadonlySet<string>; feedback: ReadonlySet<string> } {
	const results = ids(chosen);
	return {
		results,
		feedback: ids(records.feedback.filter((entry) => entry.basis.some((id) => results.has(id)))),
	};
}
