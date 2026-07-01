/** Precomputation types — hints and directions, never verdicts */

export interface Hint {
	file: string;
	line: number;
	pattern: string;
	context: string;
	inDiff: boolean;
	flags: Record<string, boolean | number | string>;
}

/**
 * A precompute script's default export. Receives the repo checkout, the parsed diff, the artifact
 * metadata, and (optionally) the materialised context directory so it can read the SAME cross-artifact
 * context the agent sees (project_inventory.json, linked_work_items.json, …) via lib/context.ts helpers.
 * The 4th argument is additive — existing 3-arg scripts keep working unchanged.
 */
export type PracticeScript = (
	repoPath: string,
	diffFiles: Map<string, DiffFile>,
	metadata: any,
	contextDir?: string,
) => PracticeResult | Promise<PracticeResult>;

export interface PracticeResult {
	practice: string;
	status: "ok" | "error" | "timeout";
	hints: Hint[];
	metrics: Record<string, number>;
	directions: string[];
}

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
