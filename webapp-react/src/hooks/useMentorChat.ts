import { useChat } from "@ai-sdk/react";
import type { UIMessage } from "@ai-sdk/react";
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
	initialMessages?: UIMessage[];
	onFinish?: () => void;
	onError?: (error: Error) => void;
}

interface UseMentorChatReturn
	extends Omit<UseChatHelpers<UIMessage>, "sendMessage"> {
	sendMessage: (text: string) => void;
	threadDetail: ChatThreadDetail | undefined;
	isThreadLoading: boolean;
	threadError: Error | null;
	groupedThreads: ChatThreadGroup[] | undefined;
	isGroupedThreadsLoading: boolean;
	isLoading: boolean;
	currentThreadId: string | undefined;
	voteMessage: (messageId: string, isUpvoted: boolean) => void;
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

	// Create stable transport configuration
	const stableTransport = useMemo(
		() =>
			new DefaultChatTransport({
				api: `${environment.serverUrl}/mentor/chat`,
				// Always attach a fresh token per request
				prepareSendMessagesRequest: ({ id, messages }) => {
					const effectiveId = id || stableThreadId;
					return {
						body: {
							id: effectiveId,
							messages,
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
		(_options: { message: UIMessage }) => {
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
	} = useChat({
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
		setMessages(threadDetail.messages as unknown as UIMessage[]);
		hydratedRef.current = threadId;
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [threadId, threadDetail?.messages, status, setMessages]);

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
			voteMessageMut.mutate({
				path: { messageId },
				body: { isUpvoted },
			});
		},
		[voteMessageMut],
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

		// Loading state
		isLoading,
	};

	return result;
}
