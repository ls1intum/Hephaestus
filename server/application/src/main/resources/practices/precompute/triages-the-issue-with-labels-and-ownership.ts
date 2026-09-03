interface IssueMeta {
	issue_type?: string | null;
	labels?: string[];
	assignees?: string[];
	milestone?: string | null;
	state?: string;
}

export default function triagesTheIssueWithMetadata(
	_repo: string,
	_diff: Map<string, unknown>,
	m: IssueMeta,
) {
	const labels = m.labels ?? [];
	const issueType = m.issue_type ?? null;
	const assignees = m.assignees ?? [];
	const milestone = m.milestone ?? null;
	const state = (m.state ?? "").toUpperCase();
	const staleLabel =
		labels.find((l) => /out.?of.?date|stale|rotten|obsolete|outdated|deprecated/i.test(l)) ?? null;

	const directions: string[] = [
		`Classification metadata: issueType=${issueType ? `"${issueType}"` : "none"}, labels=${labels.length} [${labels.slice(0, 8).join(", ")}], assignees=${assignees.length}, milestone=${milestone ? `"${milestone}"` : "none"}, state=${state || "?"}.`,
	];
	if (staleLabel && state === "OPEN" && assignees.length === 0) {
		directions.push(`The open issue has staleness label "${staleLabel}" and no assignee.`);
	}
	if (!issueType && labels.length === 0 && assignees.length === 0 && !milestone) {
		directions.push(`No issue type, label, assignee, or milestone is present.`);
	}

	return {
		hints: [],
		metrics: {
			hasIssueType: issueType ? 1 : 0,
			labelCount: labels.length,
			assigneeCount: assignees.length,
			hasMilestone: milestone ? 1 : 0,
			staleOpenUnowned: staleLabel && state === "OPEN" && assignees.length === 0 ? 1 : 0,
		},
		directions,
	};
}
