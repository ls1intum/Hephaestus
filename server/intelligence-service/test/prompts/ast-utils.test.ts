/**
 * AST Utils Tests
 *
 * Tests for safe AST-based code manipulation used by the prompts CLI.
 */

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
	updateParameterDescriptionInFile,
	updatePromptInFile,
	updateToolDescriptionInFile,
} from "../../scripts/ast-utils";

// ─────────────────────────────────────────────────────────────────────────────
// Test Setup
// ─────────────────────────────────────────────────────────────────────────────

let tempDir: string;

beforeEach(() => {
	tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "ast-utils-test-"));
});

afterEach(() => {
	fs.rmSync(tempDir, { recursive: true, force: true });
});

function createTempFile(name: string, content: string): string {
	const filePath = path.join(tempDir, name);
	fs.writeFileSync(filePath, content);
	return filePath;
}

// ─────────────────────────────────────────────────────────────────────────────
// updatePromptInFile Tests
// ─────────────────────────────────────────────────────────────────────────────

describe("updatePromptInFile", () => {
	it("should update a simple prompt template literal", () => {
		const file = createTempFile(
			"test.prompt.ts",
			`
import type { PromptDefinition } from "./types";

export const myPrompt: PromptDefinition = {
	name: "test",
	type: "text",
	prompt: \`Hello {{name}}\`,
};
`,
		);

		const result = updatePromptInFile(file, "Updated {{name}}!");

		expect(result.updated).toBe(true);
		expect(result.error).toBeUndefined();

		const updated = fs.readFileSync(file, "utf-8");
		expect(updated).toContain("prompt: `Updated {{name}}!`");
	});

	it("should return updated=false when content is the same", () => {
		const file = createTempFile(
			"same.prompt.ts",
			`
export const myPrompt = {
	prompt: \`Same content\`,
};
`,
		);

		const result = updatePromptInFile(file, "Same content");

		expect(result.updated).toBe(false);
		expect(result.error).toBeUndefined();
	});

	it("should handle multiline prompts", () => {
		const file = createTempFile(
			"multiline.prompt.ts",
			`
export const myPrompt = {
	prompt: \`Line 1
Line 2
Line 3\`,
};
`,
		);

		const result = updatePromptInFile(file, "New Line 1\nNew Line 2");

		expect(result.updated).toBe(true);

		const updated = fs.readFileSync(file, "utf-8");
		expect(updated).toContain("New Line 1");
		expect(updated).toContain("New Line 2");
	});

	it("should return error for missing prompt property", () => {
		const file = createTempFile(
			"no-prompt.ts",
			`
export const myConfig = {
	name: "test",
	type: "text",
};
`,
		);

		const result = updatePromptInFile(file, "New content");

		expect(result.updated).toBe(false);
		expect(result.error).toContain("Could not find");
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// updateToolDescriptionInFile Tests
// ─────────────────────────────────────────────────────────────────────────────

describe("updateToolDescriptionInFile", () => {
	it("should update defineToolMeta description", () => {
		const file = createTempFile(
			"test.tool.ts",
			`
import { defineToolMeta } from "./define-tool";
import { z } from "zod";

const { definition } = defineToolMeta({
	name: "getThing",
	description: \`Old description\`,
	inputSchema: z.object({}),
});
`,
		);

		const result = updateToolDescriptionInFile(file, "New description");

		expect(result.updated).toBe(true);
		expect(result.error).toBeUndefined();

		const updated = fs.readFileSync(file, "utf-8");
		expect(updated).toContain("description: `New description`");
	});

	it("should update defineToolMetaNoInput description", () => {
		const file = createTempFile(
			"no-input.tool.ts",
			`
import { defineToolMetaNoInput } from "./define-tool";

const { definition } = defineToolMetaNoInput({
	name: "getSummary",
	description: \`Old summary\`,
});
`,
		);

		const result = updateToolDescriptionInFile(file, "New summary");

		expect(result.updated).toBe(true);

		const updated = fs.readFileSync(file, "utf-8");
		expect(updated).toContain("description: `New summary`");
	});

	it("should return updated=false when content is the same", () => {
		const file = createTempFile(
			"same.tool.ts",
			`
const { definition } = defineToolMeta({
	name: "getThing",
	description: \`Same description\`,
	inputSchema: z.object({}),
});
`,
		);

		const result = updateToolDescriptionInFile(file, "Same description");

		expect(result.updated).toBe(false);
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// updateParameterDescriptionInFile Tests
// ─────────────────────────────────────────────────────────────────────────────

describe("updateParameterDescriptionInFile", () => {
	it("should update Zod .describe() with string literal", () => {
		const file = createTempFile(
			"param.tool.ts",
			`
const inputSchema = z.object({
	limit: z.number().min(1).max(50).describe("Old limit description"),
});
`,
		);

		const result = updateParameterDescriptionInFile(file, "limit", "New limit description");

		expect(result.updated).toBe(true);

		const updated = fs.readFileSync(file, "utf-8");
		expect(updated).toContain('.describe("New limit description")');
	});

	it("should update Zod .describe() with template literal", () => {
		const file = createTempFile(
			"template-param.tool.ts",
			`
const inputSchema = z.object({
	sinceDays: z.number().describe(\`Old days description\`),
});
`,
		);

		const result = updateParameterDescriptionInFile(file, "sinceDays", "New days description");

		expect(result.updated).toBe(true);

		const updated = fs.readFileSync(file, "utf-8");
		expect(updated).toContain("New days description");
	});

	it("should handle multiline Zod chains", () => {
		const file = createTempFile(
			"multiline-param.tool.ts",
			`
const inputSchema = z.object({
	limit: z
		.number()
		.min(1)
		.max(50)
		.describe("Old description"),
});
`,
		);

		const result = updateParameterDescriptionInFile(file, "limit", "Updated description");

		expect(result.updated).toBe(true);

		const updated = fs.readFileSync(file, "utf-8");
		expect(updated).toContain('.describe("Updated description")');
	});

	it("should return error for missing parameter", () => {
		const file = createTempFile(
			"missing-param.tool.ts",
			`
const inputSchema = z.object({
	foo: z.string(),
});
`,
		);

		const result = updateParameterDescriptionInFile(file, "bar", "New description");

		expect(result.updated).toBe(false);
		expect(result.error).toContain("bar");
	});
});
