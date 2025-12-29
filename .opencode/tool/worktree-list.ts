import { tool } from "@opencode-ai/plugin";
import { listWorktrees } from "./lib/worktree.js";

/**
 * OpenCode tool to list all active git worktrees in .worktrees/ directory.
 */
export default tool({
  description: "List all active git worktrees in .worktrees/ directory",
  args: {},
  async execute() {
    return listWorktrees();
  },
});
