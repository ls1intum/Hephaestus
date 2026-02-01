/**
 * Define Tool Tests
 *
 * Tests for the defineToolMeta and defineToolMetaNoInput helpers.
 */

import { describe, expect, it } from "vitest";
import { z } from "zod";
import { defineToolMeta, defineToolMetaNoInput } from "@/mentor/tools/define-tool";

describe("defineToolMeta", () => {
	it("should create tool metadata with correct structure", () => {
		const inputSchema = z.object({
			limit: z.number().min(1).max(50).describe("Maximum results"),
			state: z.enum(["open", "closed", "all"]).describe("Filter by state"),
		});

		const result = defineToolMeta({
			name: "getTasks",
			description: "Get tasks from the system",
			inputSchema,
		});

		expect(result.TOOL_NAME).toBe("getTasks");
		expect(result.TOOL_DESCRIPTION).toBe("Get tasks from the system");
		expect(result.inputSchema).toBe(inputSchema);
		expect(result.definition.type).toBe("function");
		expect(result.definition.function.name).toBe("getTasks");
		expect(result.definition.function.description).toBe("Get tasks from the system");
	});

	it("should generate valid JSON Schema from Zod", () => {
		const inputSchema = z.object({
			count: z.number().min(1).max(100),
			enabled: z.boolean().optional(),
		});

		const result = defineToolMeta({
			name: "testTool",
			description: "Test tool",
			inputSchema,
		});

		const params = result.definition.function.parameters as Record<string, unknown>;

		expect(params.type).toBe("object");
		expect(params.properties).toBeDefined();
		expect(params.required).toBeDefined();

		const properties = params.properties as Record<string, Record<string, unknown>>;
		expect(properties.count?.type).toBe("number");
		expect(properties.count?.minimum).toBe(1);
		expect(properties.count?.maximum).toBe(100);
		expect(params.required).toContain("count");
		expect(params.required).not.toContain("enabled");
	});

	it("should set additionalProperties to false", () => {
		const inputSchema = z.object({
			foo: z.string(),
		});

		const result = defineToolMeta({
			name: "strictTool",
			description: "Strict tool",
			inputSchema,
		});

		const params = result.definition.function.parameters as Record<string, unknown>;
		expect(params.additionalProperties).toBe(false);
	});

	it("should handle enum types correctly", () => {
		const inputSchema = z.object({
			status: z.enum(["pending", "active", "completed"]),
		});

		const result = defineToolMeta({
			name: "enumTool",
			description: "Tool with enum",
			inputSchema,
		});

		const params = result.definition.function.parameters as Record<string, unknown>;
		const properties = params.properties as Record<string, Record<string, unknown>>;

		expect(properties.status?.enum).toEqual(["pending", "active", "completed"]);
	});

	it("should preserve description from Zod .describe()", () => {
		const inputSchema = z.object({
			days: z.number().describe("Number of days to look back"),
		});

		const result = defineToolMeta({
			name: "describedTool",
			description: "Tool with described params",
			inputSchema,
		});

		const params = result.definition.function.parameters as Record<string, unknown>;
		const properties = params.properties as Record<string, Record<string, unknown>>;

		expect(properties.days?.description).toBe("Number of days to look back");
	});
});

describe("defineToolMetaNoInput", () => {
	it("should create tool metadata for no-input tools", () => {
		const result = defineToolMetaNoInput({
			name: "getSummary",
			description: "Get a summary of activity",
		});

		expect(result.TOOL_NAME).toBe("getSummary");
		expect(result.TOOL_DESCRIPTION).toBe("Get a summary of activity");
		expect(result.definition.type).toBe("function");
		expect(result.definition.function.name).toBe("getSummary");
	});

	it("should create empty parameters object", () => {
		const result = defineToolMetaNoInput({
			name: "noInputTool",
			description: "Tool without input",
		});

		const params = result.definition.function.parameters as Record<string, unknown>;

		expect(params.type).toBe("object");
		expect(params.properties).toEqual({});
		expect(params.required).toEqual([]);
		expect(params.additionalProperties).toBe(false);
	});
});
