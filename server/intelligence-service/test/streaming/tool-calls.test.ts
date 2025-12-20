/**
 * Tool Call Streaming Tests
 *
 * Tests tool call behavior in streaming responses using official AI SDK mocks.
 * The mentor heavily uses tools (createDocument, getIssues, etc.)
 * so this is critical functionality to verify.
 *
 * @see https://ai-sdk.dev/docs/ai-sdk-core/testing
 */

import { streamText, tool } from "ai";
import { describe, expect, it } from "vitest";
import { z } from "zod";
import {
	convertArrayToReadableStream,
	createToolCallStreamParts,
	MockLanguageModelV3,
	mockModels,
	toArray,
} from "../mocks";

describe("tool call streaming", () => {
	describe("single tool call", () => {
		it("should emit tool-call part in fullStream", async () => {
			const model = mockModels.toolCalls([
				{ id: "call-1", name: "getWeather", args: { location: "Paris" } },
			]);

			const result = streamText({
				model,
				prompt: "What's the weather in Paris?",
			});

			const parts = await toArray(result.fullStream);
			const toolCallPart = parts.find((p) => p.type === "tool-call");

			expect(toolCallPart).toBeDefined();
			expect(toolCallPart?.type).toBe("tool-call");
		});

		it("should capture tool call in stream", async () => {
			const model = mockModels.toolCalls([
				{
					id: "call-2",
					name: "createDocument",
					args: { title: "My Doc", content: "Hello world" },
				},
			]);

			const result = streamText({
				model,
				prompt: "Create a document",
			});

			const parts = await toArray(result.fullStream);
			const toolCallPart = parts.find((p) => p.type === "tool-call");

			expect(toolCallPart).toBeDefined();
			expect(toolCallPart?.type).toBe("tool-call");
		});

		it("should set finishReason to tool-calls", async () => {
			const model = mockModels.toolCalls([{ id: "call-3", name: "getIssues", args: {} }]);

			const result = streamText({
				model,
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
			const model = mockModels.toolCalls([
				{ id: "call-a", name: "getIssues", args: { repo: "main" } },
				{ id: "call-b", name: "getPullRequests", args: { state: "open" } },
			]);

			const result = streamText({
				model,
				prompt: "Get issues and PRs",
			});

			const parts = await toArray(result.fullStream);
			const toolCalls = parts.filter((p) => p.type === "tool-call");

			expect(toolCalls).toHaveLength(2);
		});
	});

	describe("mixed text and tool calls", () => {
		it("should emit both text and tool-call parts", async () => {
			const model = mockModels.mixed("Let me check that for you.", {
				id: "call-x",
				name: "getIssueDetails",
				args: { issueNumber: 42 },
			});

			const result = streamText({
				model,
				prompt: "Tell me about issue #42",
			});

			const parts = await toArray(result.fullStream);

			const textParts = parts.filter((p) => p.type === "text-delta");
			const toolCallParts = parts.filter((p) => p.type === "tool-call");

			expect(textParts.length).toBeGreaterThan(0);
			expect(toolCallParts).toHaveLength(1);
		});

		it("should combine text from textStream even with tool calls", async () => {
			const model = mockModels.mixed("Processing your request...", {
				id: "call-y",
				name: "updateDocument",
				args: { id: "doc-1" },
			});

			const result = streamText({
				model,
				prompt: "Update the document",
			});

			const chunks = await toArray(result.textStream);
			const fullText = chunks.join("");

			expect(fullText).toBe("Processing your request...");
		});
	});

	describe("tool call tracking", () => {
		it("should track tool configuration passed to model", async () => {
			const model = new MockLanguageModelV3({
				doStream: async () => ({
					stream: convertArrayToReadableStream(
						createToolCallStreamParts([{ id: "call-z", name: "testTool", args: {} }]),
					),
				}),
			});

			const mockTool = tool({
				description: "A test tool",
				inputSchema: z.object({}),
			});

			const result = streamText({
				model,
				prompt: "Use the tool",
				tools: { testTool: mockTool },
			});

			await toArray(result.fullStream);

			expect(model.doStreamCalls).toHaveLength(1);
			expect(model.doStreamCalls[0]?.tools).toBeDefined();
		});
	});
});
