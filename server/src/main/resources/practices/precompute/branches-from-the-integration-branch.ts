// Precompute HINTS for branches-from-the-integration-branch: branch topology facts. The LLM judges habit.
import type { DiffFile, PullRequestMetadata } from "../lib/types";
const INTEGRATION = new Set(["main", "master", "dev", "develop", "trunk", "release"]);

export default async function (_r: string, _d: Map<string, DiffFile>, meta: PullRequestMetadata) {
	// The PR's own commit list (capped upstream). NOT "commits ahead of the integration branch" — that and
	// the per-author breakdown live in the git history, not metadata, so the LLM reads them from the clone.
	const prCommitCount = (meta.commits ?? []).length;
	const target = (meta.target_branch ?? "").toLowerCase();
	const source = (meta.source_branch ?? "").toLowerCase();
	const targetIsIntegration = INTEGRATION.has(target) ? 1 : 0;
	const directions: string[] = [];
	if (meta.target_branch && !targetIsIntegration) {
		directions.push(
			`This PR targets '${meta.target_branch}', which is not a conventional integration branch (main/master/dev/develop) — investigate whether the branch was cut from the integration branch or stacked on another feature branch.`,
		);
	} else if (targetIsIntegration) {
		directions.push(
			`target_branch '${meta.target_branch}' is a conventional integration branch (main/master/dev/develop); the full clone is mounted at inputs/sources/scm/repo if you need the ahead-range/authors.`,
		);
	}
	if (prCommitCount >= 8) {
		directions.push(
			`The PR lists ${prCommitCount} commits — a large count can indicate a long-lived branch rather than a fresh cut from the integration branch; inspect the git history at inputs/sources/scm/repo for the actual ahead-range and its authors.`,
		);
	}
	return {
		hints: [],
		metrics: { prCommitCount, targetIsIntegration, sourceIsIntegration: INTEGRATION.has(source) ? 1 : 0 },
		directions,
	};
}
