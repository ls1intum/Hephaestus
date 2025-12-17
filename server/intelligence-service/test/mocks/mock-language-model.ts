/**
 * Mock Language Model - AI SDK Standard Quality
 *
 * Properly typed mock with:
 * - Full call tracking (doStreamCalls, doGenerateCalls)
 * - Correct LanguageModelV3 types
 * - Deterministic ID generation
 */

import type {
	LanguageModelV3CallOptions,
	LanguageModelV3StreamPart,
	LanguageModelV3Usage,
} from "@ai-sdk/provider";

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const DEFAULT_USAGE: LanguageModelV3Usage = {
	inputTokens: { total: 10, noCache: 10, cacheRead: undefined, cacheWrite: undefined },
	outputTokens: { total: 5, text: 5, reasoning: undefined },
};

// ─────────────────────────────────────────────────────────────────────────────
// Mock ID Generator
// ─────────────────────────────────────────────────────────────────────────────

/** Creates a deterministic ID generator for tests. */
export function mockId(options: { prefix?: string } = {}): () => string {
	let counter = 0;
	const prefix = options.prefix ?? "id";
	return () => `${prefix}-${counter++}`;
}

// ─────────────────────────────────────────────────────────────────────────────
// Stream Utilities
// ─────────────────────────────────────────────────────────────────────────────

/** Convert array to ReadableStream for test mocking. */
export function toReadableStream<T>(items: T[]): ReadableStream<T> {
	return new ReadableStream({
		start(controller) {
			for (const item of items) {
				controller.enqueue(item);
			}
			controller.close();
		},
	});
}

/** Convert async iterable to array for assertions. */
export async function toArray<T>(iterable: AsyncIterable<T>): Promise<T[]> {
	const result: T[] = [];
	for await (const item of iterable) {
		result.push(item);
	}
	return result;
}

// ─────────────────────────────────────────────────────────────────────────────
// Mock Language Model
// ─────────────────────────────────────────────────────────────────────────────

export interface MockLanguageModelConfig {
	provider?: string;
	modelId?: string;
	doStream?:
		| ((
				options: LanguageModelV3CallOptions,
		  ) => Promise<{ stream: ReadableStream<LanguageModelV3StreamPart> }>)
		| { stream: ReadableStream<LanguageModelV3StreamPart> };
	doGenerate?: (options: LanguageModelV3CallOptions) => Promise<unknown>;
}

/** Creates default stream parts for a simple text response. */
function defaultStreamParts(text = "Hello from mock!"): LanguageModelV3StreamPart[] {
	return [
		{ type: "stream-start", warnings: [] },
		{ type: "response-metadata", id: "mock-id", modelId: "mock-model", timestamp: new Date(0) },
		{ type: "text-start", id: "1" },
		{ type: "text-delta", id: "1", delta: text },
		{ type: "text-end", id: "1" },
		{ type: "finish", finishReason: "stop", usage: DEFAULT_USAGE },
	];
}

/**
 * A proper mock that tracks ALL calls for verification.
 * Satisfies LanguageModelV3 type requirements.
 */
export class MockLanguageModel {
	readonly specificationVersion = "v3" as const;
	readonly provider: string;
	readonly modelId: string;

	/** All calls to doStream, for assertion. */
	readonly doStreamCalls: LanguageModelV3CallOptions[] = [];

	/** All calls to doGenerate, for assertion. */
	readonly doGenerateCalls: LanguageModelV3CallOptions[] = [];

	private readonly _doStream: (
		options: LanguageModelV3CallOptions,
	) => Promise<{ stream: ReadableStream<LanguageModelV3StreamPart> }>;

	private readonly _doGenerate: (options: LanguageModelV3CallOptions) => Promise<unknown>;

	constructor(config: MockLanguageModelConfig = {}) {
		this.provider = config.provider ?? "mock-provider";
		this.modelId = config.modelId ?? "mock-model";

		// Handle doStream configuration
		if (typeof config.doStream === "function") {
			this._doStream = config.doStream;
		} else if (config.doStream) {
			const staticResult = config.doStream;
			this._doStream = () => Promise.resolve(staticResult);
		} else {
			this._doStream = () =>
				Promise.resolve({
					stream: toReadableStream(defaultStreamParts()),
				});
		}

		// Handle doGenerate configuration
		this._doGenerate =
			config.doGenerate ?? (() => Promise.reject(new Error("doGenerate not implemented")));
	}

