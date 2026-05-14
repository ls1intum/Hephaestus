import type { UseChatHelpers } from "@ai-sdk/react";
import { useChat } from "@ai-sdk/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
	DefaultChatTransport,
	parseJsonEventStream,
	readUIMessageStream,
	uiMessageChunkSchema,
} from "ai";
import { useEffect, useRef, useState } from "react";
import { v4 as uuidv4 } from "uuid";
import {
	getThreadOptions,
	getThreadQueryKey,
	listThreadsOptions,
	listThreadsQueryKey,
	voteMutation,
} from "@/api/@tanstack/react-query.gen";
import type { ChatMessageVote, ChatThreadDetail, ChatThreadSummary } from "@/api/types.gen";
import environment from "@/environment";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { keycloakService } from "@/integrations/auth";
import {
	extractVotesFromThreadDetail,
	parseSingleMessage,
	parseThreadMessages,
} from "@/lib/chat-validation";
import type { ChatMessage } from "@/lib/types";

interface UseMentorChatOptions {
	threadId?: string;
	initialMessages?: ChatMessage[];
	autoGreeting?: boolean; // If true, trigger greeting on mount for new threads
	onFinish?: () => void;
	onError?: (error: Error) => void;
}

interface UseMentorChatReturn extends Omit<UseChatHelpers<ChatMessage>, "sendMessage"> {
	sendMessage: (text: string) => void;
	triggerGreeting: () => Promise<void>; // Manually trigger a greeting
	threadDetail: ChatThreadDetail | undefined;
	isThreadLoading: boolean;
	threadError: Error | null;
	threads: ChatThreadSummary[] | undefined;
	isThreadsLoading: boolean;
	isLoading: boolean;
	currentThreadId: string | undefined;
	voteMessage: (messageId: string, isUpvoted: boolean) => void;
	votes: ChatMessageVote[];
}

