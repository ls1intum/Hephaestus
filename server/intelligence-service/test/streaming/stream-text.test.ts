/**
 * Streaming Behavior Tests
 *
 * Tests the ACTUAL streaming behavior of streamText with mocked models.
 * Uses official AI SDK test utilities from ai/test for maximum compatibility.
 *
 * Critical tests covered:
 * - Text delta ordering and accumulation
 * - Response metadata capture
 * - onFinish callback with finishReason
 * - Error mid-stream with partial data preservation
 * - Abort signal handling with cleanup
 *
 * @see https://ai-sdk.dev/docs/ai-sdk-core/testing
 */

import { streamText } from "ai";
import { describe, expect, it, vi } from "vitest";
import {
	convertArrayToReadableStream,
	createChunkedStreamParts,
	createFinishReasonStreamParts,
	createMetadataStreamParts,
	MockLanguageModelV3,
	mockModels,
	toArray,
} from "../mocks";

describe("streamText behavior", () => {
	describe("textStream", () => {
		it("should emit text deltas in order", async () => {
			const model = mockModels.streaming(["Hello", ", ", "world", "!"]);

			const result = streamText({
				model,
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
			const model = mockModels.streaming(["The ", "quick ", "brown ", "fox"]);

			const result = streamText({
				model,
				prompt: "Test",
			});

			const chunks = await toArray(result.textStream);

			expect(chunks.join("")).toBe("The quick brown fox");
		});

		it("should handle single-chunk response", async () => {
			const model = mockModels.text("Complete response in one chunk");

			const result = streamText({
				model,
				prompt: "Test",
			});

			const chunks = await toArray(result.textStream);

			expect(chunks).toHaveLength(1);
			expect(chunks[0]).toBe("Complete response in one chunk");
		});
	});

	describe("call tracking", () => {
		it("should track prompt passed to model", async () => {
			const model = new MockLanguageModelV3({
				doStream: async () => ({
					stream: convertArrayToReadableStream(createChunkedStreamParts(["Response"])),
				}),
			});

			const result = streamText({
				model,
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
			const model = new MockLanguageModelV3({
				doStream: async () => ({
					stream: convertArrayToReadableStream(createChunkedStreamParts(["Response"])),
				}),
			});

			const result = streamText({
				model,
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
			const model = new MockLanguageModelV3({
				doStream: async () => ({
					stream: convertArrayToReadableStream(createChunkedStreamParts(["Response"])),
				}),
			});

			const result = streamText({
				model,
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
			const model = mockModels.streaming(["Hello", " world"]);
			const onFinish = vi.fn();

			const result = streamText({
				model,
				prompt: "Test",
				onFinish,
			});

			await toArray(result.textStream);
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
			const model = mockModels.text("Short response");
			const onFinish = vi.fn();

			const result = streamText({
				model,
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
			const model = mockModels.custom(createFinishReasonStreamParts("Response", "length"));
			const onFinish = vi.fn();

			const result = streamText({
				model,
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
		it("should capture response metadata", async () => {
			const timestamp = new Date("2024-01-15T12:00:00Z");
			const model = mockModels.custom(
				createMetadataStreamParts("Hello", {
					id: "response-123",
					modelId: "gpt-4-turbo",
					timestamp,
				}),
			);

			const result = streamText({
				model,
				prompt: "Test",
			});

			await toArray(result.textStream);

			const response = await result.response;
			expect(response.id).toBe("response-123");
			expect(response.modelId).toBe("gpt-4-turbo");
			expect(response.timestamp).toEqual(timestamp);
		});

		it("should include metadata in onFinish response", async () => {
			const timestamp = new Date("2024-01-15T12:00:00Z");
			const model = mockModels.custom(
				createMetadataStreamParts("Response", {
					id: "meta-456",
					modelId: "claude-3",
					timestamp,
				}),
			);
			const onFinish = vi.fn();

			const result = streamText({
				model,
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
			const model = mockModels.errorMidStream("Fatal error");

			const result = streamText({
				model,
				prompt: "Test",
				onError: () => {
					// silence
				},
			});

			await expect(async () => {
				await toArray(result.textStream);
			}).rejects.toThrow("Fatal error");
		});
	});

	describe("abort handling", () => {
		it("should respect abort signal", async () => {
			let wasAborted = false;
			const model = mockModels.abortable(() => {
				wasAborted = true;
			});

			const controller = new AbortController();

			const result = streamText({
				model,
				prompt: "Test",
				abortSignal: controller.signal,
				onError: () => {
					// silence
				},
			});

			const reader = result.textStream[Symbol.asyncIterator]();
			await reader.next();

			controller.abort();

			expect(wasAborted).toBe(true);
		});

		it("should complete stream early when aborted", async () => {
			const model = mockModels.abortable(() => {
				/* no-op */
			});
			const controller = new AbortController();

			const result = streamText({
				model,
				prompt: "Test",
				abortSignal: controller.signal,
				onError: () => {
					// silence
				},
			});

			const reader = result.textStream[Symbol.asyncIterator]();
			await reader.next();

			controller.abort();

			const nextResult = await reader.next().catch(() => ({ done: true, value: undefined }));
			expect(nextResult.done).toBe(true);
		});

		it("should preserve partial data when aborted", async () => {
			const model = mockModels.abortable(() => {
				/* no-op */
			});
			const controller = new AbortController();

			const result = streamText({
				model,
				prompt: "Test",
				abortSignal: controller.signal,
				onError: () => {
					// silence
				},
			});

			const chunks: string[] = [];
			const reader = result.textStream[Symbol.asyncIterator]();

			const first = await reader.next();
			if (!first.done && first.value) {
				chunks.push(first.value);
			}

			controller.abort();

			expect(chunks).toContain("Hello");
		});
	});
});

describe("fullStream", () => {
	it("should include all stream parts", async () => {
		const model = mockModels.streaming(["A", "B"]);

		const result = streamText({
			model,
			prompt: "Test",
		});

		const parts = await toArray(result.fullStream);
		const types = parts.map((p) => p.type);

		expect(types).toContain("start");
		expect(types).toContain("text-delta");
		expect(types).toContain("finish");
	});

	it("should emit text deltas in fullStream format", async () => {
		const model = mockModels.streaming(["Hello", ", ", "world!"]);

		const result = streamText({
			model,
			prompt: "Test",
		});

		const parts = await toArray(result.fullStream);
		const textDeltas = parts.filter((p) => p.type === "text-delta");

		// AI SDK's fullStream uses 'textDelta' property on text-delta parts
		const texts = textDeltas.map((d) => {
			if ("textDelta" in d) {
				return d.textDelta;
			}
			if ("text" in d) {
				return d.text;
			}
			return "";
		});
		expect(texts).toEqual(["Hello", ", ", "world!"]);
	});

	it("should include finish part with finishReason", async () => {
		const model = mockModels.streaming(["Test"]);

		const result = streamText({
			model,
			prompt: "Test",
		});

		const parts = await toArray(result.fullStream);
		const finishPart = parts.find((p) => p.type === "finish");

		expect(finishPart).toMatchObject({
			type: "finish",
			finishReason: "stop",
		});
	});

	it("should include usage information via result.usage", async () => {
		const model = mockModels.streaming(["Test"]);

		const result = streamText({
			model,
			prompt: "Test",
		});

		await toArray(result.textStream);

		const usage = await result.usage;
		expect(usage).toMatchObject({
			inputTokens: expect.any(Number),
			outputTokens: expect.any(Number),
		});
	});
});
