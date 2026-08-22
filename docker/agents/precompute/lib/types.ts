/** Precomputation types — hints and directions, never verdicts */

export interface Hint {
	file: string;
	line: number;
	pattern: string;
	context: string;
	inDiff: boolean;
	flags: Record<string, HintFlag>;
}

/** A hint flag renders into summary.md verbatim, so it stays a JSON scalar. */
export type HintFlag = boolean | number | string;

/**
 * The parsed `inputs/context/metadata.json` every script receives as its 3rd argument. Its shape
 * depends on the artifact under review — `PullRequestMetadata` below for a pull request, the issue
 * metadata the issue scripts declare for an issue — so the shared contract says only "a JSON
 * object". A script narrows it by declaring the parameter type it actually reads.
 */
export type ArtifactMetadata = Record<string, unknown>;

/**
 * What a precompute script returns. `practice` and `status` are NOT part of it: the runner stamps
 * those on, so the filename slug stays the single source of truth for a script's identity.
 */
export interface PracticeFindings {
	hints: Hint[];
	metrics: Record<string, number>;
	directions: string[];
}

/** A validated `PracticeFindings` attributed to a practice — the shape of `{output}/{slug}.json`. */
export interface PracticeResult extends PracticeFindings {
	practice: string;
	status: "ok" | "error" | "timeout";
}

/**
 * A precompute script's default export. Receives the repo checkout, the parsed diff, the artifact
 * metadata, and (optionally) the materialised context directory so it can read the SAME cross-artifact
 * context the agent sees (project_inventory.json, linked_work_items.json, …) via lib/context.ts helpers.
 * The 4th argument is additive — existing 3-arg scripts keep working unchanged.
 *
 * Scripts are dynamic data (injected from the DB), so this is the contract the runner CALLS under,
 * not a guarantee: `parseFindings` in lib/practice-contract.ts re-checks the return value at runtime.
 */
export type PracticeScript = (
	repoPath: string,
	diffFiles: Map<string, DiffFile>,
	metadata: ArtifactMetadata,
	contextDir?: string,
) => PracticeFindings | Promise<PracticeFindings>;

export interface DiffFile {
	path: string;
	addedLines: Map<number, string>;
	removedLines: Map<number, string>;
	hunks: DiffHunk[];
}

export interface DiffHunk {
	oldStart: number;
	oldCount: number;
	newStart: number;
	newCount: number;
	lines: string[];
}

/**
 * Pull request metadata — matches the JSON produced by
 * PullRequestReviewHandler.buildPullRequestMetadata() on the server.
 * Scripts should import this instead of declaring ad-hoc types.
 */
export interface PullRequestMetadata {
	pr_number: number;
	pr_url: string;
	repository_full_name: string;
	source_branch: string;
	target_branch: string;
	commit_sha: string;
	enriched: boolean;
	title?: string;
	body?: string;
	state?: string;
	is_draft?: boolean;
	additions?: number;
	deletions?: number;
	changed_files?: number;
	author?: string;
	commits?: Array<{
		sha?: string;
		title?: string;
		message?: string;
	}>;
}