export function useMentorChat({
	threadId,
	initialMessages = [],
	autoGreeting = false,
	onFinish,
	onError,
}: UseMentorChatOptions): UseMentorChatReturn {
	const queryClient = useQueryClient();
	const { workspaceSlug, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";
	const hasWorkspace = Boolean(workspaceSlug);

	// Generate a stable chat ID for this hook lifecycle
	const [stableThreadId] = useState(() => threadId || uuidv4());

	// Fetch thread detail if threadId is provided; avoid immediate refetch on mount
	const threadQueryKey = getThreadQueryKey({
		path: { workspaceSlug: slug, threadId: threadId || "" },
	});
	const threadQuery = useQuery({
		...getThreadOptions({
			path: { workspaceSlug: slug, threadId: threadId || "" },
		}),
		enabled: Boolean(threadId) && hasWorkspace,
		initialData: () =>
			hasWorkspace
				? (queryClient.getQueryData(threadQueryKey) as ChatThreadDetail | undefined)
				: undefined,
		initialDataUpdatedAt: Date.now(),
		staleTime: 60_000,
		refetchOnMount: false,
		refetchOnWindowFocus: false,
		refetchOnReconnect: false,
	});

	const { data: threadDetail, isLoading: isThreadLoading, error: threadError } = threadQuery;

	// Fetch threads for sidebar/navigation; avoid immediate refetch on mount
	const threadsKey = listThreadsQueryKey({ path: { workspaceSlug: slug } });
	const { data: threads, isLoading: isThreadsLoading } = useQuery({
		...listThreadsOptions({ path: { workspaceSlug: slug } }),
		enabled: hasWorkspace,
		initialData: () =>
			hasWorkspace
				? (queryClient.getQueryData(threadsKey) as ChatThreadSummary[] | undefined)
				: undefined,
		initialDataUpdatedAt: Date.now(),
		staleTime: 60_000,
		refetchOnMount: false,
		refetchOnWindowFocus: false,
		refetchOnReconnect: false,
	});

	// Vote message mutation
	const voteMessageMut = useMutation(voteMutation());

	// Optimistic votes state: messageId -> isUpvoted
	const [voteState, setVoteState] = useState<Record<string, boolean | undefined>>({});
	const votes: ChatMessageVote[] = Object.entries(voteState)
		.filter((entry): entry is [string, boolean] => entry[1] !== undefined)
		.map(([messageId, isUpvoted]) => ({
			messageId,
			isUpvoted,
			updatedAt: new Date(),
		}));

	// Create stable transport configuration
	const mentorChatApi = `${environment.serverUrl}/workspaces/${slug}/mentor/chat`;
	const stableTransport = new DefaultChatTransport<ChatMessage>({
		api: mentorChatApi,
		// Always attach a fresh token per request
		prepareSendMessagesRequest: ({ id, messages }) => {
			const effectiveId = id || stableThreadId;
			// Only send the latest message; backend reconstructs context from thread ID
			const lastMessage = messages.at(-1);
			// Determine previous message ID from current local state (selected leaf or last message)
			const prev = messages.length > 1 ? messages[messages.length - 2]?.id : undefined;
			return {
				body: {
					id: effectiveId,
					message: lastMessage,
					previousMessageId: prev,
				},
				headers: {
					Authorization: `Bearer ${keycloakService.getToken()}`,
				},
			};
		},
	});

	// Create stable onFinish callback
	const stableOnFinish = (_options: { message: ChatMessage }) => {
		if (hasWorkspace) {
			queryClient.invalidateQueries({
				queryKey: listThreadsQueryKey({ path: { workspaceSlug: slug } }),
			});
		}
		if (threadId || stableThreadId) {
			queryClient.invalidateQueries({
				queryKey: getThreadQueryKey({
					path: {
						workspaceSlug: slug,
						threadId: threadId || stableThreadId || "",
					},
				}),
			});
		}
		onFinish?.();
	};

	// Create stable onError callback
	const stableOnError = (error: Error) => {
		onError?.(error);
	};

	const {
		messages,
		sendMessage: originalSendMessage,
		status,
		stop,
		regenerate,
		error,
		clearError,
		setMessages,
		resumeStream,
		addToolResult,
		addToolOutput,
		addToolApprovalResponse,
		id,
	} = useChat<ChatMessage>({
		id: stableThreadId, // Use stable ID that never changes
		messages: initialMessages, // Start with initial messages only - backend will provide thread history
		generateId: () => uuidv4(), // Generate UUID for all messages
		experimental_throttle: 100, // Add throttling for smoother streaming
		transport: stableTransport,
		onFinish: stableOnFinish,
		onError: stableOnError,
		// The Pi mentor only streams text/reasoning parts today. If/when typed
		// data parts return (e.g. token usage, custom UI events), wire them here.
	});

	// Hydrate thread messages once when loaded and not streaming
	const hydratedRef = useRef<string | null>(null);
	useEffect(() => {
		if (!threadId) return;
		if (hydratedRef.current === threadId) return;
		if (status === "streaming" || status === "submitted") return;
		if (!threadDetail?.messages) return;

		// Validate messages before setting state
		const validatedMessages = parseThreadMessages(threadDetail.messages);
		if (!validatedMessages) {
			console.error("[useMentorChat] Failed to validate thread messages");
			return;
		}

		setMessages(validatedMessages);
		hydratedRef.current = threadId;
	}, [threadId, threadDetail?.messages, status, setMessages]);

	// Hydrate votes from server thread detail when available
	const hydratedVotesRef = useRef<string | null>(null);
	useEffect(() => {
		if (!threadId) return; // only hydrate for existing threads
		if (hydratedVotesRef.current === threadId) return;

		// Safely extract and validate votes from thread detail
		const serverVotes = extractVotesFromThreadDetail(threadDetail);
		if (serverVotes.length === 0) return;

		const next: Record<string, boolean | undefined> = {};
		for (const v of serverVotes) {
			if (v?.messageId) next[v.messageId] = v.isUpvoted ?? undefined;
		}
		setVoteState(next);
		hydratedVotesRef.current = threadId;
	}, [threadId, threadDetail]);

	// Send message function
	const sendMessage = (text: string) => {
		if (!text.trim() || !hasWorkspace) {
			return;
		}

		originalSendMessage({ text });
	};

	// Trigger greeting from the mentor (no user message required)
	// Uses the same /chat endpoint with greeting=true flag
	// React Compiler handles memoization automatically - no useCallback needed
	const greetingTriggeredRef = useRef(false);
	const triggerGreeting = async () => {
		if (!hasWorkspace || greetingTriggeredRef.current) {
			return;
		}
		greetingTriggeredRef.current = true;

		// Use the same chat endpoint with greeting flag
		const chatApi = `${environment.serverUrl}/workspaces/${slug}/mentor/chat`;

		try {
			const response = await fetch(chatApi, {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					Authorization: `Bearer ${keycloakService.getToken()}`,
				},
				body: JSON.stringify({
					id: stableThreadId,
					greeting: true,
					// No message - greeting mode
				}),
			});

			if (!response.ok || !response.body) {
				throw new Error("Failed to fetch greeting");
			}

			// Parse the SSE stream into UIMessageChunks
			const chunkStream = parseJsonEventStream({
				stream: response.body,
				schema: uiMessageChunkSchema,
			}).pipeThrough(
				new TransformStream({
					transform(chunk, controller) {
						if (!chunk.success) {
							throw chunk.error;
						}
						controller.enqueue(chunk.value);
					},
				}),
			);

			// Read the stream and update messages in real-time
			const messageStream = readUIMessageStream({ stream: chunkStream });

			for await (const message of messageStream) {
				// Validate and update messages with the streaming greeting
				const validatedMessage = parseSingleMessage(message);
				if (validatedMessage) {
					setMessages([validatedMessage]);
				}
			}

			// Invalidate queries to refresh sidebar
			queryClient.invalidateQueries({
				queryKey: listThreadsQueryKey({ path: { workspaceSlug: slug } }),
			});
		} catch (err) {
			console.error("Greeting error:", err);
			onError?.(err instanceof Error ? err : new Error(String(err)));
		}
	};

	// Auto-trigger greeting on mount if enabled
	// biome-ignore lint/correctness/useExhaustiveDependencies: React Compiler ensures triggerGreeting is stable - no useCallback needed
	useEffect(() => {
		if (autoGreeting && hasWorkspace && messages.length === 0) {
			triggerGreeting();
		}
	}, [autoGreeting, hasWorkspace, messages.length]);

	// Vote message function
	const voteMessage = (messageId: string, isUpvoted: boolean) => {
		if (!hasWorkspace) {
			return;
		}
		const effectiveThreadId = threadId || stableThreadId;
		if (!effectiveThreadId) {
			return;
		}
		// Optimistically set local vote state
		setVoteState((prev) => ({ ...prev, [messageId]: isUpvoted }));
		voteMessageMut.mutate(
			{
				path: { workspaceSlug: slug, threadId: effectiveThreadId, messageId },
				body: { isUpvoted },
			},
			{
				onError: () => {
					// Rollback optimistic update on error
					setVoteState((prev) => {
						const next = { ...prev };
						delete next[messageId];
						return next;
					});
				},
				onSettled: () => {
					queryClient.invalidateQueries({
						queryKey: getThreadQueryKey({
							path: {
								workspaceSlug: slug,
								threadId: effectiveThreadId,
							},
						}),
					});
				},
			},
		);
	};

	// Compute loading states
	const isLoading =
		isWorkspaceLoading ||
		status === "submitted" ||
		(status === "streaming" && messages.length === 0) ||
		(!!threadId && isThreadLoading);

	// Return object without memoization to avoid dependency issues
	const result: UseMentorChatReturn = {
		// Core chat functionality
		messages,
		status,
		error,
		stop,
		regenerate,
		setMessages,
		resumeStream,
		addToolResult,
		addToolOutput,
		addToolApprovalResponse,
		id,
		clearError,

		// Send function
		sendMessage,

		// Thread management
		threadDetail,
		isThreadLoading,
		threadError: threadError as Error | null,
		threads,
		isThreadsLoading,
		currentThreadId: threadId || id,

		// Voting
		voteMessage,
		votes,

		// Loading state
		isLoading,

		// Greeting
		triggerGreeting,
	};

	return result;
}
