/**
 * AI SDK test utilities for the Hephaestus webapp.
 * Provides helpers for creating mock messages, threads, and simulating SSE streams.
 */
import type { UIMessageChunk } from "ai";
import type { ChatMessage, MessageMetadata } from "@/lib/types";

/**
 * Generate a simple UUID-like id for testing purposes
 */
export function createMockId(): string {
	return `test-${Math.random().toString(36).substring(2, 11)}`;
}

/**
 * Create a sequence of mock IDs for deterministic testing
 */
export function createMockIdGenerator(): () => string {
	let counter = 0;
	return () => `id-${counter++}`;
}

/**
 * Role type for test messages - we only use user and assistant in tests
 */
type TestMessageRole = "user" | "assistant";

/**
 * Create a mock ChatMessage with sensible defaults.
 * Use this to create test messages with minimal boilerplate.
 */
export function createMockUIMessage(
	overrides: Partial<Omit<ChatMessage, "role">> & {
		role?: TestMessageRole;
	} = {},
): ChatMessage {
	const id = overrides.id ?? createMockId();
	const role: TestMessageRole = overrides.role ?? "user";

	const baseMessage = {
		id,
		role,
		parts: overrides.parts ?? [{ type: "text" as const, text: "Test message" }],
		metadata: overrides.metadata as MessageMetadata | undefined,
	} as ChatMessage;

	return { ...baseMessage, ...overrides } as ChatMessage;
}

/**
 * Create a user message with text content
 */
export function createUserMessage(
	text: string,
	overrides: Partial<Omit<ChatMessage, "role">> = {},
): ChatMessage {
	return createMockUIMessage({
		role: "user",
		parts: [{ type: "text" as const, text }],
		...overrides,
	});
}

/**
 * Create an assistant message with text content
 */
export function createAssistantMessage(
	text: string,
	overrides: Partial<Omit<ChatMessage, "role">> = {},
): ChatMessage {
	return createMockUIMessage({
		role: "assistant",
		parts: [{ type: "text" as const, text, state: "done" as const }],
		...overrides,
	});
}

/**
 * Create a mock thread detail for testing thread hydration
 */
export interface MockThreadDetail {
	id: string;
	title: string;
	createdAt: Date;
	updatedAt: Date;
	messages: ChatMessage[];
	votes?: Array<{ messageId: string; isUpvoted: boolean }>;
}

export function createMockThread(
	messages: ChatMessage[] = [],
	overrides: Partial<MockThreadDetail> = {},
): MockThreadDetail {
	return {
		id: overrides.id ?? createMockId(),
		title: overrides.title ?? "Test Thread",
		createdAt: overrides.createdAt ?? new Date(),
		updatedAt: overrides.updatedAt ?? new Date(),
		messages,
		votes: overrides.votes,
	};
}

/**
 * Create a mock grouped thread for sidebar testing
 */
export interface MockChatThreadGroup {
	label: string;
	threads: Array<{
		id: string;
		title: string;
		createdAt: Date;
		lastMessageAt: Date;
	}>;
}

export function createMockGroupedThreads(
	count: number = 2,
): MockChatThreadGroup[] {
	const threads = Array.from({ length: count }, (_, i) => ({
		id: `thread-${i}`,
		title: `Thread ${i}`,
		createdAt: new Date(),
		lastMessageAt: new Date(),
	}));

	return [{ label: "Today", threads }];
}

/**
 * Format a UIMessageChunk as an SSE data line for testing
 */
export function formatSSEChunk(chunk: UIMessageChunk): string {
	return `data: ${JSON.stringify(chunk)}\n\n`;
}

/**
 * Create SSE stream chunks for a simple text response
 */
export function createTextStreamChunks(
	text: string,
	partId: string = "0",
): string[] {
	const words = text.split(" ");
	const chunks: UIMessageChunk[] = [
		{ type: "start" },
		{ type: "start-step" },
		{ type: "text-start", id: partId },
	];

	for (let i = 0; i < words.length; i++) {
		const word = i === 0 ? words[i] : ` ${words[i]}`;
		chunks.push({ type: "text-delta", id: partId, delta: word });
	}

	chunks.push(
		{ type: "text-end", id: partId },
		{ type: "finish-step" },
		{ type: "finish", finishReason: "stop" },
	);

	return chunks.map(formatSSEChunk);
}

