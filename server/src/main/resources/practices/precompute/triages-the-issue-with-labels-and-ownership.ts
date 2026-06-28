// Precompute HINTS for triages-the-issue-with-labels-and-ownership: surface the PRESENCE of routing
// metadata — labels[], assignees[], milestone. Pure presence facts (never accuracy); the LLM decides
// whether the issue is routable at a glance. No observation.
import type { Hint } from "../lib/types";

interface IssueMeta {
	labels?: string[];
	assignees?: string[];
	assignee?: string | null;
	milestone?: string | null;
	title?: string;
	state?: string;
}

export default async function (_repo: string, _diff: Map<string, unknown>, m: IssueMeta) {
	const labels = m.labels ?? [];
	// Prefer a non-empty plural array, then fall back to the singular owner — a producer that always emits
	// `assignees: []` plus a separate `assignee` would otherwise under-report ownership (`??` only fires on
	// null/undefined, not on an empty array).
	const assignees = m.assignees && m.assignees.length > 0 ? m.assignees : m.assignee ? [m.assignee] : [];
	const milestone = m.milestone ?? null;
	const state = (m.state ?? "").toUpperCase();
	// Presence of a staleness/rot label on a still-OPEN issue with no owner — surface it as a fact; the LLM decides whether routing metadata is missing.
	const staleLabel = labels.find((l) => /out.?of.?date|stale|rotten|obsolete|outdated|deprecated/i.test(l)) ?? null;

	const directions: string[] = [
		`Routing-metadata presence facts: labels=${labels.length} [${labels.slice(0, 8).join(", ")}], assignees=${assignees.length}, milestone=${milestone ? `"${milestone}"` : "none"}, state=${state || "?"}. Judge only PRESENCE/routability, never whether a label or owner is the CORRECT one.`,
	];
	if (staleLabel && state === "OPEN" && assignees.length === 0) {
		directions.push(
			`OPEN issue carries a staleness label "${staleLabel}" and has no assignee — presence facts only; confirm against the labels/owner before deciding whether routing metadata is missing, and do not assert the label is the correct one.`,
		);
	}
	if (labels.length === 0 && assignees.length === 0 && !milestone) {
		directions.push(
			`No labels, no assignee, no milestone present — confirm whether the issue is a pure question/discussion (which legitimately needs none) before treating absent routing metadata as a gap.`,
		);
	}

	const hints: Hint[] = [];
	return {
		hints,
		metrics: {
			labelCount: labels.length,
			assigneeCount: assignees.length,
			hasMilestone: milestone ? 1 : 0,
			staleOpenUnowned: staleLabel && state === "OPEN" && assignees.length === 0 ? 1 : 0,
		},
		directions,
	};
}
