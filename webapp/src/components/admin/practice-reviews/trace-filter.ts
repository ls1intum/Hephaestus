import type { ReviewLineage } from "@/api/types.gen";

import { type LineageRecords, lineageRecords, silentNotMet } from "./lineage-chain";
import { feedbackLoss } from "./trace-bottleneck";

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
			const results = ids(
				records.results.filter((result) => result.practiceSlug === filter.practiceSlug),
			);
			return {
				results,
				feedback: ids(
					records.feedback.filter((entry) => entry.basis.some((id) => results.has(id))),
				),
			};
		}
	}
}
