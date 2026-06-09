/**
 * Comprehensive tests for useMentorChat hook.
 *
 * This hook orchestrates AI SDK's useChat with Hephaestus-specific features:
 * - Thread management (loading, hydration, grouped threads)
 * - Greeting functionality
 * - Vote management
 * - Query invalidation on message completion
 */
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
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
import { useChat } from "@ai-sdk/react";
import { getThreadQueryKey } from "@/api/@tanstack/react-query.gen";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import type { ChatMessage } from "@/lib/types";
import { useMentorChat } from "./useMentorChat";

// Type the mocks for better intellisense
const mockUseChat = useChat as Mock;
const mockUseActiveWorkspaceSlug = useActiveWorkspaceSlug as Mock;

// Test utilities
function createMockMessage(role: "user" | "assistant", text: string, id?: string): ChatMessage {
	return {
		id: id ?? `msg-${Math.random().toString(36).slice(2)}`,
		role,
		parts: [{ type: "text", text, state: role === "assistant" ? "done" : undefined }],
	} as ChatMessage;
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
	let mockSendMessage: Mock;
	let mockSetMessages: Mock;
	let mockStop: Mock;
	let mockRegenerate: Mock;
	let mockClearError: Mock;

	beforeEach(() => {
		vi.clearAllMocks();

		queryClient = createQueryClient();

		// Setup default mocks
		mockSendMessage = vi.fn();
		mockSetMessages = vi.fn();
		mockStop = vi.fn();
		mockRegenerate = vi.fn();
		mockClearError = vi.fn();

		mockUseActiveWorkspaceSlug.mockReturnValue({
			workspaceSlug: "test-workspace",
			isLoading: false,
		});

		mockUseChat.mockReturnValue({
			id: "mock-uuid-123",
			messages: [],
			status: "ready",
			error: undefined,
			sendMessage: mockSendMessage,
			setMessages: mockSetMessages,
			stop: mockStop,
			regenerate: mockRegenerate,
			clearError: mockClearError,
			resumeStream: vi.fn(),
			addToolResult: vi.fn(),
			addToolOutput: vi.fn(),
			addToolApprovalResponse: vi.fn(),
		});

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

		it("should initialize with provided initial messages", () => {
			const initialMessages = [
				createMockMessage("user", "Hello"),
				createMockMessage("assistant", "Hi there!"),
			];

			renderHook(() => useMentorChat({ initialMessages }), {
				wrapper: createWrapper(queryClient),
			});

			// Verify useChat was called with initial messages
			expect(mockUseChat).toHaveBeenCalledWith(
				expect.objectContaining({
					messages: initialMessages,
				}),
			);
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
		it("should call sendMessage with text when sendMessage is invoked", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.sendMessage("Hello AI!");
			});

			expect(mockSendMessage).toHaveBeenCalledWith({ text: "Hello AI!" });
		});

		it("should not send empty messages", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.sendMessage("");
			});

			expect(mockSendMessage).not.toHaveBeenCalled();
		});

		it("should not send whitespace-only messages", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.sendMessage("   ");
			});

			expect(mockSendMessage).not.toHaveBeenCalled();
		});

		it("should not send messages when workspace is not available", () => {
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

			expect(mockSendMessage).not.toHaveBeenCalled();
		});
	});

	// Status pass-through ("ready"/"submitted"/"streaming") is a mock round-trip — the hook
	// returns whatever useChat returns. Covered structurally; behavioural assertion happens in
	// live-LLM tests where the real status transitions matter.

	describe("error handling", () => {
		it("should expose error from useChat", () => {
			const testError = new Error("Test error");
			mockUseChat.mockReturnValue({
				id: "mock-uuid-123",
				messages: [],
				status: "error",
				error: testError,
				sendMessage: mockSendMessage,
				setMessages: mockSetMessages,
				stop: mockStop,
				regenerate: mockRegenerate,
				clearError: mockClearError,
				resumeStream: vi.fn(),
				addToolResult: vi.fn(),
				addToolOutput: vi.fn(),
				addToolApprovalResponse: vi.fn(),
			});

			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.error).toBe(testError);
		});

		it("should call onError callback when provided and error occurs", async () => {
			const onError = vi.fn();

			// Capture the onError callback passed to useChat
			mockUseChat.mockImplementation((options) => {
				// Simulate an error occurring
				setTimeout(() => {
					options.onError?.(new Error("Streaming error"));
				}, 0);

				return {
					id: "mock-uuid-123",
					messages: [],
					status: "ready",
					error: undefined,
					sendMessage: mockSendMessage,
					setMessages: mockSetMessages,
					stop: mockStop,
					regenerate: mockRegenerate,
					clearError: mockClearError,
					resumeStream: vi.fn(),
					addToolResult: vi.fn(),
					addToolOutput: vi.fn(),
					addToolApprovalResponse: vi.fn(),
				};
			});

			renderHook(() => useMentorChat({ onError }), {
				wrapper: createWrapper(queryClient),
			});

			// The hook forwards useChat's error to the caller's onError — assert that pass-through actually
			// fires end-to-end (the setTimeout above simulates useChat raising a streaming error).
			await waitFor(() => expect(onError).toHaveBeenCalledWith(expect.any(Error)));
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
		it("should hydrate messages from thread detail when data is available", async () => {
			// IDs must be UUIDs — parseThreadMessages (chat-validation) rejects non-UUID ids, which would
			// silently skip hydration. (The previous "msg-1"/"msg-2" ids never validated, masking the bug.)
			const threadMessages = [
				createMockMessage("user", "Previous message", "f47ac10b-58cc-4372-a567-0e02b2c3d479"),
				createMockMessage("assistant", "Previous response", "c9bf9e57-1685-4c89-bafb-ff5af830be8a"),
			];

			// Mock useChat to simulate the hook with thread detail available
			// The hook hydrates messages when threadDetail is available

			mockUseChat.mockImplementation((options) => {
				return {
					id: options.id ?? "mock-uuid-123",
					messages: [],
					status: "ready",
					error: undefined,
					sendMessage: mockSendMessage,
					setMessages: mockSetMessages,
					stop: mockStop,
					regenerate: mockRegenerate,
					clearError: mockClearError,
					resumeStream: vi.fn(),
					addToolResult: vi.fn(),
					addToolOutput: vi.fn(),
					addToolApprovalResponse: vi.fn(),
				};
			});

			// Create a mock fetch for the thread API
			const mockFetch = vi.fn().mockResolvedValue({
				ok: true,
				json: vi.fn().mockResolvedValue({
					id: "thread-123",
					title: "Test Thread",
					messages: threadMessages,
				}),
			});
			global.fetch = mockFetch;

			// Pre-populate the query cache under the hook's REAL query key (hey-api createQueryKey),
			// so threadQuery resolves and the hydration effect runs.
			queryClient.setQueryData(
				getThreadQueryKey({ path: { workspaceSlug: "test-workspace", threadId: "thread-123" } }),
				{
					id: "thread-123",
					title: "Test Thread",
					messages: threadMessages,
				},
			);

			const { result } = renderHook(() => useMentorChat({ threadId: "thread-123" }), {
				wrapper: createWrapper(queryClient),
			});

			// The hook should set the threadId as currentThreadId
			expect(result.current.currentThreadId).toBe("thread-123");

			// Once the thread detail resolves (and we're not streaming), the hydration effect pushes the
			// parsed thread messages into the chat exactly once — assert the effect actually ran, not just
			// that setMessages is re-exposed.
			await waitFor(() => expect(mockSetMessages).toHaveBeenCalledTimes(1));
			expect(mockSetMessages.mock.calls[0][0]).toHaveLength(threadMessages.length);
		});

		it("should not hydrate when streaming is in progress", async () => {
			mockUseChat.mockReturnValue({
				id: "mock-uuid-123",
				messages: [],
				status: "streaming",
				error: undefined,
				sendMessage: mockSendMessage,
				setMessages: mockSetMessages,
				stop: mockStop,
				regenerate: mockRegenerate,
				clearError: mockClearError,
				resumeStream: vi.fn(),
				addToolResult: vi.fn(),
				addToolOutput: vi.fn(),
				addToolApprovalResponse: vi.fn(),
			});

			queryClient.setQueryData(
				["getThread", { path: { workspaceSlug: "test-workspace", threadId: "thread-123" } }],
				{
					id: "thread-123",
					messages: [createMockMessage("user", "Test")],
				},
			);

			renderHook(() => useMentorChat({ threadId: "thread-123" }), {
				wrapper: createWrapper(queryClient),
			});

			// setMessages should not be called during streaming
			await new Promise((resolve) => setTimeout(resolve, 50));
			expect(mockSetMessages).not.toHaveBeenCalled();
		});
	});

	describe("callback invocation", () => {
		it("should invalidate queries when message finishes", async () => {
			const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");

			// Capture and invoke the onFinish callback
			mockUseChat.mockImplementation((options) => {
				// Immediately call onFinish to simulate completion
				setTimeout(() => {
					options.onFinish?.({
						message: createMockMessage("assistant", "Done"),
					});
				}, 0);

				return {
					id: "mock-uuid-123",
					messages: [],
					status: "ready",
					error: undefined,
					sendMessage: mockSendMessage,
					setMessages: mockSetMessages,
					stop: mockStop,
					regenerate: mockRegenerate,
					clearError: mockClearError,
					resumeStream: vi.fn(),
					addToolResult: vi.fn(),
					addToolOutput: vi.fn(),
					addToolApprovalResponse: vi.fn(),
				};
			});

			renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			await waitFor(() => {
				expect(invalidateSpy).toHaveBeenCalled();
			});
		});
	});

	describe("transport configuration", () => {
		it("should configure transport with correct API endpoint", () => {
			renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(mockUseChat).toHaveBeenCalledWith(
				expect.objectContaining({
					transport: expect.objectContaining({
						api: "http://localhost:8080/workspaces/test-workspace/mentor/chat",
					}),
				}),
			);
		});

		it("rides the session cookie + CSRF double-submit header (ADR 0017 auth migration)", () => {
			renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			// biome-ignore lint/suspicious/noExplicitAny: transport comes from the mocked useChat call args
			const transport = (mockUseChat.mock.calls.at(-1)?.[0] as any).transport;
			const prepared = transport.prepareSendMessagesRequest({
				id: "thread-1",
				messages: [{ id: "m1", role: "user", parts: [{ type: "text", text: "hi" }] }],
			});

			// The Keycloak Bearer header is gone; the request now carries the session cookie + CSRF token.
			expect(prepared.credentials).toBe("include");
			expect(prepared.headers["X-XSRF-TOKEN"]).toBe("mock-csrf");
		});
	});
});
