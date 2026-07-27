/**
 * Tests for useMentorChat.
 *
 * This hook orchestrates AI SDK's useChat with Hephaestus-specific features:
 * - Thread management (loading, hydration, grouped threads)
 * - Vote management
 * - Query invalidation on message completion
 */
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { type ReactNode, useState } from "react";
import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from "vitest";

// Mock external dependencies before importing the hook
vi.mock("@ai-sdk/react", () => ({
	useChat: vi.fn(),
}));

vi.mock("@/hooks/use-active-workspace", () => ({
	useActiveWorkspaceSlug: vi.fn(),
}));

vi.mock("@/integrations/auth", () => ({
	csrfHeaders: vi.fn(() => ({ "X-XSRF-TOKEN": "mock-csrf" })),
}));

vi.mock("@/environment", () => ({
	default: {
		serverUrl: "http://localhost:8080",
	},
}));

vi.mock("uuid", () => ({
	v4: vi.fn(() => "mock-uuid-123"),
}));

// Import after mocks are set up
import type { UseChatHelpers } from "@ai-sdk/react";
import { useChat } from "@ai-sdk/react";
import { getThreadQueryKey, listThreadsQueryKey } from "@/api/@tanstack/react-query.gen";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import type { ChatMessage } from "@/lib/types";
import { useMentorChat } from "./use-mentor-chat";

// Type the mocks for better intellisense
const mockUseChat = useChat as Mock;
const mockUseActiveWorkspaceSlug = useActiveWorkspaceSlug as Mock;

type ChatStatus = UseChatHelpers<ChatMessage>["status"];

// Test utilities
function createMockMessage(role: "user" | "assistant", text: string, id?: string): ChatMessage {
	return {
		id: id ?? `msg-${Math.random().toString(36).slice(2)}`,
		role,
		parts: [{ type: "text", text, state: role === "assistant" ? "done" : undefined }],
	} as ChatMessage;
}

/** The visible text of a message, the way the transcript renders it. */
function textOf(message: ChatMessage): string {
	return message.parts
		.map((part) => (part.type === "text" ? part.text : ""))
		.join("")
		.trim();
}

interface FakeChat {
	/** What the hook handed to `useChat` on the last render — the transport lives here. */
	options: {
		id: string;
		messages: ChatMessage[];
		transport: unknown;
		onFinish?: (event: { message: ChatMessage }) => void;
		onError?: (error: Error) => void;
	};
	/** A stream that fails the way a dropped connection does. */
	raiseError: (error: Error) => void;
	/** A turn that completes, which is what triggers the hook's cache invalidation. */
	finishTurn: () => void;
}

/**
 * A stateful stand-in for AI SDK's `useChat`.
 *
 * The hook under test does not own its transcript — `useChat` does — so a stub that returns a frozen
 * `messages: []` leaves every behavioural claim here ("sends", "refuses to send", "hydrates",
 * "does not overwrite a stream") with nothing to check but a spy. This fake keeps the list the way
 * the real hook does, so the tests read `result.current.messages` and `result.current.status` — the
 * two values the mentor transcript is rendered from.
 */
function installFakeChat(initialStatus: ChatStatus = "ready"): FakeChat {
	const fake = {
		options: undefined,
		raiseError: () => {},
		finishTurn: () => {},
	} as unknown as FakeChat;

	mockUseChat.mockImplementation((options: FakeChat["options"] & { generateId?: () => string }) => {
		fake.options = options;
		const [messages, setMessages] = useState<ChatMessage[]>(options.messages ?? []);
		const [status, setStatus] = useState<ChatStatus>(initialStatus);
		const [error, setError] = useState<Error | undefined>(undefined);

		fake.raiseError = (raised: Error) => {
			setStatus("error");
			setError(raised);
			options.onError?.(raised);
		};
		fake.finishTurn = () => {
			options.onFinish?.({ message: createMockMessage("assistant", "Done") });
		};

		return {
			id: options.id,
			messages,
			status,
			error,
			sendMessage: ({ text }: { text: string }) => {
				setMessages((current) => [
					...current,
					createMockMessage("user", text, options.generateId?.()),
				]);
				setStatus("submitted");
			},
			setMessages,
			stop: vi.fn(),
			regenerate: vi.fn(),
			clearError: vi.fn(),
			resumeStream: vi.fn(),
			addToolResult: vi.fn(),
			addToolOutput: vi.fn(),
			addToolApprovalResponse: vi.fn(),
		};
	});

	return fake;
}

