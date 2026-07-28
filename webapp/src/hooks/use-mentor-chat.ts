import type { UseChatHelpers } from "@ai-sdk/react";
import { useChat } from "@ai-sdk/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { DefaultChatTransport } from "ai";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
import { csrfHeaders } from "@/integrations/auth";
import { extractVotesFromThreadDetail, parseThreadMessages } from "@/lib/chat-validation";
import type { ChatMessage } from "@/lib/types";

interface UseMentorChatOptions {
	threadId?: string;
	initialMessages?: ChatMessage[];
	onFinish?: () => void;
	onError?: (error: Error) => void;
}

interface UseMentorChatReturn extends Omit<UseChatHelpers<ChatMessage>, "sendMessage"> {
	sendMessage: (text: string) => void;
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

	// Transport must be stable across renders — `useChat` reads `transport` once on mount
	// and again only when identity changes; a fresh `DefaultChatTransport` per render would
	// thrash internal state. The deps reduce to per-workspace + per-thread-id.
	const transport = useMemo(
		() =>
			new DefaultChatTransport<ChatMessage>({
				api: `${environment.serverUrl}/workspaces/${slug}/mentor/chat`,
				prepareSendMessagesRequest: ({ id, messages }) => {
					const effectiveId = id || stableThreadId;
					// Only send the latest message; backend reconstructs context from thread id.
					// Parent-message linkage lives on the server via the chat_message tree, so we
					// don't ship a `previousMessageId` (it would be a no-op the server ignores).
					const lastMessage = messages.at(-1);
					return {
						body: { id: effectiveId, message: lastMessage },
						// Cookie-session auth (ADR 0017): session cookie rides credentials:include;
						// CSRF double-submit header for this state-changing POST.
						credentials: "include",
						headers: { ...csrfHeaders() },
					};
				},
			}),
		[slug, stableThreadId],
	);

	const handleFinish = useCallback(() => {
		if (hasWorkspace) {
			queryClient.invalidateQueries({
				queryKey: listThreadsQueryKey({ path: { workspaceSlug: slug } }),
			});
		}
		if (threadId || stableThreadId) {
			queryClient.invalidateQueries({
				queryKey: getThreadQueryKey({
					path: { workspaceSlug: slug, threadId: threadId || stableThreadId || "" },
				}),
			});
		}
		onFinish?.();
	}, [hasWorkspace, queryClient, slug, threadId, stableThreadId, onFinish]);

	const handleError = useCallback(
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
		// experimental_throttle batches React re-renders, but Pi's text-delta cadence is already
		// LLM-bound (~10-30/s). Adding 100 ms batches on top makes tokens stall in chunks of
		// 1-3 deltas, breaking the "live typing" feel users expect from streaming. The webapp's
		// markdown renderer is cheap enough to handle every delta without throttling.
		transport,
		onFinish: handleFinish,
		onError: handleError,
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

	// The new-thread "Hi! I'm your mentor" greeting is rendered statically by the route
	// component when `messages.length === 0` (see Greeting.tsx). The previous implementation
	// did a separate POST to /mentor/chat with `{greeting: true}` to stream a server-generated
	// greeting; the server has no greeting flag, so the round-trip silently short-circuited as
	// "User message text is empty." The static greeting matches the UX without an API call.

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
	};

	return result;
}
