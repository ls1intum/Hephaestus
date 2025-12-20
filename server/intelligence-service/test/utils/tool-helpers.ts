/**
 * Type-safe test utilities for AI SDK tools.
 *
 * Provides helpers that correctly match AI SDK's Tool type structure
 * while allowing flexible testing of tool behavior.
 */

import type { Tool, ToolExecutionOptions } from "ai";

/**
 * Standard AI SDK tool execution options for testing.
 */
export function createToolOptions(
	overrides: Partial<ToolExecutionOptions> = {},
): ToolExecutionOptions {
	return {
		abortSignal: new AbortController().signal,
		toolCallId: `test-${Date.now()}`,
		messages: [],
		...overrides,
	};
}

/**
 * Type-safe tool executor that handles AI SDK's Tool type correctly.
 * Parses input through the tool's schema to apply defaults (like the AI SDK does).
 *
 * @param tool - An AI SDK Tool instance
 * @param input - Input matching the tool's input schema
 * @param options - Optional execution options
 * @returns The tool's output (non-async version)
 */
export async function execTool<TInput, TOutput>(
	tool: Tool<TInput, TOutput>,
	input: TInput,
	options: ToolExecutionOptions = createToolOptions(),
): Promise<TOutput> {
	if (!tool.execute) {
		throw new Error("Tool has no execute function");
	}
	// Parse input through schema to apply defaults (like AI SDK does)
	const parsedInput = parseInputWithDefaults(tool, input);
	const result = await tool.execute(parsedInput, options);
	// AI SDK tools can return AsyncIterable for streaming, but our tools return direct values
	// This cast is safe because we know our tools don't use streaming
	return result as TOutput;
}

/**
 * Parse input through the tool's schema to apply Zod defaults.
 * AI SDK uses FlexibleSchema which can be either a Zod schema or JSON schema.
 */
function parseInputWithDefaults<TInput, TOutput>(
	tool: Tool<TInput, TOutput>,
	input: unknown,
): TInput {
	const schema = tool.inputSchema;
	if (!schema) {
		return input as TInput;
	}

	// FlexibleSchema can be ZodSchema (has parse method) or JSONSchema
	// Check if it's a Zod schema by looking for the parse method
	if ("parse" in schema && typeof schema.parse === "function") {
		try {
			return schema.parse(input);
		} catch {
			// If parsing fails, return original input and let execute handle validation
			return input as TInput;
		}
	}

	// For non-Zod schemas, return input as-is
	return input as TInput;
}

/**
 * Execute a tool with sensible test inputs.
 *
 * Since strict mode requires all parameters, this provides reasonable
 * test values for each tool type based on the tool's name in its description.
 */
export async function execToolWithDefaults<TOutput>(
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	tool: Tool<any, TOutput>,
	options: ToolExecutionOptions = createToolOptions(),
): Promise<TOutput> {
	if (!tool.execute) {
		throw new Error("Tool has no execute function");
	}

	// Determine sensible defaults based on tool description
	const desc = tool.description?.toLowerCase() ?? "";

	let defaultInput: Record<string, unknown> = {};

	if (desc.includes("pull request")) {
		defaultInput = { state: "all", limit: 10, sinceDays: 14 };
	} else if (desc.includes("issues")) {
		defaultInput = { state: "all", limit: 10 };
	} else if (desc.includes("document")) {
		defaultInput = { limit: 5 };
	} else if (desc.includes("session") || desc.includes("past mentor")) {
		defaultInput = { limit: 5 };
	} else if (desc.includes("assigned") || desc.includes("responsibilities")) {
		defaultInput = { includeReviewRequests: true };
	} else if (desc.includes("feedback")) {
		defaultInput = { sinceDays: 14, includeThreads: true };
	} else if (desc.includes("reviews") && desc.includes("given")) {
		defaultInput = { sinceDays: 14, limit: 15 };
	}
	// getActivitySummary has empty schema, so {} is fine

	// Parse through schema to validate
	const parsedInput = parseInputWithDefaults(tool, defaultInput);
	const result = await tool.execute(parsedInput, options);
	return result as TOutput;
}
