/**
 * AI SDK Official Test Utilities
 *
 * Re-exports and extends the official AI SDK test utilities.
 * These are the GOLD STANDARD for testing AI SDK integrations.
 *
 * @see https://ai-sdk.dev/docs/ai-sdk-core/testing
 */

import type {
	LanguageModelV3FinishReason,
	LanguageModelV3StreamPart,
	LanguageModelV3Usage,
} from "@ai-sdk/provider";
import { convertArrayToReadableStream, MockLanguageModelV3 } from "ai/test";

// ─────────────────────────────────────────────────────────────────────────────
// Official AI SDK Test Utilities Re-exports
// ─────────────────────────────────────────────────────────────────────────────

export { simulateReadableStream } from "ai";
export { convertArrayToReadableStream, MockLanguageModelV3, mockId } from "ai/test";

// ─────────────────────────────────────────────────────────────────────────────
// Test Constants
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Default usage metrics for test mocks.
 * Matches AI SDK v3 LanguageModelV3Usage structure.
 */
export const TEST_USAGE: LanguageModelV3Usage = {
	inputTokens: { total: 10, noCache: 10, cacheRead: undefined, cacheWrite: undefined },
	outputTokens: { total: 5, text: 5, reasoning: undefined },
};

/**
 * Create a finish reason object (new format in AI SDK v3.0.0-beta.166+).
 */
function finishReason(
	unified: LanguageModelV3FinishReason["unified"],
): LanguageModelV3FinishReason {
	return { unified, raw: undefined };
}

// ─────────────────────────────────────────────────────────────────────────────
// Stream Part Builders
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Create standard stream parts for a text response.
 * This is the canonical stream format from AI SDK.
 */
export function createTextStreamParts(text: string): LanguageModelV3StreamPart[] {
	return [
		{ type: "stream-start", warnings: [] },
		{ type: "response-metadata", id: "mock-id", modelId: "mock-model", timestamp: new Date(0) },
		{ type: "text-start", id: "text-0" },
		{ type: "text-delta", id: "text-0", delta: text },
		{ type: "text-end", id: "text-0" },
		{ type: "finish", finishReason: finishReason("stop"), usage: TEST_USAGE },
	];
}

/**
 * Create stream parts for chunked streaming.
 */
export function createChunkedStreamParts(chunks: string[]): LanguageModelV3StreamPart[] {
	return [
		{ type: "stream-start", warnings: [] },
		{ type: "response-metadata", id: "mock-id", modelId: "mock-model", timestamp: new Date(0) },
		{ type: "text-start", id: "text-0" },
		...chunks.map((delta) => ({ type: "text-delta" as const, id: "text-0", delta })),
		{ type: "text-end", id: "text-0" },
		{ type: "finish", finishReason: finishReason("stop"), usage: TEST_USAGE },
	];
}

/**
 * Create stream parts for tool call responses.
 */
export function createToolCallStreamParts(
	toolCalls: Array<{ id: string; name: string; args: Record<string, unknown> }>,
): LanguageModelV3StreamPart[] {
	return [
		{ type: "stream-start", warnings: [] },
		{
			type: "response-metadata",
			id: "tool-response",
			modelId: "mock-model",
			timestamp: new Date(0),
		},
		...toolCalls.map((call) => ({
			type: "tool-call" as const,
			toolCallId: call.id,
			toolName: call.name,
			input: JSON.stringify(call.args),
		})),
		{ type: "finish", finishReason: finishReason("tool-calls"), usage: TEST_USAGE },
	];
}

/**
 * Create stream parts for mixed text + tool call responses.
 */
export function createMixedStreamParts(
	text: string,
	toolCall: { id: string; name: string; args: Record<string, unknown> },
): LanguageModelV3StreamPart[] {
	return [
		{ type: "stream-start", warnings: [] },
		{
			type: "response-metadata",
			id: "mixed-response",
			modelId: "mock-model",
			timestamp: new Date(0),
		},
		{ type: "text-start", id: "text-0" },
		{ type: "text-delta", id: "text-0", delta: text },
		{ type: "text-end", id: "text-0" },
		{
			type: "tool-call",
			toolCallId: toolCall.id,
			toolName: toolCall.name,
			input: JSON.stringify(toolCall.args),
		},
		{ type: "finish", finishReason: finishReason("tool-calls"), usage: TEST_USAGE },
	];
}

/**
 * Create stream parts with custom finish reason.
 */
export function createFinishReasonStreamParts(
	text: string,
	finishReasonType: LanguageModelV3FinishReason["unified"],
): LanguageModelV3StreamPart[] {
	return [
		{ type: "stream-start", warnings: [] },
		{ type: "response-metadata", id: "mock-id", modelId: "mock-model", timestamp: new Date(0) },
		{ type: "text-start", id: "text-0" },
		{ type: "text-delta", id: "text-0", delta: text },
		{ type: "text-end", id: "text-0" },
		{ type: "finish", finishReason: finishReason(finishReasonType), usage: TEST_USAGE },
	];
}

