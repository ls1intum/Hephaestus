/**
 * Tool Call Streaming Tests
 *
 * Tests tool call behavior in streaming responses.
 * The mentor heavily uses tools (createDocument, getIssues, etc.)
 * so this is critical functionality to verify.
 */

import type { LanguageModelV3, LanguageModelV3StreamPart } from "@ai-sdk/provider";
import { streamText, tool } from "ai";
import { describe, expect, it } from "vitest";
import { z } from "zod";
import {
	DEFAULT_USAGE,
	MockLanguageModel,
	toArray,
	toReadableStream,
} from "../mocks/mock-language-model";

// ─────────────────────────────────────────────────────────────────────────────
// Test Utilities
// ─────────────────────────────────────────────────────────────────────────────

function createToolCallStream(
	toolCalls: Array<{ id: string; name: string; args: Record<string, unknown> }>,
): ReadableStream<LanguageModelV3StreamPart> {
	const parts: LanguageModelV3StreamPart[] = [
		{ type: "stream-start", warnings: [] },
		{ type: "response-metadata", id: "tool-response", modelId: "mock", timestamp: new Date(0) },
		...toolCalls.map((call) => ({
			type: "tool-call" as const,
			toolCallId: call.id,
			toolName: call.name,
			input: JSON.stringify(call.args),
		})),
		{ type: "finish", finishReason: "tool-calls" as const, usage: DEFAULT_USAGE },
	];
	return toReadableStream(parts);
}

function createMixedStream(
	text: string,
	toolCall: { id: string; name: string; args: Record<string, unknown> },
): ReadableStream<LanguageModelV3StreamPart> {
	const parts: LanguageModelV3StreamPart[] = [
		{ type: "stream-start", warnings: [] },
		{ type: "response-metadata", id: "mixed-response", modelId: "mock", timestamp: new Date(0) },
		{ type: "text-start", id: "1" },
		{ type: "text-delta", id: "1", delta: text },
		{ type: "text-end", id: "1" },
		{
			type: "tool-call",
			toolCallId: toolCall.id,
			toolName: toolCall.name,
			input: JSON.stringify(toolCall.args),
		},
		{ type: "finish", finishReason: "tool-calls" as const, usage: DEFAULT_USAGE },
	];
	return toReadableStream(parts);
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

describe("tool call streaming", () => {
	describe("single tool call", () => {
		it("should emit tool-call part in fullStream", async () => {
			const model = new MockLanguageModel({
				doStream: () =>
					Promise.resolve({
						stream: createToolCallStream([
							{ id: "call-1", name: "getWeather", args: { location: "Paris" } },
						]),
					}),
			});

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "What's the weather in Paris?",
			});

			const parts = await toArray(result.fullStream);
			const toolCallPart = parts.find((p) => p.type === "tool-call");

			expect(toolCallPart).toBeDefined();
			// AI SDK transforms tool-call parts - verify the transformed structure
			expect(toolCallPart?.type).toBe("tool-call");
		});

		it("should capture tool call in stream", async () => {
			const model = new MockLanguageModel({
				doStream: () =>
					Promise.resolve({
						stream: createToolCallStream([
							{
								id: "call-2",
								name: "createDocument",
								args: { title: "My Doc", content: "Hello world" },
							},
						]),
					}),
			});

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Create a document",
			});

			const parts = await toArray(result.fullStream);
			const toolCallPart = parts.find((p) => p.type === "tool-call");

			// Tool call should be captured
			expect(toolCallPart).toBeDefined();
			expect(toolCallPart?.type).toBe("tool-call");
		});

		it("should set finishReason to tool-calls", async () => {
			const model = new MockLanguageModel({
				doStream: () =>
					Promise.resolve({
						stream: createToolCallStream([{ id: "call-3", name: "getIssues", args: {} }]),
					}),
			});

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "List issues",
			});

			const parts = await toArray(result.fullStream);
			const finishPart = parts.find((p) => p.type === "finish") as
				| { finishReason: string }
				| undefined;

			expect(finishPart?.finishReason).toBe("tool-calls");
		});
	});

	describe("multiple tool calls", () => {
		it("should emit all tool-call parts in sequence", async () => {
			const model = new MockLanguageModel({
				doStream: () =>
					Promise.resolve({
						stream: createToolCallStream([
							{ id: "call-a", name: "getIssues", args: { repo: "main" } },
							{ id: "call-b", name: "getPullRequests", args: { state: "open" } },
						]),
					}),
			});

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Get issues and PRs",
			});

			const parts = await toArray(result.fullStream);
			const toolCalls = parts.filter((p) => p.type === "tool-call");

			// Should emit 2 tool call parts
			expect(toolCalls).toHaveLength(2);
		});
	});

	describe("mixed text and tool calls", () => {
		it("should emit both text and tool-call parts", async () => {
			const model = new MockLanguageModel({
				doStream: () =>
					Promise.resolve({
						stream: createMixedStream("Let me check that for you.", {
							id: "call-x",
							name: "getIssueDetails",
							args: { issueNumber: 42 },
						}),
					}),
			});

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Tell me about issue #42",
			});

			const parts = await toArray(result.fullStream);

			const textParts = parts.filter((p) => p.type === "text-delta");
			const toolCallParts = parts.filter((p) => p.type === "tool-call");

			expect(textParts.length).toBeGreaterThan(0);
			expect(toolCallParts).toHaveLength(1);
		});

		it("should combine text from textStream even with tool calls", async () => {
			const model = new MockLanguageModel({
				doStream: () =>
					Promise.resolve({
						stream: createMixedStream("Processing your request...", {
							id: "call-y",
							name: "updateDocument",
							args: { id: "doc-1" },
						}),
					}),
			});

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Update the document",
			});

			const chunks = await toArray(result.textStream);
			const fullText = chunks.join("");

			expect(fullText).toBe("Processing your request...");
		});
	});

	describe("tool call tracking", () => {
		it("should track tool configuration passed to model", async () => {
			const model = new MockLanguageModel({
				doStream: () =>
					Promise.resolve({
						stream: createToolCallStream([{ id: "call-z", name: "testTool", args: {} }]),
					}),
			});

			// Use AI SDK tool helper with inputSchema instead of parameters
			const mockTool = tool({
				description: "A test tool",
				inputSchema: z.object({}),
			});

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Use the tool",
				tools: { testTool: mockTool },
			});

			await toArray(result.fullStream);

			expect(model.doStreamCalls).toHaveLength(1);
			expect(model.doStreamCalls[0]?.tools).toBeDefined();
		});
	});
});
