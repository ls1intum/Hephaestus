/**
 * Mock for @ai-sdk/react useChat hook.
 * Provides a controllable mock implementation for testing chat behavior.
 */

import type { UseChatHelpers } from "@ai-sdk/react";
import type { UIMessage } from "ai";
import { vi } from "vitest";
import type { ChatMessage } from "@/lib/types";

export type MockChatStatus = "ready" | "submitted" | "streaming" | "error";

export interface MockUseChatState<TMessage extends UIMessage = ChatMessage> {
	id: string;
	messages: TMessage[];
	status: MockChatStatus;
	error: Error | undefined;
}

export interface MockUseChatControls<TMessage extends UIMessage = ChatMessage> {
	/** Set messages directly */
	setMessages: (
		messages: TMessage[] | ((prev: TMessage[]) => TMessage[]),
	) => void;
	/** Set status */
	setStatus: (status: MockChatStatus) => void;
	/** Set error */
	setError: (error: Error | undefined) => void;
	/** Simulate streaming text response */
	simulateStreamingResponse: (
		text: string,
		messageId?: string,
	) => Promise<void>;
	/** Simulate receiving a data part during streaming */
	simulateDataPart: (dataPart: { type: string; data?: unknown }) => void;
	/** Get current state */
	getState: () => MockUseChatState<TMessage>;
	/** Reset to initial state */
	reset: () => void;
}

export interface MockUseChatReturn<TMessage extends UIMessage = ChatMessage>
	extends Omit<UseChatHelpers<TMessage>, "setMessages"> {
	setMessages: (
		messages: TMessage[] | ((prev: TMessage[]) => TMessage[]),
	) => void;
}

/**
 * Create a mock useChat hook with controllable state and behavior.
 * Returns both the mock hook and controls for testing.
 */
export function createMockUseChat<TMessage extends UIMessage = ChatMessage>(
	initialState: Partial<MockUseChatState<TMessage>> = {},
): {
	mockUseChat: (
		options?: Record<string, unknown>,
	) => MockUseChatReturn<TMessage>;
	controls: MockUseChatControls<TMessage>;
} {
	let state: MockUseChatState<TMessage> = {
		id: initialState.id ?? "mock-chat-id",
		messages: initialState.messages ?? [],
		status: initialState.status ?? "ready",
		error: initialState.error,
	};

	// Store callbacks from options
	let onFinish: ((options: { message: TMessage }) => void) | undefined;
	let onError: ((error: Error) => void) | undefined;
	// onData callback for handling data parts during streaming
	type OnDataCallback = (dataPart: { type: string; data?: unknown }) => void;
	let onData: OnDataCallback | undefined;

	const setMessagesImpl = (
		messagesOrUpdater: TMessage[] | ((prev: TMessage[]) => TMessage[]),
	) => {
		if (typeof messagesOrUpdater === "function") {
			state.messages = messagesOrUpdater(state.messages);
		} else {
			state.messages = messagesOrUpdater;
		}
	};

	/**
	 * Simulate receiving a data part during streaming
	 */
	const simulateDataPart = (dataPart: { type: string; data?: unknown }) => {
		onData?.(dataPart);
	};

	const controls: MockUseChatControls<TMessage> = {
		setMessages: setMessagesImpl,
		setStatus: (status) => {
			state.status = status;
		},
		setError: (error) => {
			state.error = error;
			if (error) {
				state.status = "error";
				onError?.(error);
			}
		},
		simulateStreamingResponse: async (
			text: string,
			messageId = "assistant-msg-1",
		) => {
			state.status = "streaming";

			const assistantMessage = {
				id: messageId,
				role: "assistant" as const,
				parts: [{ type: "text" as const, text, state: "done" as const }],
			} as TMessage;

			state.messages = [...state.messages, assistantMessage];
			state.status = "ready";

			onFinish?.({ message: assistantMessage });
		},
		getState: () => ({ ...state }),
		reset: () => {
			state = {
				id: initialState.id ?? "mock-chat-id",
				messages: initialState.messages ?? [],
				status: initialState.status ?? "ready",
				error: initialState.error,
			};
		},
		// Expose simulateDataPart for testing document streaming
		simulateDataPart,
	} as MockUseChatControls<TMessage>;

	const mockUseChat = (
		options: Record<string, unknown> = {},
	): MockUseChatReturn<TMessage> => {
		// Capture callbacks from options
		if (options.onFinish && typeof options.onFinish === "function") {
			onFinish = options.onFinish as (options: { message: TMessage }) => void;
		}
		if (options.onError && typeof options.onError === "function") {
			onError = options.onError as (error: Error) => void;
		}
		if (options.onData && typeof options.onData === "function") {
			onData = options.onData as OnDataCallback;
		}

		// Update id if provided
		if (options.id && typeof options.id === "string") {
			state.id = options.id;
		}

		// Update initial messages if provided
		if (
			options.messages &&
			Array.isArray(options.messages) &&
			state.messages.length === 0
		) {
			state.messages = options.messages as TMessage[];
		}

		const sendMessage = vi
			.fn()
			.mockImplementation((input?: { text?: string; parts?: unknown[] }) => {
				const userMessage = {
					id: `user-msg-${Date.now()}`,
					role: "user" as const,
					parts: input?.parts ?? [
						{ type: "text" as const, text: input?.text ?? "" },
					],
				} as TMessage;

				state.messages = [...state.messages, userMessage];
				state.status = "submitted";
				return Promise.resolve();
			});

		return {
			id: state.id,
			messages: state.messages,
			status: state.status,
			error: state.error,
			sendMessage: sendMessage as UseChatHelpers<TMessage>["sendMessage"],
			setMessages: setMessagesImpl,
			stop: vi.fn(),
			regenerate: vi.fn(),
			resumeStream: vi.fn().mockResolvedValue(undefined),
			addToolResult: vi.fn(),
			addToolOutput: vi.fn(),
			addToolApprovalResponse: vi.fn(),
			clearError: vi.fn(() => {
				state.error = undefined;
				state.status = "ready";
			}),
		};
	};

	return { mockUseChat, controls };
}

/**
 * Streaming chunk type for mock creation
 */
interface StreamingChunk {
	type: string;
	id?: string;
	delta?: string;
	finishReason?: string;
}

/**
 * Create mock streaming response chunks for testing
 */
export function createMockStreamingChunks(
	text: string,
	partId = "0",
): StreamingChunk[] {
	const words = text.split(" ");
	const chunks: StreamingChunk[] = [
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

	return chunks;
}

/**
 * Format chunk as SSE data line
 */
export function formatChunk(chunk: Record<string, unknown>): string {
	return `data: ${JSON.stringify(chunk)}\n\n`;
}
