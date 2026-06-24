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
	const assignees = m.assignees ?? (m.assignee ? [m.assignee] : []);
	const milestone = m.milestone ?? null;
	const state = (m.state ?? "").toUpperCase();
	// A self-applied staleness/rot label on a still-OPEN issue with no owner is an untriaged liability, not a routable issue.
	const staleLabel = labels.find((l) => /out.?of.?date|stale|rotten|obsolete|outdated|deprecated/i.test(l)) ?? null;

	const directions: string[] = [
		`Routing-metadata presence facts: labels=${labels.length} [${labels.slice(0, 8).join(", ")}], assignees=${assignees.length}, milestone=${milestone ? `"${milestone}"` : "none"}, state=${state || "?"}. Judge only PRESENCE/routability, never whether a label or owner is the CORRECT one.`,
	];
	if (staleLabel && state === "OPEN" && assignees.length === 0) {
		directions.push(
			`ROT SIGNAL: OPEN issue self-labeled "${staleLabel}" with no assignee — an untriaged rotting liability. This is a candidate triage/lifecycle finding (no owner to act on a known-stale item), not something to wave through as routable.`,
		);
	}
	if (labels.length === 0 && assignees.length === 0 && !milestone) {
		directions.push(
			`No labels, no assignee, no milestone — a strong candidate finding for un-triaged routing metadata (unless the issue is a pure question).`,
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
