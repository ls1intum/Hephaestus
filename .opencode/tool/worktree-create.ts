import { tool } from "@opencode-ai/plugin";
import { createWorktree } from "./lib/worktree.js";

/**
 * OpenCode tool to create a new git worktree for isolated development.
 */
export default tool({
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
});
