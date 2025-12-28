/**
 * Tool Merger for Langfuse-Managed Tool Descriptions
 *
 * This module merges tool descriptions from Langfuse prompt config with
 * local tool implementations. This architecture enables:
 *
 * 1. Tool descriptions versioned in Langfuse alongside prompts
 * 2. A/B testing different tool descriptions without code changes
 * 3. Type-safe local execution with Zod schemas
 *
 * The flow:
 * 1. Load prompt from Langfuse (includes tools in config)
 * 2. Create local tools with local descriptions (fallback)
 * 3. Override descriptions with Langfuse definitions
 *
 * This uses the AI SDK's tool spreading pattern - tools are plain objects
 * and their `description` property can be overridden at runtime.
 *
 * @see https://langfuse.com/docs/prompt-management/features/config#function-calling
 */

import type { Tool, ToolSet } from "ai";
import type { PromptToolDefinition } from "@/prompts/types";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Merged tool config from Langfuse.
 */
export interface MergedToolConfig {
	/** Tool choice strategy from Langfuse config */
	toolChoice?: "auto" | "required" | "none" | { type: "tool"; toolName: string };
	/** Max tool steps from Langfuse config */
	maxToolSteps?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Description Override Function
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Override tool descriptions with Langfuse-managed definitions.
 *
 * This function takes existing AI SDK tools and replaces their descriptions
 * with ones from Langfuse prompt config. Tools not in Langfuse config keep
 * their original descriptions (graceful fallback).
 *
 * @param localTools - Tools created via registry (with local descriptions)
 * @param langfuseTools - Tool definitions from Langfuse prompt config
 * @returns ToolSet with Langfuse descriptions where available
 *
 * @example
 * ```typescript
 * // Create local tools (have fallback descriptions)
 * const localTools = {
 *   ...createActivityTools(toolContext),
 *   createDocument: createDocumentFactory({ ... }),
 * };
 *
 * // Override descriptions from Langfuse config
 * const tools = overrideToolDescriptions(
 *   localTools,
 *   resolvedPrompt.config.tools
 * );
 * ```
 */
export function overrideToolDescriptions(
	localTools: ToolSet,
	langfuseTools: readonly PromptToolDefinition[] | undefined,
): ToolSet {
	// If no Langfuse tools, return local tools as-is (graceful fallback)
	if (!langfuseTools || langfuseTools.length === 0) {
		return localTools;
	}

	// Build lookup for Langfuse descriptions by tool name
	const langfuseByName = new Map<string, PromptToolDefinition>();
	for (const def of langfuseTools) {
		langfuseByName.set(def.function.name, def);
	}

	// Override descriptions for matching tools
	const mergedTools: ToolSet = {};
	for (const [name, localTool] of Object.entries(localTools)) {
		const langfuseDef = langfuseByName.get(name);

		if (langfuseDef) {
			// Override description with Langfuse version
			// Tools are plain objects - spread works as expected
			mergedTools[name] = {
				...(localTool as Tool),
				description: langfuseDef.function.description,
			} as Tool;
		} else {
			// Keep local tool as-is (graceful fallback for tools not in Langfuse)
			mergedTools[name] = localTool;
		}
	}

	return mergedTools;
}

/**
 * Extract tool config from Langfuse prompt config.
 *
 * @param config - Raw config from prompt
 * @returns Typed tool config
 */
export function extractToolConfig(config: Record<string, unknown>): MergedToolConfig {
	return {
		toolChoice:
			config.toolChoice === "auto" ||
			config.toolChoice === "required" ||
			config.toolChoice === "none"
				? config.toolChoice
				: typeof config.toolChoice === "object" &&
						config.toolChoice !== null &&
						"type" in config.toolChoice
					? (config.toolChoice as { type: "tool"; toolName: string })
					: undefined,
		maxToolSteps: typeof config.maxToolSteps === "number" ? config.maxToolSteps : undefined,
	};
}
