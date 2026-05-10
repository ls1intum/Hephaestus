/**
 * Generate AI SDK tool config + prompt-config definition from one Zod schema.
 * Eliminates drift between the runtime schema and the prompt-config JSON Schema.
 */

import { type ZodTypeAny, z } from "zod";
import type { PromptToolDefinition } from "@/prompts/types";

export interface ToolMetaInput<T extends ZodTypeAny> {
	name: string;
	description: string;
	inputSchema: T;
}

export interface ToolMeta<T extends ZodTypeAny> {
	TOOL_NAME: string;
	TOOL_DESCRIPTION: string;
	inputSchema: T;
	definition: PromptToolDefinition;
}

/** Tool metadata from a single Zod source of truth. */
export function defineToolMeta<T extends ZodTypeAny>(input: ToolMetaInput<T>): ToolMeta<T> {
	const { name, description, inputSchema } = input;
	const jsonSchema = z.toJSONSchema(inputSchema) as Record<string, unknown>;
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
				parameters: { ...parameters, additionalProperties: false },
			},
		},
	};
}

/** Like {@link defineToolMeta} but for tools with no input. */
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
				parameters: { type: "object", properties: {}, required: [], additionalProperties: false },
			},
		},
	};
}
