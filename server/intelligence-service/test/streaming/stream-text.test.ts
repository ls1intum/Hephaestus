/**
 * Streaming Behavior Tests
 *
 * Tests the ACTUAL streaming behavior of streamText with mocked models.
 * This is what AI SDK tests extensively and what we were completely missing.
 *
 * Critical tests covered:
 * - Text delta ordering and accumulation
 * - Response metadata capture
 * - onFinish callback with finishReason
 * - Error mid-stream with partial data preservation
 * - Abort signal handling with cleanup
 */

import type { LanguageModelV3, LanguageModelV3StreamPart } from "@ai-sdk/provider";
import { streamText } from "ai";
import { describe, expect, it, vi } from "vitest";
import {
	createChunkedStream,
	MockLanguageModel,
	mocks,
	toArray,
	toReadableStream,
} from "../mocks/mock-language-model";

describe("streamText behavior", () => {
	describe("textStream", () => {
		it("should emit text deltas in order", async () => {
			const model = mocks.streaming(["Hello", ", ", "world", "!"]);

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Say hello",
			});

			const chunks = await toArray(result.textStream);

			expect(chunks).toMatchInlineSnapshot(`
				[
				  "Hello",
				  ", ",
				  "world",
				  "!",
				]
			`);
		});

		it("should combine into complete text", async () => {
			const model = mocks.streaming(["The ", "quick ", "brown ", "fox"]);

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
			});

			const chunks = await toArray(result.textStream);

			expect(chunks.join("")).toBe("The quick brown fox");
		});

		it("should handle single-chunk response", async () => {
			const model = mocks.text("Complete response in one chunk");

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
			});

			const chunks = await toArray(result.textStream);

			expect(chunks).toHaveLength(1);
			expect(chunks[0]).toBe("Complete response in one chunk");
		});
	});

	describe("call tracking", () => {
		it("should track prompt passed to model", async () => {
			const model = new MockLanguageModel({
				doStream: () => Promise.resolve({ stream: createChunkedStream(["Response"]) }),
			});

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "What is 2+2?",
			});

			await toArray(result.textStream);

			expect(model.doStreamCalls).toHaveLength(1);
			expect(model.doStreamCalls[0]?.prompt).toMatchInlineSnapshot(`
				[
				  {
				    "content": [
				      {
				        "text": "What is 2+2?",
				        "type": "text",
				      },
				    ],
				    "providerOptions": undefined,
				    "role": "user",
				  },
				]
			`);
		});

		it("should track system prompt separately", async () => {
			const model = new MockLanguageModel({
				doStream: () => Promise.resolve({ stream: createChunkedStream(["Response"]) }),
			});

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				system: "You are a helpful assistant",
				prompt: "Hello",
			});

			await toArray(result.textStream);

			expect(model.doStreamCalls[0]?.prompt).toMatchInlineSnapshot(`
				[
				  {
				    "content": "You are a helpful assistant",
				    "role": "system",
				  },
				  {
				    "content": [
				      {
				        "text": "Hello",
				        "type": "text",
				      },
				    ],
				    "providerOptions": undefined,
				    "role": "user",
				  },
				]
			`);
		});

		it("should track multiple messages in conversation", async () => {
			const model = new MockLanguageModel({
				doStream: () => Promise.resolve({ stream: createChunkedStream(["Response"]) }),
			});

			// Using the messages array format directly as the AI SDK expects
			const result = streamText({
				model: model as unknown as LanguageModelV3,
				messages: [
					{ role: "user", content: "Hello" },
					{ role: "assistant", content: "Hi!" },
					{ role: "user", content: "How are you?" },
				],
			});

			await toArray(result.textStream);

			expect(model.doStreamCalls[0]?.prompt).toHaveLength(3);
		});
	});

	describe("onFinish callback", () => {
		it("should call onFinish with complete text", async () => {
			const model = mocks.streaming(["Hello", " world"]);
			const onFinish = vi.fn();

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				onFinish,
			});

			await toArray(result.textStream);

			// onFinish is called async, wait a tick
			await new Promise((r) => setTimeout(r, 10));

			expect(onFinish).toHaveBeenCalledTimes(1);
			expect(onFinish).toHaveBeenCalledWith(
				expect.objectContaining({
					text: "Hello world",
					finishReason: "stop",
				}),
			);
		});

		it("should include usage in onFinish", async () => {
			const model = mocks.text("Short response");
			const onFinish = vi.fn();

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				onFinish,
			});

			await toArray(result.textStream);
			await new Promise((r) => setTimeout(r, 10));

			expect(onFinish).toHaveBeenCalledWith(
				expect.objectContaining({
					usage: expect.objectContaining({
						inputTokens: expect.any(Number),
						outputTokens: expect.any(Number),
					}),
				}),
			);
		});

		it("should capture finishReason correctly", async () => {
			// Create model with custom finish reason
			const customParts: LanguageModelV3StreamPart[] = [
				{ type: "stream-start", warnings: [] },
				{ type: "text-start", id: "1" },
				{ type: "text-delta", id: "1", delta: "Response" },
				{ type: "text-end", id: "1" },
				{
					type: "finish",
					finishReason: "length",
					usage: {
						inputTokens: { total: 10, noCache: 10, cacheRead: undefined, cacheWrite: undefined },
						outputTokens: { total: 5, text: 5, reasoning: undefined },
					},
				},
			];

			const model = new MockLanguageModel({
				doStream: () => Promise.resolve({ stream: toReadableStream(customParts) }),
			});

			const onFinish = vi.fn();

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				onFinish,
			});

			await toArray(result.textStream);
			await new Promise((r) => setTimeout(r, 10));

			expect(onFinish).toHaveBeenCalledWith(
				expect.objectContaining({
					finishReason: "length",
				}),
			);
		});
	});

	describe("response metadata", () => {
		it("should capture response metadata in fullStream", async () => {
			const timestamp = new Date("2024-01-15T12:00:00Z");
			const customParts: LanguageModelV3StreamPart[] = [
				{ type: "stream-start", warnings: [] },
				{
					type: "response-metadata",
					id: "response-123",
					modelId: "gpt-4-turbo",
					timestamp,
				},
				{ type: "text-start", id: "1" },
				{ type: "text-delta", id: "1", delta: "Hello" },
				{ type: "text-end", id: "1" },
				{
					type: "finish",
					finishReason: "stop",
					usage: {
						inputTokens: { total: 10, noCache: 10, cacheRead: undefined, cacheWrite: undefined },
						outputTokens: { total: 5, text: 5, reasoning: undefined },
					},
				},
			];

			const model = new MockLanguageModel({
				doStream: () => Promise.resolve({ stream: toReadableStream(customParts) }),
			});

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
			});

			// Consume the stream to get response
			await toArray(result.textStream);

			// Check response property for metadata
			const response = await result.response;
			expect(response.id).toBe("response-123");
			expect(response.modelId).toBe("gpt-4-turbo");
			expect(response.timestamp).toEqual(timestamp);
		});

		it("should include metadata in onFinish response", async () => {
			const timestamp = new Date("2024-01-15T12:00:00Z");
			const customParts: LanguageModelV3StreamPart[] = [
				{ type: "stream-start", warnings: [] },
				{
					type: "response-metadata",
					id: "meta-456",
					modelId: "claude-3",
					timestamp,
				},
				{ type: "text-start", id: "1" },
				{ type: "text-delta", id: "1", delta: "Response" },
				{ type: "text-end", id: "1" },
				{
					type: "finish",
					finishReason: "stop",
					usage: {
						inputTokens: { total: 10, noCache: 10, cacheRead: undefined, cacheWrite: undefined },
						outputTokens: { total: 5, text: 5, reasoning: undefined },
					},
				},
			];

			const model = new MockLanguageModel({
				doStream: () => Promise.resolve({ stream: toReadableStream(customParts) }),
			});

			const onFinish = vi.fn();

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				onFinish,
			});

			await toArray(result.textStream);
			await new Promise((r) => setTimeout(r, 10));

			expect(onFinish).toHaveBeenCalledWith(
				expect.objectContaining({
					response: expect.objectContaining({
						id: "meta-456",
						modelId: "claude-3",
					}),
				}),
			);
		});
	});

	describe("error handling mid-stream", () => {
		it("should propagate error to consumer", async () => {
			const model = mocks.error("Fatal error");

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				onError: () => {
					// void
				},
			});

			await expect(async () => {
				await toArray(result.textStream);
			}).rejects.toThrow("Fatal error");
		});

		it("should preserve partial data before error", async () => {
			const model = mocks.error("Mid-stream failure");

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				onError: () => {
					// Silence error
				},
			});

			const chunks: string[] = [];
			try {
				for await (const chunk of result.textStream) {
					chunks.push(chunk);
				}
			} catch {
				// Expected to throw after partial data
			}

			// The error stream emits "Starting..." before erroring
			expect(chunks).toContain("Starting...");
		});

		it("should reject before error but preserve partial chunks", async () => {
			// More explicit test: verify we get partial data then error
			const model = mocks.error("Interrupted");

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				onError: () => {
					// void
				},
			});

			const reader = result.textStream[Symbol.asyncIterator]();

			// First chunk should succeed (partial data)
			const first = await reader.next();
			expect(first.done).toBe(false);
			expect(first.value).toBe("Starting...");

			// Second read should throw
			await expect(reader.next()).rejects.toThrow("Interrupted");
		});
	});

	describe("abort handling", () => {
		it("should respect abort signal", async () => {
			let wasAborted = false;
			const model = mocks.abortable(() => {
				wasAborted = true;
			});

			const controller = new AbortController();

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				abortSignal: controller.signal,
				onError: () => {
					// void return
				},
			});

			// Start consuming
			const reader = result.textStream[Symbol.asyncIterator]();
			await reader.next(); // Get first chunk

			// Abort mid-stream
			controller.abort();

			expect(wasAborted).toBe(true);
		});

		it("should complete stream early when aborted", async () => {
			const model = mocks.abortable(() => {
				// no-op
			});
			const controller = new AbortController();

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				abortSignal: controller.signal,
				onError: () => {
					// void
				},
			});

			// Start consuming
			const reader = result.textStream[Symbol.asyncIterator]();
			await reader.next();

			// Abort
			controller.abort();

			// Trying to read should either throw or return done=true (graceful abort)
			const nextResult = await reader.next().catch(() => ({ done: true, value: undefined }));
			expect(nextResult.done).toBe(true);
		});

		it("should preserve partial data when aborted", async () => {
			const model = mocks.abortable(() => {
				// no-op
			});
			const controller = new AbortController();

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				abortSignal: controller.signal,
				onError: () => {
					// void
				},
			});

			const chunks: string[] = [];
			const reader = result.textStream[Symbol.asyncIterator]();

			// Get the first chunk before abort
			const first = await reader.next();
			if (!first.done && first.value) {
				chunks.push(first.value);
			}

			// Abort
			controller.abort();

			// Verify we got partial data
			expect(chunks).toContain("Hello");
		});

		it("should stop model processing on abort", async () => {
			let processingStarted = false;
			let processingStopped = false;

			const model = mocks.abortable(() => {
				processingStopped = true;
			});

			// Track that doStream was called
			const originalDoStream = model.doStream.bind(model);
			model.doStream = (options) => {
				processingStarted = true;
				return originalDoStream(options);
			};

			const controller = new AbortController();

			const result = streamText({
				model: model as unknown as LanguageModelV3,
				prompt: "Test",
				abortSignal: controller.signal,
				onError: () => {
					// void
				},
			});

			const reader = result.textStream[Symbol.asyncIterator]();
			await reader.next();

			expect(processingStarted).toBe(true);

			controller.abort();

			expect(processingStopped).toBe(true);
		});
	});
});

