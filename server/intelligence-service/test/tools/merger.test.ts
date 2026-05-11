// Tests for overrideToolDescriptions + extractToolConfig.

import { tool } from "ai";
import { describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { extractToolConfig, overrideToolDescriptions } from "@/mentor/tools/merger";
import type { PromptToolDefinition } from "@/prompts/types";

function createMockTool(description: string) {
	return tool({
		description,
		inputSchema: z.object({ query: z.string() }),
		execute: async ({ query }) => ({ result: query }),
	});
}

const overrides: PromptToolDefinition[] = [
	{
		type: "function",
		function: {
			name: "search",
			description: "prompt-config search description",
			parameters: { type: "object", properties: { query: { type: "string" } } },
		},
	},
	{
		type: "function",
		function: {
			name: "fetch",
			description: "prompt-config fetch description",
			parameters: { type: "object", properties: { url: { type: "string" } } },
		},
	},
];

describe("overrideToolDescriptions", () => {
	it("overrides matching tools and leaves the rest unchanged", () => {
		const local = {
			search: createMockTool("Local search"),
			custom: createMockTool("Custom tool"),
		};
		const result = overrideToolDescriptions(local, overrides);
		expect(result.search?.description).toBe("prompt-config search description");
		expect(result.custom?.description).toBe("Custom tool");
	});

	it.each([
		["undefined", undefined],
		["empty array", []],
	])("returns the same instance when overrides is %s (graceful fallback)", (_, value) => {
		const local = { search: createMockTool("Local search") };
		expect(overrideToolDescriptions(local, value)).toBe(local);
	});

	it("preserves execute + inputSchema when overriding description", () => {
		const execute = vi.fn().mockResolvedValue({ result: "test" });
		const inputSchema = z.object({ query: z.string().min(1) });
		const local = { search: tool({ description: "Local", inputSchema, execute }) };
		const result = overrideToolDescriptions(local, overrides);
		expect(result.search?.execute).toBe(execute);
		expect(result.search?.inputSchema).toBe(inputSchema);
		expect(result.search?.description).toBe("prompt-config search description");
	});
});

describe("extractToolConfig", () => {
	it("passes through a specific tool choice ({type:'tool'})", () => {
		expect(
			extractToolConfig({ toolChoice: { type: "tool", toolName: "search" } }).toolChoice,
		).toEqual({ type: "tool", toolName: "search" });
	});

	it("returns undefined for an invalid toolChoice string", () => {
		expect(extractToolConfig({ toolChoice: "invalid" }).toolChoice).toBeUndefined();
	});

	it("rejects non-number maxToolSteps", () => {
		expect(extractToolConfig({ maxToolSteps: "5" }).maxToolSteps).toBeUndefined();
	});

	it("ignores unrelated config properties (only toolChoice + maxToolSteps survive)", () => {
		const config = extractToolConfig({
			toolChoice: "auto",
			maxToolSteps: 5,
			temperature: 0.7,
			model: "gpt-4",
		});
		expect(Object.keys(config)).toEqual(["toolChoice", "maxToolSteps"]);
	});
});
