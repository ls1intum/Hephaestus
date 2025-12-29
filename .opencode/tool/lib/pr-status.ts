import {
  runSafe,
  shellEscape,
  parseWorktreeList,
  filterActiveWorktrees,
  type CheckInfo,
} from "./utils.js";

/**
 * PR information for a worktree.
 */
interface PRInfo {
  worktree: string;
  branch: string;
  prNumber?: number;
  prUrl?: string;
  prState?: string;
  checksStatus?: string;
  reviewDecision?: string;
  behindMain?: number;
  lastCommit?: string;
}

/**
 * Get PR status for all active worktrees.
 * @returns Formatted markdown report of PR status across worktrees
 */
export function getPRStatus(): string {
  const worktreeOutput = runSafe("git worktree list --porcelain");
  const worktrees = parseWorktreeList(worktreeOutput);
  const filtered = filterActiveWorktrees(worktrees);

  if (filtered.length === 0) {
    return "No active worktrees. Use `npm run worktree:create <branch>` to start.";
  }

  const results: PRInfo[] = [];

  for (const wt of filtered) {
    const info: PRInfo = {
      worktree: wt.path,
      branch: wt.branch,
    };

    const prJson = runSafe(
      `gh pr view ${shellEscape(wt.branch)} --json number,url,state,statusCheckRollup,reviewDecision 2>/dev/null`,
    );

    if (prJson) {
      try {
        const pr = JSON.parse(prJson) as {
          number?: number;
          url?: string;
          state?: string;
          statusCheckRollup?: CheckInfo[];
          reviewDecision?: string;
        };
        info.prNumber = pr.number;
        info.prUrl = pr.url;
        info.prState = pr.state;

        const checks: CheckInfo[] = pr.statusCheckRollup || [];
        if (checks.length === 0) {
          info.checksStatus = "none";
        } else if (
          checks.some(
            (c) => c.status === "IN_PROGRESS" || c.status === "QUEUED",
          )
        ) {
          info.checksStatus = "running";
        } else if (checks.some((c) => c.conclusion === "FAILURE")) {
          info.checksStatus = "failed";
        } else if (checks.every((c) => c.conclusion === "SUCCESS")) {
          info.checksStatus = "passed";
        } else {
          info.checksStatus = "mixed";
        }

        info.reviewDecision = pr.reviewDecision || "none";
      } catch {
        // Failed to parse PR JSON, continue without PR info
      }
    }

    info.lastCommit = runSafe(`git log -1 --format="%s" 2>/dev/null`, wt.path);
    results.push(info);
  }

  const lines: string[] = ["## Worktree Status\n"];

  for (const info of results) {
    const status: string[] = [];

    if (info.prNumber) {
      status.push(`PR #${info.prNumber}`);

      if (info.prState === "MERGED") {
        status.push("MERGED");
      } else if (info.prState === "CLOSED") {
        status.push("CLOSED");
      } else {
        if (info.checksStatus === "failed") status.push("CI FAILED");
        else if (info.checksStatus === "running") status.push("CI running...");
        else if (info.checksStatus === "passed") status.push("CI passed");

        if (info.reviewDecision === "CHANGES_REQUESTED")
          status.push("CHANGES REQUESTED");
        else if (info.reviewDecision === "APPROVED") status.push("APPROVED");
      }
    } else {
      status.push("No PR");
    }

    lines.push(`### ${info.branch}`);
    lines.push(`- Path: \`${info.worktree}\``);
    lines.push(`- Status: ${status.join(" | ")}`);
    if (info.prUrl) lines.push(`- URL: ${info.prUrl}`);
    if (info.lastCommit) lines.push(`- Last: "${info.lastCommit}"`);
    lines.push("");
  }

  return lines.join("\n");
}

// CLI runner for standalone testing
if (
  import.meta.url === `file://${process.argv[1]}` ||
  process.argv[1]?.endsWith("pr-status.ts")
) {
  console.log(getPRStatus());
}