/**
 * Create stream parts with custom metadata.
 */
export function createMetadataStreamParts(
	text: string,
	metadata: { id: string; modelId: string; timestamp: Date },
): LanguageModelV3StreamPart[] {
	return [
		{ type: "stream-start", warnings: [] },
		{ type: "response-metadata", ...metadata },
		{ type: "text-start", id: "text-0" },
		{ type: "text-delta", id: "text-0", delta: text },
		{ type: "text-end", id: "text-0" },
		{ type: "finish", finishReason: finishReason("stop"), usage: TEST_USAGE },
	];
}

// ─────────────────────────────────────────────────────────────────────────────
// Mock Model Factories (Using Official MockLanguageModelV3)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Factory functions for common mock model configurations.
 * Uses the official MockLanguageModelV3 from ai/test.
 */
export const mockModels = {
	/**
	 * Create a model that returns a simple text response.
	 */
	text(text: string): MockLanguageModelV3 {
		return new MockLanguageModelV3({
			doStream: async () => ({
				stream: convertArrayToReadableStream(createTextStreamParts(text)),
			}),
		});
	},

	/**
	 * Create a model that streams text in chunks.
	 */
	streaming(chunks: string[]): MockLanguageModelV3 {
		return new MockLanguageModelV3({
			doStream: async () => ({
				stream: convertArrayToReadableStream(createChunkedStreamParts(chunks)),
			}),
		});
	},

	/**
	 * Create a model that rejects immediately (for error testing).
	 */
	rejects(message: string): MockLanguageModelV3 {
		return new MockLanguageModelV3({
			doStream: () => {
				throw new Error(message);
			},
		});
	},

	/**
	 * Create a model that errors mid-stream after emitting partial data.
	 */
	errorMidStream(errorMessage: string): MockLanguageModelV3 {
		return new MockLanguageModelV3({
			doStream: async () => ({
				stream: new ReadableStream<LanguageModelV3StreamPart>({
					start(controller) {
						controller.enqueue({ type: "stream-start", warnings: [] });
						controller.enqueue({ type: "text-start", id: "text-0" });
						controller.enqueue({ type: "text-delta", id: "text-0", delta: "Starting..." });
						// Error after partial content
						controller.error(new Error(errorMessage));
					},
				}),
			}),
		});
	},

	/**
	 * Create a model that supports abort signal handling.
	 */
	abortable(onAbort: () => void): MockLanguageModelV3 {
		return new MockLanguageModelV3({
			doStream: async ({ abortSignal }) => ({
				stream: new ReadableStream<LanguageModelV3StreamPart>({
					start(controller) {
						controller.enqueue({ type: "stream-start", warnings: [] });
						controller.enqueue({ type: "text-start", id: "text-0" });
						controller.enqueue({ type: "text-delta", id: "text-0", delta: "Hello" });

						abortSignal?.addEventListener("abort", () => {
							onAbort();
							controller.error(new DOMException("Aborted", "AbortError"));
						});
					},
				}),
			}),
		});
	},

	/**
	 * Create a model that returns tool calls.
	 */
	toolCalls(
		toolCalls: Array<{ id: string; name: string; args: Record<string, unknown> }>,
	): MockLanguageModelV3 {
		return new MockLanguageModelV3({
			doStream: async () => ({
				stream: convertArrayToReadableStream(createToolCallStreamParts(toolCalls)),
			}),
		});
	},

	/**
	 * Create a model with text + tool call (mixed response).
	 */
	mixed(
		text: string,
		toolCall: { id: string; name: string; args: Record<string, unknown> },
	): MockLanguageModelV3 {
		return new MockLanguageModelV3({
			doStream: async () => ({
				stream: convertArrayToReadableStream(createMixedStreamParts(text, toolCall)),
			}),
		});
	},

	/**
	 * Create a model with custom stream parts.
	 */
	custom(parts: LanguageModelV3StreamPart[]): MockLanguageModelV3 {
		return new MockLanguageModelV3({
			doStream: async () => ({
				stream: convertArrayToReadableStream(parts),
			}),
		});
	},

	/**
	 * Create a model with custom doStream implementation.
	 */
	withDoStream(
		doStream: () => Promise<{ stream: ReadableStream<LanguageModelV3StreamPart> }>,
	): MockLanguageModelV3 {
		return new MockLanguageModelV3({ doStream });
	},
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Async Iterable Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Convert an async iterable to an array for assertions.
 */
export async function toArray<T>(iterable: AsyncIterable<T>): Promise<T[]> {
	const result: T[] = [];
	for await (const item of iterable) {
		result.push(item);
	}
	return result;
}
