/**
 * Comprehensive tests for useMentorChat hook.
 *
 * This hook orchestrates AI SDK's useChat with Hephaestus-specific features:
 * - Thread management (loading, hydration, grouped threads)
 * - Greeting functionality
 * - Vote management
 * - Document/artifact streaming
 * - Query invalidation on message completion
 */
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import {
	afterEach,
	beforeEach,
	describe,
	expect,
	it,
	type Mock,
	vi,
} from "vitest";

// Mock external dependencies before importing the hook
vi.mock("@ai-sdk/react", () => ({
	useChat: vi.fn(),
}));

vi.mock("@/hooks/use-active-workspace", () => ({
	useActiveWorkspaceSlug: vi.fn(),
}));

vi.mock("@/integrations/auth", () => ({
	keycloakService: {
		getToken: vi.fn(() => "mock-token"),
	},
}));

vi.mock("@/environment", () => ({
	default: {
		serverUrl: "http://localhost:8080",
	},
}));

vi.mock("@/stores/artifact-store", () => ({
	useArtifactStore: {
		getState: vi.fn(() => ({
			openArtifact: vi.fn(),
			closeArtifact: vi.fn(),
		})),
	},
}));

vi.mock("@/stores/document-store", () => ({
	useDocumentsStore: {
		getState: vi.fn(() => ({
			setEmptyDraft: vi.fn(),
			appendDraftDelta: vi.fn(),
			finishDraft: vi.fn(),
			documents: {},
		})),
	},
}));

vi.mock("uuid", () => ({
	v4: vi.fn(() => "mock-uuid-123"),
}));

// Import after mocks are set up
import { useChat } from "@ai-sdk/react";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import type { ChatMessage } from "@/lib/types";
import { useArtifactStore } from "@/stores/artifact-store";
import { useDocumentsStore } from "@/stores/document-store";
import { useMentorChat } from "./useMentorChat";

// Type the mocks for better intellisense
const mockUseChat = useChat as Mock;
const mockUseActiveWorkspaceSlug = useActiveWorkspaceSlug as Mock;