function createQueryClient() {
	return new QueryClient({
		defaultOptions: {
			queries: {
				retry: false,
				staleTime: Number.POSITIVE_INFINITY,
			},
			mutations: {
				retry: false,
			},
		},
	});
}

function createWrapper(queryClient: QueryClient) {
	return function Wrapper({ children }: { children: ReactNode }) {
		return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
	};
}

describe("useMentorChat", () => {
	let queryClient: QueryClient;
	let chat: FakeChat;

	beforeEach(() => {
		vi.clearAllMocks();

		queryClient = createQueryClient();

		mockUseActiveWorkspaceSlug.mockReturnValue({
			workspaceSlug: "test-workspace",
			isLoading: false,
		});

		chat = installFakeChat();

		// Reset fetch mock
		global.fetch = vi.fn();
	});

	afterEach(() => {
		vi.restoreAllMocks();
	});

	describe("initialization", () => {
		it("should initialize with default values when no threadId provided", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.messages).toEqual([]);
			expect(result.current.status).toBe("ready");
			expect(result.current.error).toBeUndefined();
			expect(result.current.currentThreadId).toBe("mock-uuid-123");
		});

		it("should use provided threadId when available", () => {
			const { result } = renderHook(() => useMentorChat({ threadId: "existing-thread-123" }), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.currentThreadId).toBe("existing-thread-123");
		});

		it("opens on the messages it was handed, so a resumed thread renders straight away", () => {
			const initialMessages = [
				createMockMessage("user", "Hello"),
				createMockMessage("assistant", "Hi there!"),
			];

			const { result } = renderHook(() => useMentorChat({ initialMessages }), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.messages).toEqual(initialMessages);
		});

		it("should not enable thread query when workspace is loading", () => {
			mockUseActiveWorkspaceSlug.mockReturnValue({
				workspaceSlug: null,
				isLoading: true,
			});

			const { result } = renderHook(() => useMentorChat({ threadId: "thread-123" }), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.isLoading).toBe(true);
		});
	});

	describe("message sending", () => {
		it("puts what the user typed into the transcript", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.sendMessage("Hello AI!");
			});

			expect(result.current.messages).toHaveLength(1);
			expect(result.current.messages[0].role).toBe("user");
			expect(textOf(result.current.messages[0])).toBe("Hello AI!");
			expect(result.current.status).toBe("submitted");
		});

		it.each<[string, string]>([
			["an empty message", ""],
			["a message that is only whitespace", "   "],
		])("leaves the transcript untouched for %s", (_name, text) => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.sendMessage(text);
			});

			expect(result.current.messages).toEqual([]);
			expect(result.current.status).toBe("ready");
		});

		it("sends nothing while no workspace is active, because there is no endpoint to send to", () => {
			mockUseActiveWorkspaceSlug.mockReturnValue({
				workspaceSlug: null,
				isLoading: false,
			});

			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.sendMessage("Hello");
			});

			expect(result.current.messages).toEqual([]);
			expect(result.current.status).toBe("ready");
		});
	});

	// Status pass-through ("ready"/"submitted"/"streaming") is a mock round-trip — the hook
	// returns whatever useChat returns. Covered structurally; behavioural assertion happens in
	// live-LLM tests where the real status transitions matter.

	describe("error handling", () => {
		it("surfaces a failed stream as state and tells the caller about it once", async () => {
			const onError = vi.fn();
			const streamingError = new Error("Streaming error");
			const { result } = renderHook(() => useMentorChat({ onError }), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				chat.raiseError(streamingError);
			});

			// Both halves matter: the transcript has to be able to render the failure, and the route
			// that opened the chat has to be told so it can toast.
			expect(result.current.error).toBe(streamingError);
			expect(result.current.status).toBe("error");
			await waitFor(() => expect(onError).toHaveBeenCalledExactlyOnceWith(streamingError));
		});
	});

	describe("vote functionality", () => {
		it("should call voteMessage with correct parameters", async () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.voteMessage("msg-123", true);
			});

			// Optimistic update should happen
			expect(result.current.votes).toContainEqual(
				expect.objectContaining({
					messageId: "msg-123",
					isUpvoted: true,
				}),
			);
		});

		it("should handle vote downvote", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.voteMessage("msg-456", false);
			});

			expect(result.current.votes).toContainEqual(
				expect.objectContaining({
					messageId: "msg-456",
					isUpvoted: false,
				}),
			);
		});

		it("should track multiple votes", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.voteMessage("msg-1", true);
				result.current.voteMessage("msg-2", false);
				result.current.voteMessage("msg-3", true);
			});

			expect(result.current.votes).toHaveLength(3);
		});

		it("should not vote when workspace is not available", () => {
			mockUseActiveWorkspaceSlug.mockReturnValue({
				workspaceSlug: null,
				isLoading: false,
			});

			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.voteMessage("msg-123", true);
			});

			expect(result.current.votes).toHaveLength(0);
		});
	});

	describe("thread hydration", () => {
		// IDs must be UUIDs — parseThreadMessages (chat-validation) rejects non-UUID ids and hydration
		// is skipped silently, so a non-UUID fixture would make this suite green over a dead effect.
		const threadMessages = [
			createMockMessage("user", "Previous message", "f47ac10b-58cc-4372-a567-0e02b2c3d479"),
			createMockMessage("assistant", "Previous response", "c9bf9e57-1685-4c89-bafb-ff5af830be8a"),
		];

		/** The cache entry `threadQuery` resolves from, under the hook's real hey-api query key. */
		function seedThread() {
			queryClient.setQueryData(
				getThreadQueryKey({ path: { workspaceSlug: "test-workspace", threadId: "thread-123" } }),
				{
					id: "thread-123",
					title: "Test Thread",
					messages: threadMessages,
				},
			);
		}

		it("renders the stored thread once its detail resolves", async () => {
			seedThread();

			const { result } = renderHook(() => useMentorChat({ threadId: "thread-123" }), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.currentThreadId).toBe("thread-123");
			await waitFor(() =>
				expect(result.current.messages.map((message) => message.id)).toEqual(
					threadMessages.map((message) => message.id),
				),
			);
			expect(result.current.messages.map(textOf)).toEqual([
				"Previous message",
				"Previous response",
			]);
		});

		it("does not overwrite an answer that is still streaming", async () => {
			chat = installFakeChat("streaming");
			seedThread();

			const { result } = renderHook(() => useMentorChat({ threadId: "thread-123" }), {
				wrapper: createWrapper(queryClient),
			});

			// Long enough for the hydration effect to have run had it been going to.
			await new Promise((resolve) => setTimeout(resolve, 50));
			expect(result.current.messages).toEqual([]);
			expect(result.current.status).toBe("streaming");
		});
	});

	describe("callback invocation", () => {
		it("marks the thread and the thread list stale when a turn finishes", async () => {
			const threadsKey = listThreadsQueryKey({ path: { workspaceSlug: "test-workspace" } });
			// No `threadId` was passed, so the hook files the turn under the id it generated itself.
			const threadKey = getThreadQueryKey({
				path: { workspaceSlug: "test-workspace", threadId: "mock-uuid-123" },
			});
			queryClient.setQueryData(threadsKey, []);
			queryClient.setQueryData(threadKey, { id: "mock-uuid-123", messages: [] });

			renderHook(() => useMentorChat({}), { wrapper: createWrapper(queryClient) });
			expect(queryClient.getQueryState(threadsKey)?.isInvalidated).toBe(false);

			act(() => {
				chat.finishTurn();
			});

			// The sidebar's thread list and the open thread both changed on the server; leaving either
			// fresh leaves the answer that just arrived missing from the next navigation.
			expect(queryClient.getQueryState(threadsKey)?.isInvalidated).toBe(true);
			expect(queryClient.getQueryState(threadKey)?.isInvalidated).toBe(true);
		});
	});

	describe("transport configuration", () => {
		it("posts the latest message to the active workspace's mentor endpoint, cookie + CSRF", () => {
			renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			const transport = chat.options.transport as {
				api: string;
				prepareSendMessagesRequest: (input: {
					id: string;
					messages: { id: string; role: string; parts: { type: string; text: string }[] }[];
				}) => {
					body: unknown;
					credentials: string;
					headers: Record<string, string>;
				};
			};

			expect(transport.api).toBe("http://localhost:8080/workspaces/test-workspace/mentor/chat");

			const latest = { id: "m2", role: "user", parts: [{ type: "text", text: "second" }] };
			const prepared = transport.prepareSendMessagesRequest({
				id: "thread-1",
				messages: [{ id: "m1", role: "user", parts: [{ type: "text", text: "first" }] }, latest],
			});

			// Only the newest turn goes over the wire — the server rebuilds context from the thread id.
			expect(prepared.body).toEqual({ id: "thread-1", message: latest });
			// Cookie-session auth (ADR 0017): the session rides `credentials: include` and the
			// state-changing POST carries the CSRF double-submit header. No Keycloak Bearer.
			expect(prepared.credentials).toBe("include");
			expect(prepared.headers["X-XSRF-TOKEN"]).toBe("mock-csrf");
		});
	});
});