describe("fullStream", () => {
	it("should include all stream parts", async () => {
		const model = mocks.streaming(["A", "B"]);

		const result = streamText({
			model: model as unknown as LanguageModelV3,
			prompt: "Test",
		});

		const parts = await toArray(result.fullStream);
		const types = parts.map((p) => p.type);

		expect(types).toContain("start");
		expect(types).toContain("text-delta");
		expect(types).toContain("finish");
	});

	it("should emit text deltas in fullStream format", async () => {
		const model = mocks.streaming(["Hello", ", ", "world!"]);

		const result = streamText({
			model: model as unknown as LanguageModelV3,
			prompt: "Test",
		});

		const parts = await toArray(result.fullStream);
		const textDeltas = parts.filter((p) => p.type === "text-delta");

		// Verify we get the expected text values
		expect(
			textDeltas.map((d) => ("textDelta" in d ? d.textDelta : "text" in d ? d.text : "")),
		).toEqual(["Hello", ", ", "world!"]);
	});

	it("should include finish part with finishReason", async () => {
		const model = mocks.streaming(["Test"]);

		const result = streamText({
			model: model as unknown as LanguageModelV3,
			prompt: "Test",
		});

		const parts = await toArray(result.fullStream);
		const finishPart = parts.find((p) => p.type === "finish");

		expect(finishPart).toMatchObject({
			type: "finish",
			finishReason: "stop",
		});
	});

	it("should include usage information accessible via result.usage", async () => {
		const model = mocks.streaming(["Test"]);

		const result = streamText({
			model: model as unknown as LanguageModelV3,
			prompt: "Test",
		});

		// Consume the stream
		await toArray(result.textStream);

		// Usage is available on the result object
		const usage = await result.usage;
		expect(usage).toMatchObject({
			inputTokens: expect.any(Number),
			outputTokens: expect.any(Number),
		});
	});
});
