/**
 * Tool Definition Helper
 *
 * Single source of truth for tool metadata.
 * Generates both AI SDK tool config AND Langfuse definition from Zod schema.
 *
 * This eliminates schema drift between runtime (Zod) and Langfuse (JSON Schema).
 *
 * @example
 * ```ts
 * const inputSchema = z.object({ state: z.enum(["open", "merged"]) });
 *
 * const { definition, TOOL_DESCRIPTION } = defineToolMeta({
 *   name: "getPullRequests",
 *   description: `...`,
 *   inputSchema,
 * });
 *
 * // Use in tool factory
 * tool({ description: TOOL_DESCRIPTION, inputSchema, ... });
 *
 * // Export for Langfuse
 * export { definition as getPullRequestsDefinition };
 * ```
 */

import { type ZodTypeAny, z } from "zod";
import type { PromptToolDefinition } from "@/prompts/types";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export interface ToolMetaInput<T extends ZodTypeAny> {
	/** Tool name (camelCase, e.g., "getPullRequests") */
	name: string;
	/** Tool description with usage guidance */
	description: string;
	/** Zod schema for input parameters */
	inputSchema: T;
}

export interface ToolMeta<T extends ZodTypeAny> {
	/** Tool name constant */
	TOOL_NAME: string;
	/** Tool description constant */
	TOOL_DESCRIPTION: string;
	/** Zod input schema (for AI SDK) */
	inputSchema: T;
	/** Langfuse tool definition (generated from Zod) */
	definition: PromptToolDefinition;
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Define tool metadata with a single source of truth.
 *
 * - Input schema is defined once as Zod
 * - JSON Schema for Langfuse is auto-generated using Zod v4's native support
 * - No drift possible between runtime and Langfuse
 */
export function defineToolMeta<T extends ZodTypeAny>(input: ToolMetaInput<T>): ToolMeta<T> {
	const { name, description, inputSchema } = input;

	// Use Zod v4's native JSON Schema conversion
	const jsonSchema = z.toJSONSchema(inputSchema) as Record<string, unknown>;

	// Remove $schema key (not needed for Langfuse)
	const { $schema: _, ...parameters } = jsonSchema;

	return {
		TOOL_NAME: name,
		TOOL_DESCRIPTION: description,
		inputSchema,
		definition: {
			type: "function",
			function: {
				name,
				description,
				parameters: {
					...parameters,
					additionalProperties: false,
				},
			},
		},
	};
}

/**
 * Define tool metadata for a tool with no input parameters.
 */
export function defineToolMetaNoInput(
	input: Omit<ToolMetaInput<ZodTypeAny>, "inputSchema">,
): Omit<ToolMeta<never>, "inputSchema"> {
	const { name, description } = input;

	return {
		TOOL_NAME: name,
		TOOL_DESCRIPTION: description,
		definition: {
			type: "function",
			function: {
				name,
				description,
				parameters: {
					type: "object",
					properties: {},
					required: [],
					additionalProperties: false,
				},
			},
		},
	};
}
