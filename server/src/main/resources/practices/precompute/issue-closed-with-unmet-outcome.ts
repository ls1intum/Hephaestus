// Precompute HINTS for issue-closed-with-unmet-outcome: at close time, surface whether the issue's own
// stated definition-of-done was met — unchecked acceptance boxes and open sub-issues at the moment of
// closing. FACTS only (rollup + checkbox counts + close metadata); the LLM decides whether the outcome was
// confirmed before closing (an unticked box may simply never have been ticked). No observation.
import type { Hint } from "../lib/types";

interface IssueMeta {
	body?: string;
	state?: string;
	state_reason?: string | null;
	sub_issues_total?: number;
	sub_issues_completed?: number;
	closed_at?: string | null;
}

export default async function (_repo: string, _diff: Map<string, unknown>, m: IssueMeta) {
	const body = (m.body ?? "").trim();
	const state = (m.state ?? "").toUpperCase();
	const reason = m.state_reason ?? null;
	const unchecked = (body.match(/^[\s>]*[-*]\s+\[ \]/gm) ?? []).length;
	const checked = (body.match(/^[\s>]*[-*]\s+\[[xX]\]/gm) ?? []).length;
	const subTotal = m.sub_issues_total ?? 0;
	const subDone = m.sub_issues_completed ?? 0;
	const subOpen = Math.max(0, subTotal - subDone);

	const directions: string[] = [];
	if (state !== "CLOSED") {
		directions.push(
			`Issue state is ${state || "unknown"} — not CLOSED; this close-time check concerns only closed issues.`,
		);
	} else {
		directions.push(
			`Close-time outcome facts: state_reason=${reason ?? "none"}, uncheckedBoxes=${unchecked}, checkedBoxes=${checked}, subIssuesOpen=${subOpen}/${subTotal}, closed_at=${m.closed_at ?? "?"}.`,
		);
		if (unchecked > 0 || subOpen > 0) {
			directions.push(
				`Closed with ${unchecked} unchecked acceptance item(s) and ${subOpen} open sub-issue(s) — candidate for a "closed before its own DoD was confirmed" finding. Note: an unticked box may simply never have been ticked; frame as a lifecycle habit, not a claim the work is wrong.`,
			);
		} else if (subTotal > 0 || checked > 0) {
			directions.push(
				`All ${checked} acceptance item(s) checked and ${subTotal} sub-issue(s) completed at close — outcome appears confirmed.`,
			);
		}
	}

	const hints: Hint[] = [];
	return {
		hints,
		metrics: {
			stateClosed: state === "CLOSED" ? 1 : 0,
			uncheckedBoxes: unchecked,
			checkedBoxes: checked,
			subIssuesOpenAtClose: subOpen,
			subIssuesTotal: subTotal,
		},
		directions,
	};
}
