/**
 * Tool Merger Tests
 *
 * Tests for merging Langfuse-managed tool descriptions with local executors.
 */

import { tool } from "ai";
import { describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { extractToolConfig, overrideToolDescriptions } from "@/mentor/tools/merger";
import type { PromptToolDefinition } from "@/prompts/types";

// ─────────────────────────────────────────────────────────────────────────────
// Test Fixtures
// ─────────────────────────────────────────────────────────────────────────────

function createMockTool(description: string) {
	return tool({
		description,
		inputSchema: z.object({ query: z.string() }),
		execute: async ({ query }) => ({ result: query }),
	});
}

const mockLangfuseTools: PromptToolDefinition[] = [
	{
		type: "function",
		function: {
			name: "search",
			description: "Langfuse-managed search description",
			parameters: { type: "object", properties: { query: { type: "string" } } },
		},
	},
	{
		type: "function",
		function: {
			name: "fetch",
			description: "Langfuse-managed fetch description",
			parameters: { type: "object", properties: { url: { type: "string" } } },
		},
	},
];

// ─────────────────────────────────────────────────────────────────────────────
// overrideToolDescriptions Tests
// ─────────────────────────────────────────────────────────────────────────────

describe("overrideToolDescriptions", () => {
	describe("with Langfuse tools", () => {
		it("should override description for matching tools", () => {
			const localTools = {
				search: createMockTool("Local search description"),
			};

			const result = overrideToolDescriptions(localTools, mockLangfuseTools);

			expect(result.search).toBeDefined();
			expect(result.search?.description).toBe("Langfuse-managed search description");
		});

		it("should preserve local tool for non-matching tools", () => {
			const localTools = {
				unknown: createMockTool("Unknown tool description"),
			};

			const result = overrideToolDescriptions(localTools, mockLangfuseTools);

			expect(result.unknown).toBeDefined();
			expect(result.unknown?.description).toBe("Unknown tool description");
		});

		it("should handle mix of matching and non-matching tools", () => {
			const localTools = {
				search: createMockTool("Local search"),
				custom: createMockTool("Custom tool"),
			};

			const result = overrideToolDescriptions(localTools, mockLangfuseTools);

			expect(result.search?.description).toBe("Langfuse-managed search description");
			expect(result.custom?.description).toBe("Custom tool");
		});

		it("should override multiple matching tools", () => {
			const localTools = {
				search: createMockTool("Local search"),
				fetch: createMockTool("Local fetch"),
			};

			const result = overrideToolDescriptions(localTools, mockLangfuseTools);

			expect(result.search?.description).toBe("Langfuse-managed search description");
			expect(result.fetch?.description).toBe("Langfuse-managed fetch description");
		});
	});

	describe("graceful fallback", () => {
		it("should return local tools unchanged when langfuseTools is undefined", () => {
			const localTools = {
				search: createMockTool("Local search"),
			};

			const result = overrideToolDescriptions(localTools, undefined);

			expect(result).toBe(localTools);
		});

		it("should return local tools unchanged when langfuseTools is empty", () => {
			const localTools = {
				search: createMockTool("Local search"),
			};

			const result = overrideToolDescriptions(localTools, []);

			expect(result).toBe(localTools);
		});
	});

	describe("tool preservation", () => {
		it("should preserve execute function when overriding description", () => {
			const execute = vi.fn().mockResolvedValue({ result: "test" });
			const localTools = {
				search: tool({
					description: "Local",
					inputSchema: z.object({ query: z.string() }),
					execute,
				}),
			};

			const result = overrideToolDescriptions(localTools, mockLangfuseTools);

			// Verify execute function is preserved
			expect(result.search?.execute).toBe(execute);

			// Verify description is overridden
			expect(result.search?.description).toBe("Langfuse-managed search description");
		});

		it("should preserve inputSchema when overriding description", () => {
			const schema = z.object({ query: z.string().min(1) });
			const localTools = {
				search: tool({
					description: "Local",
					inputSchema: schema,
					execute: async () => ({}),
				}),
			};

			const result = overrideToolDescriptions(localTools, mockLangfuseTools);

			expect(result.search?.inputSchema).toBe(schema);
		});
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// extractToolConfig Tests
// ─────────────────────────────────────────────────────────────────────────────

describe("extractToolConfig", () => {
	describe("toolChoice extraction", () => {
		it("should extract 'auto' toolChoice", () => {
			const config = extractToolConfig({ toolChoice: "auto" });
			expect(config.toolChoice).toBe("auto");
		});

		it("should extract 'required' toolChoice", () => {
			const config = extractToolConfig({ toolChoice: "required" });
			expect(config.toolChoice).toBe("required");
		});

		it("should extract 'none' toolChoice", () => {
			const config = extractToolConfig({ toolChoice: "none" });
			expect(config.toolChoice).toBe("none");
		});

		it("should extract specific tool choice", () => {
			const config = extractToolConfig({
				toolChoice: { type: "tool", toolName: "search" },
			});
			expect(config.toolChoice).toEqual({ type: "tool", toolName: "search" });
		});

		it("should return undefined for invalid toolChoice", () => {
			const config = extractToolConfig({ toolChoice: "invalid" });
			expect(config.toolChoice).toBeUndefined();
		});

		it("should return undefined when toolChoice is missing", () => {
			const config = extractToolConfig({});
			expect(config.toolChoice).toBeUndefined();
		});
	});

	describe("maxToolSteps extraction", () => {
		it("should extract maxToolSteps number", () => {
			const config = extractToolConfig({ maxToolSteps: 5 });
			expect(config.maxToolSteps).toBe(5);
		});

		it("should return undefined for non-number maxToolSteps", () => {
			const config = extractToolConfig({ maxToolSteps: "5" });
			expect(config.maxToolSteps).toBeUndefined();
		});

		it("should return undefined when maxToolSteps is missing", () => {
			const config = extractToolConfig({});
			expect(config.maxToolSteps).toBeUndefined();
		});
	});

	describe("full config extraction", () => {
		it("should extract both toolChoice and maxToolSteps", () => {
			const config = extractToolConfig({
				toolChoice: "auto",
				maxToolSteps: 10,
			});

			expect(config.toolChoice).toBe("auto");
			expect(config.maxToolSteps).toBe(10);
		});

		it("should ignore unrelated config properties", () => {
			const config = extractToolConfig({
				toolChoice: "auto",
				maxToolSteps: 5,
				temperature: 0.7,
				model: "gpt-4",
			});

			expect(config.toolChoice).toBe("auto");
			expect(config.maxToolSteps).toBe(5);
			expect(Object.keys(config)).toEqual(["toolChoice", "maxToolSteps"]);
		});
	});
});
