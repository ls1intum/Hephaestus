// Override tool descriptions from prompt config; local descriptions are the fallback.

import type { Tool, ToolSet } from "ai";
import type { PromptToolDefinition } from "@/prompts/types";

export interface MergedToolConfig {
	toolChoice?: "auto" | "required" | "none" | { type: "tool"; toolName: string };
	maxToolSteps?: number;
}

/**
 * Override descriptions on local tools using prompt-config definitions.
 * Tools not in the override list are returned unchanged.
 */
export function overrideToolDescriptions(
	localTools: ToolSet,
	overrideTools: readonly PromptToolDefinition[] | undefined,
): ToolSet {
	if (!overrideTools || overrideTools.length === 0) {
		return localTools;
	}

	const overridesByName = new Map<string, PromptToolDefinition>();
	for (const def of overrideTools) {
		overridesByName.set(def.function.name, def);
	}

	const mergedTools: ToolSet = {};
	for (const [name, localTool] of Object.entries(localTools)) {
		const override = overridesByName.get(name);
		mergedTools[name] = override
			? ({ ...(localTool as Tool), description: override.function.description } as Tool)
			: localTool;
	}
	return mergedTools;
}

/** Extract toolChoice + maxToolSteps from a prompt config block. */
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
