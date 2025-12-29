import { execSync } from "child_process";

/**
 * Escape a string for safe use in shell commands.
 * Uses single-quote wrapping with proper escaping of embedded quotes.
 */
export function shellEscape(s: string): string {
  return `'${s.replace(/'/g, "'\\''")}'`;
}

/**
 * Result of parsing a git worktree entry.
 */
export interface WorktreeEntry {
  path: string;
  branch: string;
  head: string;
}

/**
 * PR check status from GitHub.
 */
export interface CheckInfo {
  status?: string;
  conclusion?: string;
}

/**
 * Execute a shell command and return trimmed stdout.
 * @param cmd - The command to execute
 * @param cwd - Optional working directory
 * @returns Trimmed stdout output
 * @throws Error with stderr/message if command fails
 */
export function run(cmd: string, cwd?: string): string {
  try {
    return execSync(cmd, {
      cwd,
      encoding: "utf8",
      stdio: ["pipe", "pipe", "pipe"],
    }).trim();
  } catch (e: unknown) {
    const err = e as { stderr?: string; message?: string };
    throw new Error(`Command failed: ${cmd}\n${err.stderr || err.message}`);
  }
}

/**
 * Execute a shell command, returning empty string on failure.
 * @param cmd - The command to execute
 * @param cwd - Optional working directory
 * @returns Trimmed stdout output, or empty string on error
 */
export function runSafe(cmd: string, cwd?: string): string {
  try {
    return run(cmd, cwd);
  } catch {
    return "";
  }
}

/**
 * Parse git worktree list --porcelain output into structured entries.
 * @param output - Raw output from git worktree list --porcelain
 * @returns Array of worktree entries
 */
export function parseWorktreeList(output: string): WorktreeEntry[] {
  const worktrees: WorktreeEntry[] = [];
  let current: Partial<WorktreeEntry> = {};

  for (const line of output.split("\n")) {
    if (line.startsWith("worktree ")) {
      if (current.path) {
        worktrees.push(current as WorktreeEntry);
      }
      current = { path: line.replace("worktree ", ""), head: "", branch: "" };
    } else if (line.startsWith("HEAD ")) {
      current.head = line.replace("HEAD ", "").substring(0, 8);
    } else if (line.startsWith("branch ")) {
      current.branch = line.replace("branch refs/heads/", "");
    }
  }

  if (current.path) {
    worktrees.push(current as WorktreeEntry);
  }

  return worktrees;
}

/**
 * Filter worktrees to only those in .worktrees/ directory.
 * @param worktrees - Array of worktree entries
 * @returns Filtered array containing only .worktrees/ entries
 */
export function filterActiveWorktrees(
  worktrees: WorktreeEntry[],
): WorktreeEntry[] {
  return worktrees.filter((w) => w.path.includes(".worktrees/"));
}
