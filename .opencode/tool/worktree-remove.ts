import { tool } from "@opencode-ai/plugin";
import { removeWorktree } from "./lib/worktree.js";

/**
 * OpenCode tool to remove a git worktree.
 */
export default tool({
  description: "Remove a git worktree",
  args: {
    path: tool.schema.string().describe("Path to the worktree"),
  },
  async execute({ path }: { path: string }) {
    return removeWorktree(path);
  },
});
