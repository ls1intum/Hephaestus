import { tool } from "@opencode-ai/plugin";
import { getPRStatus } from "./lib/pr-status.js";

/**
 * OpenCode tool to check PR status for all active worktrees.
 */
export default tool({
  description: "Check PR status for all active worktrees",
  args: {},
  async execute() {
    return getPRStatus();
  },
});
