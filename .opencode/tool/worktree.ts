import { existsSync } from "fs";
import {
  run,
  shellEscape,
  parseWorktreeList,
  filterActiveWorktrees,
} from "./utils.js";

/**
 * List all active git worktrees in the .worktrees/ directory.
 * @returns Formatted string listing worktrees with branch and HEAD info
 */
export function listWorktrees(): string {
  try {
    const output = run("git worktree list --porcelain");
    const worktrees = parseWorktreeList(output);
    const filtered = filterActiveWorktrees(worktrees);

    if (filtered.length === 0) {
      return "No active worktrees in .worktrees/ directory.";
    }

    const lines = filtered.map((w) => `- ${w.path} [${w.branch}] (${w.head})`);
    return `Active worktrees:\n${lines.join("\n")}`;
  } catch (e: unknown) {
    const err = e as { message?: string };
    return `Error listing worktrees: ${err.message}`;
  }
}

/**
 * Create a new git worktree for isolated development.
 * @param branch - The branch name to create or checkout
 * @param base - The base branch to create from (default: main)
 * @returns Status message about the created worktree
 */
export function createWorktree(branch: string, base = "main"): string {
  const safeName = `wt-${branch.replace(/[^a-zA-Z0-9-]/g, "-")}`;
  const path = `.worktrees/${safeName}`;

  try {
    if (existsSync(path)) {
      return `Worktree already exists: ${path}`;
    }

    let branchExists = false;
    try {
      run(`git rev-parse --verify ${shellEscape(branch)}`);
      branchExists = true;
    } catch {
      // Branch doesn't exist, will create it
    }

    if (branchExists) {
      run(`git worktree add ${shellEscape(path)} ${shellEscape(branch)}`);
    } else {
      run(
        `git worktree add -b ${shellEscape(branch)} ${shellEscape(path)} ${shellEscape(base)}`,
      );
    }

    return `Created worktree: ${path}\nBranch: ${branch}\nBase: ${base}`;
  } catch (e: unknown) {
    const err = e as { message?: string };
    return `Error creating worktree: ${err.message}`;
  }
}

/**
 * Remove a git worktree and prune stale entries.
 * @param path - Path to the worktree to remove
 * @returns Status message about the removal
 */
export function removeWorktree(path: string): string {
  try {
    run(`git worktree remove --force ${shellEscape(path)}`);
    run("git worktree prune");
    return `Removed worktree: ${path}`;
  } catch (e: unknown) {
    const err = e as { message?: string };
    return `Error removing worktree: ${err.message}`;
  }
}

// CLI runner
if (
  import.meta.url === `file://${process.argv[1]}` ||
  process.argv[1]?.endsWith("worktree.ts")
) {
  const cmd = process.argv[2];
  const arg1 = process.argv[3];
  const arg2 = process.argv[4];

  switch (cmd) {
    case "list":
      console.log(listWorktrees());
      break;
    case "create":
      if (!arg1) {
        console.error("Usage: worktree.ts create <branch> [base]");
        process.exit(1);
      }
      console.log(createWorktree(arg1, arg2));
      break;
    case "remove":
      if (!arg1) {
        console.error("Usage: worktree.ts remove <path>");
        process.exit(1);
      }
      console.log(removeWorktree(arg1));
      break;
    default:
      console.log("Usage: worktree.ts <list|create|remove> [args]");
      console.log("  list              - List all worktrees");
      console.log("  create <branch>   - Create worktree for branch");
      console.log("  remove <path>     - Remove worktree");
  }
}

// OpenCode plugin exports (only if @opencode-ai/plugin is available)
type ToolFn = typeof import("@opencode-ai/plugin").tool;
let tool: ToolFn | undefined;

try {
  const plugin = await import("@opencode-ai/plugin");
  tool = plugin.tool;
} catch {
  // Plugin not available, skip tool registration
}

/** Tool to list all active git worktrees in .worktrees/ directory */
export const worktree_list = tool
  ? tool({
      description: "List all active git worktrees in .worktrees/ directory",
      args: {},
      async execute() {
        return listWorktrees();
      },
    })
  : undefined;

/** Tool to create a new git worktree for isolated development */
export const worktree_create = tool
  ? tool({
      description: "Create a new git worktree for isolated development",
      args: {
        branch: tool.schema.string().describe("Branch name to create"),
        base: tool.schema
          .string()
          .optional()
          .describe("Base branch (default: main)"),
      },
      async execute({ branch, base }: { branch: string; base?: string }) {
        return createWorktree(branch, base);
      },
    })
  : undefined;

/** Tool to remove a git worktree */
export const worktree_remove = tool
  ? tool({
      description: "Remove a git worktree",
      args: {
        path: tool.schema.string().describe("Path to the worktree"),
      },
      async execute({ path }: { path: string }) {
        return removeWorktree(path);
      },
    })
  : undefined;