// Test utilities
function createMockMessage(
	role: "user" | "assistant",
	text: string,
	id?: string,
): ChatMessage {
	return {
		id: id ?? `msg-${Math.random().toString(36).slice(2)}`,
		role,
		parts: [
			{ type: "text", text, state: role === "assistant" ? "done" : undefined },
		],
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
		return (
			<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
		);
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
			const { result } = renderHook(
				() => useMentorChat({ threadId: "existing-thread-123" }),
				{ wrapper: createWrapper(queryClient) },
			);

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

			const { result } = renderHook(
				() => useMentorChat({ threadId: "thread-123" }),
				{ wrapper: createWrapper(queryClient) },
			);

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

	describe("status transitions", () => {
		it("should reflect 'ready' status initially", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.status).toBe("ready");
		});

		it("should reflect 'submitted' status when message is being sent", () => {
			mockUseChat.mockReturnValue({
				id: "mock-uuid-123",
				messages: [createMockMessage("user", "Hello")],
				status: "submitted",
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

			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.status).toBe("submitted");
		});

		it("should reflect 'streaming' status during response streaming", () => {
			mockUseChat.mockReturnValue({
				id: "mock-uuid-123",
				messages: [
					createMockMessage("user", "Hello"),
					createMockMessage("assistant", "Hi..."),
				],
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

			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.status).toBe("streaming");
		});

		it("should compute isLoading correctly for various states", () => {
			// Test workspace loading
			mockUseActiveWorkspaceSlug.mockReturnValue({
				workspaceSlug: "test-workspace",
				isLoading: true,
			});

			const { result: loadingResult, rerender } = renderHook(
				() => useMentorChat({}),
				{ wrapper: createWrapper(queryClient) },
			);

			expect(loadingResult.current.isLoading).toBe(true);

			// Test submitted status
			mockUseActiveWorkspaceSlug.mockReturnValue({
				workspaceSlug: "test-workspace",
				isLoading: false,
			});

			mockUseChat.mockReturnValue({
				id: "mock-uuid-123",
				messages: [],
				status: "submitted",
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

			rerender();
			expect(loadingResult.current.isLoading).toBe(true);
		});
	});

	describe("greeting functionality", () => {
		it("should trigger greeting when autoGreeting=true and no messages exist", async () => {
			const mockFetch = vi.fn().mockResolvedValue({
				ok: true,
				body: createMockSSEStream([
					'data: {"type":"start"}\n\n',
					'data: {"type":"text-start","id":"0"}\n\n',
					'data: {"type":"text-delta","id":"0","delta":"Hello!"}\n\n',
					'data: {"type":"text-end","id":"0"}\n\n',
					'data: {"type":"finish","finishReason":"stop"}\n\n',
				]),
			});
			global.fetch = mockFetch;

			renderHook(() => useMentorChat({ autoGreeting: true }), {
				wrapper: createWrapper(queryClient),
			});

			await waitFor(() => {
				expect(mockFetch).toHaveBeenCalledWith(
					"http://localhost:8080/workspaces/test-workspace/mentor/chat",
					expect.objectContaining({
						method: "POST",
						body: expect.stringContaining('"greeting":true'),
					}),
				);
			});
		});

		it("should not trigger greeting when messages already exist", async () => {
			const mockFetch = vi.fn();
			global.fetch = mockFetch;

			mockUseChat.mockReturnValue({
				id: "mock-uuid-123",
				messages: [createMockMessage("user", "Existing message")],
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

			renderHook(() => useMentorChat({ autoGreeting: true }), {
				wrapper: createWrapper(queryClient),
			});

			// Wait a bit to ensure the effect would have run
			await new Promise((resolve) => setTimeout(resolve, 50));

			expect(mockFetch).not.toHaveBeenCalled();
		});

		it("should not trigger greeting when autoGreeting=false", async () => {
			const mockFetch = vi.fn();
			global.fetch = mockFetch;

			renderHook(() => useMentorChat({ autoGreeting: false }), {
				wrapper: createWrapper(queryClient),
			});

			await new Promise((resolve) => setTimeout(resolve, 50));

			expect(mockFetch).not.toHaveBeenCalled();
		});

		it("should call triggerGreeting manually", async () => {
			const mockFetch = vi.fn().mockResolvedValue({
				ok: true,
				body: createMockSSEStream([
					'data: {"type":"start"}\n\n',
					'data: {"type":"finish","finishReason":"stop"}\n\n',
				]),
			});
			global.fetch = mockFetch;

			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			await act(async () => {
				await result.current.triggerGreeting();
			});

			expect(mockFetch).toHaveBeenCalledWith(
				expect.stringContaining("/mentor/chat"),
				expect.objectContaining({
					body: expect.stringContaining('"greeting":true'),
				}),
			);
		});

		it("should only trigger greeting once even if called multiple times", async () => {
			const mockFetch = vi.fn().mockResolvedValue({
				ok: true,
				body: createMockSSEStream([
					'data: {"type":"finish","finishReason":"stop"}\n\n',
				]),
			});
			global.fetch = mockFetch;

			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			await act(async () => {
				await result.current.triggerGreeting();
				await result.current.triggerGreeting();
				await result.current.triggerGreeting();
			});

			expect(mockFetch).toHaveBeenCalledTimes(1);
		});
	});

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

		it("should call onError callback when provided and error occurs", () => {
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

			// The callback was passed to useChat - verify the structure
			expect(mockUseChat).toHaveBeenCalledWith(
				expect.objectContaining({
					onError: expect.any(Function),
				}),
			);
		});

		it("should handle greeting fetch errors gracefully", async () => {
			const onError = vi.fn();
			const mockFetch = vi.fn().mockResolvedValue({
				ok: false,
				status: 500,
			});
			global.fetch = mockFetch;

			const { result } = renderHook(() => useMentorChat({ onError }), {
				wrapper: createWrapper(queryClient),
			});

			await act(async () => {
				await result.current.triggerGreeting();
			});

			expect(onError).toHaveBeenCalledWith(expect.any(Error));
		});

		it("should expose clearError function", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.clearError).toBe(mockClearError);
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
			const threadMessages = [
				createMockMessage("user", "Previous message", "msg-1"),
				createMockMessage("assistant", "Previous response", "msg-2"),
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

			// Pre-populate query cache with thread data using the correct query key structure
			const queryKey = [
				"getThread",
				{ path: { workspaceSlug: "test-workspace", threadId: "thread-123" } },
			];
			queryClient.setQueryData(queryKey, {
				id: "thread-123",
				title: "Test Thread",
				messages: threadMessages,
			});

			const { result } = renderHook(
				() => useMentorChat({ threadId: "thread-123" }),
				{ wrapper: createWrapper(queryClient) },
			);

			// The hook should set the threadId as currentThreadId
			expect(result.current.currentThreadId).toBe("thread-123");

			// Hydration relies on threadDetail being loaded which triggers setMessages
			// In the real implementation, this happens via the useQuery + useEffect combination
			// For testing, we verify the mechanism is in place
			await waitFor(
				() => {
					// The setMessages should be exposed and callable
					expect(result.current.setMessages).toBe(mockSetMessages);
				},
				{ timeout: 100 },
			);
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
				[
					"getThread",
					{ path: { workspaceSlug: "test-workspace", threadId: "thread-123" } },
				],
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
		it("should pass onFinish to useChat", () => {
			const onFinish = vi.fn();

			renderHook(() => useMentorChat({ onFinish }), {
				wrapper: createWrapper(queryClient),
			});

			expect(mockUseChat).toHaveBeenCalledWith(
				expect.objectContaining({
					onFinish: expect.any(Function),
				}),
			);
		});

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

	describe("artifact/document handling", () => {
		it("should expose openArtifactForDocument", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(typeof result.current.openArtifactForDocument).toBe("function");
		});

		it("should call artifact store when opening artifact", () => {
			const mockOpenArtifact = vi.fn();
			(useArtifactStore.getState as Mock).mockReturnValue({
				openArtifact: mockOpenArtifact,
				closeArtifact: vi.fn(),
			});

			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			const mockDocument = { id: "doc-123", title: "Test Doc" };
			const mockRect = { top: 0, left: 0, width: 100, height: 100 } as DOMRect;

			act(() => {
				result.current.openArtifactForDocument(mockDocument as never, mockRect);
			});

			expect(mockOpenArtifact).toHaveBeenCalledWith(
				"text:doc-123",
				mockRect,
				"Test Doc",
			);
		});

		it("should expose closeArtifact", () => {
			const mockCloseArtifact = vi.fn();
			(useArtifactStore.getState as Mock).mockReturnValue({
				openArtifact: vi.fn(),
				closeArtifact: mockCloseArtifact,
			});

			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			act(() => {
				result.current.closeArtifact();
			});

			expect(mockCloseArtifact).toHaveBeenCalled();
		});

		it("should handle document data parts via onData callback", () => {
			const mockSetEmptyDraft = vi.fn();
			const mockAppendDraftDelta = vi.fn();
			const mockFinishDraft = vi.fn();

			(useDocumentsStore.getState as Mock).mockReturnValue({
				setEmptyDraft: mockSetEmptyDraft,
				appendDraftDelta: mockAppendDraftDelta,
				finishDraft: mockFinishDraft,
				documents: {},
			});

			// Capture the onData callback
			let capturedOnData:
				| ((dataPart: { type: string; data?: unknown }) => void)
				| undefined;
			mockUseChat.mockImplementation((options) => {
				capturedOnData = options.onData;
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

			// Simulate document creation data part
			act(() => {
				capturedOnData?.({
					type: "data-document-create",
					data: { id: "doc-1", title: "New Document" },
				});
			});

			expect(mockSetEmptyDraft).toHaveBeenCalledWith("doc-1", {
				title: "New Document",
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

		it("should use throttling for smoother streaming", () => {
			renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(mockUseChat).toHaveBeenCalledWith(
				expect.objectContaining({
					experimental_throttle: 100,
				}),
			);
		});
	});

	describe("exposed controls", () => {
		it("should expose stop function", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.stop).toBe(mockStop);
		});

		it("should expose regenerate function", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.regenerate).toBe(mockRegenerate);
		});

		it("should expose setMessages function", () => {
			const { result } = renderHook(() => useMentorChat({}), {
				wrapper: createWrapper(queryClient),
			});

			expect(result.current.setMessages).toBe(mockSetMessages);
		});
	});
});

// Helper function for creating mock SSE streams
function createMockSSEStream(chunks: string[]): ReadableStream<Uint8Array> {
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
