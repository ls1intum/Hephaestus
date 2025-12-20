/**
 * Prompt Loader Tests
 *
 * Tests the prompt loader module:
 * - Langfuse integration
 * - Local fallback behavior
 * - Template compilation
 * - Cache management
 *
 * These tests ensure prompts load correctly from both sources.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ChatMessage, PromptDefinition } from "@/prompts/types";

// Mock the telemetry module before importing loader
vi.mock("@/shared/ai/telemetry", () => ({
	isTelemetryEnabled: vi.fn(() => false),
	langfuse: {
		prompt: {
			get: vi.fn(),
		},
	},
}));

import {
	clearPromptCache,
	getPromptCacheStats,
	loadPrompt,
	preloadPrompts,
} from "@/prompts/loader";
import { isTelemetryEnabled, langfuse } from "@/shared/ai/telemetry";

// ─────────────────────────────────────────────────────────────────────────────
// Test Fixtures
// ─────────────────────────────────────────────────────────────────────────────

const textPromptDefinition: PromptDefinition<"text"> = {
	name: "test-text-prompt",
	type: "text",
	prompt: "Hello {{name}}, welcome to {{project}}!",
	description: "Test text prompt",
	config: { model: "gpt-4o-mini", temperature: 0.5 },
	variables: ["name", "project"],
};

const chatPromptDefinition: PromptDefinition<"chat"> = {
	name: "test-chat-prompt",
	type: "chat",
	prompt: [
		{ role: "system", content: "You are a helpful assistant for {{project}}." },
		{ role: "user", content: "Hello, my name is {{name}}." },
	],
	description: "Test chat prompt",
	config: { model: "gpt-4o", temperature: 0.7, maxTokens: 1000 },
	variables: ["name", "project"],
};

const noConfigPromptDefinition: PromptDefinition<"text"> = {
	name: "test-no-config-prompt",
	type: "text",
	prompt: "Simple prompt without config",
};

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

describe("Prompt Loader", () => {
	beforeEach(() => {
		clearPromptCache();
		vi.clearAllMocks();
	});

	afterEach(() => {
		clearPromptCache();
	});

	describe("loadPrompt with Langfuse available", () => {
		it("should return Langfuse prompt with config", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Langfuse prompt: Hello {{name}}!",
				version: 3,
				config: { model: "gpt-4o", temperature: 0.9 },
				compile: vi.fn((vars) => `Compiled: Hello ${vars?.name}!`),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			const result = await loadPrompt(textPromptDefinition);

			expect(result.source).toBe("langfuse");
			expect(result.langfuseVersion).toBe(3);
			expect(result.langfusePrompt).toBeDefined();
			expect(result.config.model).toBe("gpt-4o");
			expect(result.config.temperature).toBe(0.9);
		});

		it("should merge Langfuse config with local config", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Langfuse prompt",
				version: 1,
				config: { temperature: 0.2 }, // Only override temperature
				compile: vi.fn(() => "Compiled"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			const result = await loadPrompt(textPromptDefinition);

			// Langfuse temperature should override local
			expect(result.config.temperature).toBe(0.2);
			// Local model should remain since Langfuse didn't provide it
			expect(result.config.model).toBe("gpt-4o-mini");
		});

		it("should call langfuse.prompt.get with correct parameters", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Test",
				version: 1,
				config: {},
				compile: vi.fn(() => "Test"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			await loadPrompt(textPromptDefinition, { label: "staging" });

			expect(langfuse.prompt.get).toHaveBeenCalledWith(
				"test-text-prompt",
				expect.objectContaining({
					label: "staging",
					type: "text",
					fallback: textPromptDefinition.prompt,
				}),
			);
		});

		it("should use version instead of label when provided", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Test",
				version: 5,
				config: {},
				compile: vi.fn(() => "Test"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			await loadPrompt(textPromptDefinition, { version: 5 });

			expect(langfuse.prompt.get).toHaveBeenCalledWith(
				"test-text-prompt",
				expect.objectContaining({
					version: 5,
				}),
			);
		});
	});

	describe("loadPrompt with Langfuse unavailable", () => {
		it("should return local fallback", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			const result = await loadPrompt(textPromptDefinition);

			expect(result.source).toBe("local");
			expect(result.langfusePrompt).toBeUndefined();
			expect(result.langfuseVersion).toBeUndefined();
			expect(result.definition).toBe(textPromptDefinition);
			expect(result.config.model).toBe("gpt-4o-mini");
		});

		it("should use local fallback when Langfuse errors", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockRejectedValue(new Error("Network error"));

			const result = await loadPrompt(textPromptDefinition);

			expect(result.source).toBe("local");
			expect(result.langfusePrompt).toBeUndefined();
		});

		it("should return empty config when definition has no config", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			const result = await loadPrompt(noConfigPromptDefinition);

			expect(result.config).toEqual({});
		});
	});

	describe("loadPrompt with missing variables", () => {
		it("should keep placeholders for missing variables in text prompts", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			const result = await loadPrompt(textPromptDefinition);
			const compiled = result.compile({ name: "Alice" }); // Missing 'project'

			expect(compiled).toBe("Hello Alice, welcome to {{project}}!");
		});

		it("should keep placeholders for missing variables in chat prompts", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			const result = await loadPrompt(chatPromptDefinition);
			const compiled = result.compile({ name: "Bob" }); // Missing 'project'

			expect(compiled).toHaveLength(2);
			expect(compiled[0]?.content).toBe("You are a helpful assistant for {{project}}.");
			expect(compiled[1]?.content).toBe("Hello, my name is Bob.");
		});
	});

	describe("compile() for text prompts", () => {
		it("should replace all variables", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			const result = await loadPrompt(textPromptDefinition);
			const compiled = result.compile({ name: "Alice", project: "Hephaestus" });

			expect(compiled).toBe("Hello Alice, welcome to Hephaestus!");
		});

		it("should handle empty variables object", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			const result = await loadPrompt(textPromptDefinition);
			const compiled = result.compile({});

			expect(compiled).toBe("Hello {{name}}, welcome to {{project}}!");
		});

		it("should handle undefined variables", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			const result = await loadPrompt(textPromptDefinition);
			const compiled = result.compile();

			expect(compiled).toBe("Hello {{name}}, welcome to {{project}}!");
		});
	});

	describe("compile() for chat prompts", () => {
		it("should replace variables in each message", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			const result = await loadPrompt(chatPromptDefinition);
			const compiled = result.compile({ name: "Charlie", project: "TestProject" });

			expect(compiled).toHaveLength(2);
			expect(compiled[0]).toEqual({
				role: "system",
				content: "You are a helpful assistant for TestProject.",
			});
			expect(compiled[1]).toEqual({
				role: "user",
				content: "Hello, my name is Charlie.",
			});
		});

		it("should preserve message roles", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			const result = await loadPrompt(chatPromptDefinition);
			const compiled = result.compile({ name: "Test", project: "Test" });

			expect(compiled).toHaveLength(2);
			expect(compiled[0]?.role).toBe("system");
			expect(compiled[1]?.role).toBe("user");
		});

		it("should use Langfuse chat messages when available", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			const langfuseMessages: ChatMessage[] = [
				{ role: "system", content: "Langfuse system for {{project}}" },
				{ role: "user", content: "Langfuse user {{name}}" },
			];
			// Mock compile to return compiled messages with variable substitution
			// The loader delegates to langfusePrompt.compile() directly
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: langfuseMessages,
				version: 2,
				config: {},
				compile: vi.fn((vars) => [
					{ role: "system", content: `Langfuse system for ${vars?.project}` },
					{ role: "user", content: `Langfuse user ${vars?.name}` },
				]),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			const result = await loadPrompt(chatPromptDefinition);
			const compiled = result.compile({ name: "Dan", project: "LangProject" });

			expect(compiled).toHaveLength(2);
			expect(compiled[0]?.content).toBe("Langfuse system for LangProject");
			expect(compiled[1]?.content).toBe("Langfuse user Dan");
		});
	});

	describe("Cache behavior", () => {
		it("should return cached prompt on second call", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Cached prompt",
				version: 1,
				config: {},
				compile: vi.fn(() => "Cached"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			// First call
			const first = await loadPrompt(textPromptDefinition);
			expect(langfuse.prompt.get).toHaveBeenCalledTimes(1);

			// Second call - should use cache
			const second = await loadPrompt(textPromptDefinition);
			expect(langfuse.prompt.get).toHaveBeenCalledTimes(1); // Not called again

			expect(first).toBe(second); // Same object reference
		});

		it("should skip cache when skipCache option is true", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Fresh prompt",
				version: 1,
				config: {},
				compile: vi.fn(() => "Fresh"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			// First call
			await loadPrompt(textPromptDefinition);
			expect(langfuse.prompt.get).toHaveBeenCalledTimes(1);

			// Second call with skipCache
			await loadPrompt(textPromptDefinition, { skipCache: true });
			expect(langfuse.prompt.get).toHaveBeenCalledTimes(2);
		});

		it("should cache local prompts too", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			const first = await loadPrompt(textPromptDefinition);
			const second = await loadPrompt(textPromptDefinition);

			expect(first).toBe(second);
		});
	});

	describe("Cache TTL", () => {
		it("should fetch fresh after TTL expires", async () => {
			vi.useFakeTimers();
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Fresh prompt",
				version: 1,
				config: {},
				compile: vi.fn(() => "Fresh"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			// First call
			await loadPrompt(textPromptDefinition);
			expect(langfuse.prompt.get).toHaveBeenCalledTimes(1);

			// Advance time by 5 minutes + 1 second (past TTL)
			vi.advanceTimersByTime(5 * 60 * 1000 + 1000);

			// Second call - should fetch fresh
			await loadPrompt(textPromptDefinition);
			expect(langfuse.prompt.get).toHaveBeenCalledTimes(2);

			vi.useRealTimers();
		});

		it("should use cache within TTL", async () => {
			vi.useFakeTimers();
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Cached prompt",
				version: 1,
				config: {},
				compile: vi.fn(() => "Cached"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			// First call
			await loadPrompt(textPromptDefinition);
			expect(langfuse.prompt.get).toHaveBeenCalledTimes(1);

			// Advance time by 4 minutes (within TTL)
			vi.advanceTimersByTime(4 * 60 * 1000);

			// Second call - should use cache
			await loadPrompt(textPromptDefinition);
			expect(langfuse.prompt.get).toHaveBeenCalledTimes(1);

			vi.useRealTimers();
		});
	});

	describe("clearPromptCache", () => {
		it("should clear all cached prompts", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Prompt to cache",
				version: 1,
				config: {},
				compile: vi.fn(() => "Cached"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			// Load and cache prompts
			await loadPrompt(textPromptDefinition);
			await loadPrompt(chatPromptDefinition);

			const statsBefore = getPromptCacheStats();
			expect(statsBefore.size).toBe(2);

			// Clear cache
			clearPromptCache();

			const statsAfter = getPromptCacheStats();
			expect(statsAfter.size).toBe(0);
			expect(statsAfter.entries).toEqual([]);
		});

		it("should force fresh fetch after cache cleared", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Fresh after clear",
				version: 1,
				config: {},
				compile: vi.fn(() => "Fresh"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			await loadPrompt(textPromptDefinition);
			expect(langfuse.prompt.get).toHaveBeenCalledTimes(1);

			clearPromptCache();

			await loadPrompt(textPromptDefinition);
			expect(langfuse.prompt.get).toHaveBeenCalledTimes(2);
		});
	});

	describe("preloadPrompts", () => {
		it("should load multiple prompts in parallel", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			await preloadPrompts([textPromptDefinition, chatPromptDefinition, noConfigPromptDefinition]);

			const stats = getPromptCacheStats();
			expect(stats.size).toBe(3);
			expect(stats.entries).toContain("test-text-prompt");
			expect(stats.entries).toContain("test-chat-prompt");
			expect(stats.entries).toContain("test-no-config-prompt");
		});

		it("should handle partial failures gracefully", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get)
				.mockResolvedValueOnce({
					prompt: "First success",
					version: 1,
					config: {},
					compile: vi.fn(() => ""),
				} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>)
				.mockRejectedValueOnce(new Error("Second fails"))
				.mockResolvedValueOnce({
					prompt: "Third success",
					version: 1,
					config: {},
					compile: vi.fn(() => ""),
				} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			// Should not throw
			await preloadPrompts([textPromptDefinition, chatPromptDefinition, noConfigPromptDefinition]);

			// Stats should show what was loaded (including fallbacks)
			const stats = getPromptCacheStats();
			expect(stats.size).toBe(3); // All loaded, some from fallback
		});
	});

	describe("getPromptCacheStats", () => {
		it("should return correct stats for empty cache", () => {
			const stats = getPromptCacheStats();

			expect(stats.size).toBe(0);
			expect(stats.entries).toEqual([]);
		});

		it("should return correct stats after loading prompts", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			await loadPrompt(textPromptDefinition);
			await loadPrompt(chatPromptDefinition);

			const stats = getPromptCacheStats();

			expect(stats.size).toBe(2);
			expect(stats.entries).toContain("test-text-prompt");
			expect(stats.entries).toContain("test-chat-prompt");
		});

		it("should update stats after cache clear", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(false);

			await loadPrompt(textPromptDefinition);
			const beforeClear = getPromptCacheStats();
			expect(beforeClear.size).toBe(1);

			clearPromptCache();
			const afterClear = getPromptCacheStats();
			expect(afterClear.size).toBe(0);
		});
	});

	describe("Config merging", () => {
		it("should use Langfuse config to override local config", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Test",
				version: 1,
				config: {
					model: "gpt-4-turbo",
					temperature: 0.1,
					customField: "langfuse-value",
				},
				compile: vi.fn(() => "Test"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			const result = await loadPrompt(textPromptDefinition);

			// Langfuse values should override
			expect(result.config.model).toBe("gpt-4-turbo");
			expect(result.config.temperature).toBe(0.1);
			expect(result.config.customField).toBe("langfuse-value");
		});

		it("should preserve local config fields not in Langfuse", async () => {
			const definitionWithExtraConfig: PromptDefinition<"text"> = {
				name: "test-extra-config",
				type: "text",
				prompt: "Test prompt",
				config: {
					model: "gpt-4o-mini",
					temperature: 0.5,
					maxTokens: 2000,
					localOnlyField: "local-value",
				},
			};

			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Test",
				version: 1,
				config: { temperature: 0.9 }, // Only override temperature
				compile: vi.fn(() => "Test"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			const result = await loadPrompt(definitionWithExtraConfig);

			// Langfuse override
			expect(result.config.temperature).toBe(0.9);
			// Local values preserved
			expect(result.config.model).toBe("gpt-4o-mini");
			expect(result.config.maxTokens).toBe(2000);
			expect(result.config.localOnlyField).toBe("local-value");
		});

		it("should handle null/undefined Langfuse config", async () => {
			vi.mocked(isTelemetryEnabled).mockReturnValue(true);
			vi.mocked(langfuse.prompt.get).mockResolvedValue({
				prompt: "Test",
				version: 1,
				config: undefined,
				compile: vi.fn(() => "Test"),
			} as unknown as Awaited<ReturnType<typeof langfuse.prompt.get>>);

			const result = await loadPrompt(textPromptDefinition);

			// Should fall back to local config entirely
			expect(result.config.model).toBe("gpt-4o-mini");
			expect(result.config.temperature).toBe(0.5);
		});
	});
});
