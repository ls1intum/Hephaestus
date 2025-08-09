import type { ChatMessageVote } from "@/api/types.gen";
import type { ChatMessage } from "@/lib/types";
import { useChat } from "@ai-sdk/react";
import type { UseChatHelpers } from "@ai-sdk/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { DefaultChatTransport } from "ai";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { v4 as uuidv4 } from "uuid";

import {
	getGroupedThreadsOptions,
	getGroupedThreadsQueryKey,
	getThreadOptions,
	getThreadQueryKey,
	voteMessageMutation,
} from "@/api/@tanstack/react-query.gen";
import type { ChatThreadDetail, ChatThreadGroup } from "@/api/types.gen";
import environment from "@/environment";
import { keycloakService } from "@/integrations/auth";

interface UseMentorChatOptions {
	threadId?: string;
	initialMessages?: ChatMessage[];
	onFinish?: () => void;
	onError?: (error: Error) => void;
}

interface UseMentorChatReturn
	extends Omit<UseChatHelpers<ChatMessage>, "sendMessage"> {
	sendMessage: (text: string) => void;
	threadDetail: ChatThreadDetail | undefined;
	isThreadLoading: boolean;
	threadError: Error | null;
	groupedThreads: ChatThreadGroup[] | undefined;
	isGroupedThreadsLoading: boolean;
	isLoading: boolean;
	currentThreadId: string | undefined;
	voteMessage: (messageId: string, isUpvoted: boolean) => void;
	votes: ChatMessageVote[];
}

export function useMentorChat({
	threadId,
	initialMessages = [],
	onFinish,
	onError,
}: UseMentorChatOptions): UseMentorChatReturn {
	const queryClient = useQueryClient();

	// Generate a stable chat ID for this hook lifecycle
	const [stableThreadId] = useState(() => threadId || uuidv4());

	// Fetch thread detail if threadId is provided
	const threadQuery = useQuery({
		...getThreadOptions({ path: { threadId: threadId || "" } }),
		enabled: !!threadId,
	});

	const {
		data: threadDetail,
		isLoading: isThreadLoading,
		error: threadError,
	} = threadQuery;

	// Fetch grouped threads for sidebar/navigation
	const { data: groupedThreads, isLoading: isGroupedThreadsLoading } = useQuery(
		getGroupedThreadsOptions(),
	);

	// Vote message mutation
	const voteMessageMut = useMutation(voteMessageMutation());

	// Optimistic votes state: messageId -> isUpvoted
	const [voteState, setVoteState] = useState<
		Record<string, boolean | undefined>
	>({});
	const votes: ChatMessageVote[] = useMemo(() => {
		return Object.entries(voteState).map(([messageId, isUpvoted]) => ({
			messageId,
			isUpvoted,
		}));
	}, [voteState]);

	// Create stable transport configuration
	const stableTransport = useMemo(
		() =>
			new DefaultChatTransport({
				api: `${environment.serverUrl}/mentor/chat`,
				// Always attach a fresh token per request
				prepareSendMessagesRequest: ({ id, messages }) => {
					const effectiveId = id || stableThreadId;
					// Only send the latest message; backend reconstructs context from thread ID
					const lastMessage = messages.at(-1);
					// Determine previous message ID from current local state (selected leaf or last message)
					const prev =
						messages.length > 1 ? messages[messages.length - 2]?.id : undefined;
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
			}),
		[stableThreadId],
	);

	// Create stable onFinish callback
	const stableOnFinish = useCallback(
		(_options: { message: ChatMessage }) => {
			queryClient.invalidateQueries({ queryKey: getGroupedThreadsQueryKey() });
			if (threadId || stableThreadId) {
				queryClient.invalidateQueries({
					queryKey: getThreadQueryKey({
						path: { threadId: threadId || stableThreadId || "" },
					}),
				});
			}
			onFinish?.();
		},
		[queryClient, threadId, stableThreadId, onFinish],
	);

	// Create stable onError callback
	const stableOnError = useCallback(
		(error: Error) => {
			onError?.(error);
		},
		[onError],
	);

	const {
		messages,
		sendMessage: originalSendMessage,
		status,
		stop,
		regenerate,
		error,
		setMessages,
		resumeStream,
		addToolResult,
		id,
	} = useChat<ChatMessage>({
		id: stableThreadId, // Use stable ID that never changes
		messages: initialMessages, // Start with initial messages only - backend will provide thread history
		generateId: () => uuidv4(), // Generate UUID for all messages
		// experimental_throttle: 100, // Add throttling for smoother streaming
		transport: stableTransport,
		onFinish: stableOnFinish,
		onError: stableOnError,
	});

	// Hydrate existing thread messages when loaded
	const hydratedRef = useRef<string | null>(null);
	useEffect(() => {
		if (!threadId) return; // only hydrate for existing threads
		if (!threadDetail?.messages) return;
		// Avoid re-hydrating the same thread multiple times
		if (hydratedRef.current === threadId) return;

		// Only hydrate when not actively streaming/submitted
		if (status === "streaming" || status === "submitted") return;

		// Replace the local chat state with server messages
		// No transformation needed since backend now uses direct JsonNode objects
		setMessages(threadDetail.messages as unknown as ChatMessage[]);
		hydratedRef.current = threadId;
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [threadId, threadDetail?.messages, status, setMessages]);

	// Hydrate votes from server thread detail when available
	const hydratedVotesRef = useRef<string | null>(null);
	useEffect(() => {
		if (!threadId) return; // only hydrate for existing threads
		// We rely on the backend including votes in the thread detail DTO
		const serverVotes = (
			threadDetail as unknown as {
				votes?: Array<{ messageId?: string; isUpvoted?: boolean }>;
			}
		)?.votes;
		if (!serverVotes) return;
		if (hydratedVotesRef.current === threadId) return;

		const next: Record<string, boolean | undefined> = {};
		for (const v of serverVotes) {
			if (v?.messageId) next[v.messageId] = v.isUpvoted ?? undefined;
		}
		setVoteState(next);
		hydratedVotesRef.current = threadId;
	}, [threadId, threadDetail]);

	// Send message function
	const sendMessage = useCallback(
		(text: string) => {
			if (!text.trim()) {
				return;
			}

			originalSendMessage({ text });
		},
		[originalSendMessage],
	); // Vote message function
	const voteMessage = useCallback(
		(messageId: string, isUpvoted: boolean) => {
			// Optimistically set local vote state
			setVoteState((prev) => ({ ...prev, [messageId]: isUpvoted }));
			voteMessageMut.mutate(
				{
					path: { messageId },
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
						if (threadId || stableThreadId) {
							queryClient.invalidateQueries({
								queryKey: getThreadQueryKey({
									path: { threadId: threadId || stableThreadId || "" },
								}),
							});
						}
					},
				},
			);
		},
		[voteMessageMut, queryClient, threadId, stableThreadId],
	);

	// Compute loading states
	const isLoading =
		status === "submitted" ||
		(status === "streaming" && messages.length === 0) ||
		(!!threadId && isThreadLoading);

	// Return object without memoization to avoid dependency issues
	const result = {
		// Core chat functionality
		messages,
		status,
		error,
		stop,
		regenerate,
		setMessages,
		resumeStream,
		addToolResult,
		id,

		// Send function
		sendMessage,

		// Thread management
		threadDetail,
		isThreadLoading,
		threadError,
		groupedThreads,
		isGroupedThreadsLoading,
		currentThreadId: threadId || id,

		// Voting
		voteMessage,
		votes,

		// Loading state
		isLoading,
	};

	return result;
}