/**
 * Create a mock ReadableStream that emits SSE chunks
 */
export function createMockSSEStream(
	chunks: string[],
): ReadableStream<Uint8Array> {
	const encoder = new TextEncoder();
	let index = 0;

	return new ReadableStream({
		pull(controller) {
			if (index < chunks.length) {
				controller.enqueue(encoder.encode(chunks[index]));
				index++;
			} else {
				controller.close();
			}
		},
	});
}

/**
 * Create a controllable mock stream for testing streaming behavior
 */
export class MockStreamController {
	private controller: ReadableStreamDefaultController<Uint8Array> | null = null;
	private encoder = new TextEncoder();
	private closed = false;

	get stream(): ReadableStream<Uint8Array> {
		return new ReadableStream({
			start: (controller) => {
				this.controller = controller;
			},
		});
	}

	write(chunk: UIMessageChunk | string): void {
		if (this.closed || !this.controller) return;
		const data = typeof chunk === "string" ? chunk : formatSSEChunk(chunk);
		this.controller.enqueue(this.encoder.encode(data));
	}

	close(): void {
		if (this.closed || !this.controller) return;
		this.closed = true;
		this.controller.close();
	}

	error(err: Error): void {
		if (this.closed || !this.controller) return;
		this.closed = true;
		this.controller.error(err);
	}
}

/**
 * Wait for a hook status to transition to a specific value
 */
export async function waitForStatus(
	getStatus: () => string,
	targetStatus: string,
	timeout: number = 5000,
): Promise<void> {
	const start = Date.now();
	while (getStatus() !== targetStatus) {
		if (Date.now() - start > timeout) {
			throw new Error(
				`Timeout waiting for status "${targetStatus}", current: "${getStatus()}"`,
			);
		}
		await new Promise((resolve) => setTimeout(resolve, 10));
	}
}

/**
 * Wait for streaming to complete (status becomes 'ready')
 */
export async function waitForStreaming(
	getStatus: () => string,
	timeout: number = 5000,
): Promise<void> {
	return waitForStatus(getStatus, "ready", timeout);
}

/**
 * Create a promise that resolves after a delay
 */
export function delay(ms: number): Promise<void> {
	return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Create a resolvable promise for testing async flows
 */
export function createResolvablePromise<T = void>(): {
	promise: Promise<T>;
	resolve: (value: T) => void;
	reject: (error: Error) => void;
} {
	let resolve: (value: T) => void = () => {};
	let reject: (error: Error) => void = () => {};

	const promise = new Promise<T>((res, rej) => {
		resolve = res;
		reject = rej;
	});

	return { promise, resolve, reject };
}

/**
 * Create a mock fetch response for testing API calls
 */
export function createMockFetchResponse(
	body: ReadableStream<Uint8Array> | string | object,
	options: {
		ok?: boolean;
		status?: number;
		headers?: Record<string, string>;
	} = {},
): Response {
	const { status = 200, headers = {} } = options;

	let bodyStream: ReadableStream<Uint8Array> | null = null;

	if (body instanceof ReadableStream) {
		bodyStream = body;
	} else if (typeof body === "string") {
		const encoder = new TextEncoder();
		bodyStream = new ReadableStream({
			start(controller) {
				controller.enqueue(encoder.encode(body));
				controller.close();
			},
		});
	} else {
		const encoder = new TextEncoder();
		bodyStream = new ReadableStream({
			start(controller) {
				controller.enqueue(encoder.encode(JSON.stringify(body)));
				controller.close();
			},
		});
	}

	return new Response(bodyStream, {
		status,
		headers: new Headers({
			"Content-Type": "text/event-stream",
			...headers,
		}),
	});
}

/**
 * Create mock workspace context for testing
 */
export interface MockWorkspaceContext {
	workspaceSlug: string;
	isLoading: boolean;
}

export function createMockWorkspaceContext(
	overrides: Partial<MockWorkspaceContext> = {},
): MockWorkspaceContext {
	return {
		workspaceSlug: "test-workspace",
		isLoading: false,
		...overrides,
	};
}
