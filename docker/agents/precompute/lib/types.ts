/** Precomputation types — hints and directions, never verdicts */

export interface Hint {
  file: string;
  line: number;
  pattern: string;
  context: string;
  inDiff: boolean;
  flags: Record<string, boolean | number | string>;
}

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
}