	async doStream(
		options: LanguageModelV3CallOptions,
	): Promise<{ stream: ReadableStream<LanguageModelV3StreamPart> }> {
		this.doStreamCalls.push(options);
		return await this._doStream(options);
	}

	async doGenerate(options: LanguageModelV3CallOptions): Promise<unknown> {
		this.doGenerateCalls.push(options);
		return await this._doGenerate(options);
	}

	// LanguageModelV3 requires supportedUrls to return RegExp[]
	get supportedUrls(): Promise<Record<string, RegExp[]>> {
		return Promise.resolve({});
	}
}

// ─────────────────────────────────────────────────────────────────────────────
// Stream Builders
// ─────────────────────────────────────────────────────────────────────────────

/** Create stream parts for a simple text response. */
export function createTextStream(text: string): ReadableStream<LanguageModelV3StreamPart> {
	return toReadableStream(defaultStreamParts(text));
}

/** Create stream parts for chunked streaming. */
export function createChunkedStream(chunks: string[]): ReadableStream<LanguageModelV3StreamPart> {
	const totalLength = chunks.join("").length;
	const parts: LanguageModelV3StreamPart[] = [
		{ type: "stream-start", warnings: [] },
		{ type: "response-metadata", id: "stream-id", modelId: "mock", timestamp: new Date(0) },
		{ type: "text-start", id: "1" },
		...chunks.map((delta) => ({ type: "text-delta" as const, id: "1", delta })),
		{ type: "text-end", id: "1" },
		{
			type: "finish",
			finishReason: "stop",
			usage: {
				...DEFAULT_USAGE,
				outputTokens: { total: totalLength, text: totalLength, reasoning: undefined },
			},
		},
	];
	return toReadableStream(parts);
}

/** Create stream that errors during consumption after emitting partial data. */
export function createErrorStream(errorMessage: string): ReadableStream<LanguageModelV3StreamPart> {
	let pullCount = 0;
	return new ReadableStream({
		pull(controller) {
			pullCount++;
			switch (pullCount) {
				case 1:
					controller.enqueue({ type: "stream-start", warnings: [] });
					break;
				case 2:
					controller.enqueue({ type: "text-start", id: "1" });
					break;
				case 3:
					controller.enqueue({ type: "text-delta", id: "1", delta: "Starting..." });
					break;
				default:
					controller.error(new Error(errorMessage));
			}
		},
	});
}

/** Create stream that aborts mid-response. */
export function createAbortableStream(
	onAbort: () => void,
): (signal?: AbortSignal) => ReadableStream<LanguageModelV3StreamPart> {
	return (signal?: AbortSignal) => {
		return new ReadableStream({
			start(controller) {
				controller.enqueue({ type: "stream-start", warnings: [] });
				controller.enqueue({ type: "text-start", id: "1" });
				controller.enqueue({ type: "text-delta", id: "1", delta: "Hello" });

				signal?.addEventListener("abort", () => {
					onAbort();
					controller.error(new DOMException("Aborted", "AbortError"));
				});
			},
		});
	};
}

// ─────────────────────────────────────────────────────────────────────────────
// Factory Helpers (Convenience)
// ─────────────────────────────────────────────────────────────────────────────

export const mocks = {
	/** Simple text response model. */
	text(text: string): MockLanguageModel {
		return new MockLanguageModel({
			doStream: () => Promise.resolve({ stream: createTextStream(text) }),
		});
	},

	/** Multi-chunk streaming model. */
	streaming(chunks: string[]): MockLanguageModel {
		return new MockLanguageModel({
			doStream: () => Promise.resolve({ stream: createChunkedStream(chunks) }),
		});
	},

	/** Model that errors during stream. */
	error(message: string): MockLanguageModel {
		return new MockLanguageModel({
			doStream: () => Promise.resolve({ stream: createErrorStream(message) }),
		});
	},

	/** Model that supports abort. */
	abortable(onAbort: () => void): MockLanguageModel {
		const streamFactory = createAbortableStream(onAbort);
		return new MockLanguageModel({
			doStream: (options) => Promise.resolve({ stream: streamFactory(options.abortSignal) }),
		});
	},

	/** Model that rejects immediately. */
	rejects(message: string): MockLanguageModel {
		return new MockLanguageModel({
			doStream: () => Promise.reject(new Error(message)),
		});
	},
};

// ─────────────────────────────────────────────────────────────────────────────
// Exports
// ─────────────────────────────────────────────────────────────────────────────

export { DEFAULT_USAGE };
