import type { UseChatHelpers } from "@ai-sdk/react";
import { useChat } from "@ai-sdk/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { DefaultChatTransport } from "ai";
import { useEffect, useRef, useState } from "react";
import { v4 as uuidv4 } from "uuid";
import {
	getGroupedThreadsOptions,
	getGroupedThreadsQueryKey,
	getThreadOptions,
	getThreadQueryKey,
	voteMessageMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	ChatMessageVote,
	ChatThreadDetail,
	ChatThreadGroup,
	Document,
} from "@/api/types.gen";
import environment from "@/environment";
import { keycloakService } from "@/integrations/auth";
import type { ChatMessage } from "@/lib/types";
import { useArtifactStore } from "@/stores/artifact-store";
import { useDocumentArtifact } from "./useDocumentArtifact";

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
	// Minimal overlay actions exposed to parent when needed
	openArtifactForDocument: (document: Document, boundingBox: DOMRect) => void;
	openArtifactById: (documentId: string, boundingBox: DOMRect) => Promise<void>;
	closeArtifact: () => void;
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

	// Fetch thread detail if threadId is provided; avoid immediate refetch on mount
	const threadQueryKey = getThreadQueryKey({
		path: { threadId: threadId || "" },
	});
	const threadQuery = useQuery({
		...getThreadOptions({ path: { threadId: threadId || "" } }),
		enabled: !!threadId,
		initialData: () => queryClient.getQueryData(threadQueryKey),
		initialDataUpdatedAt: Date.now(),
		staleTime: 60_000,
		refetchOnMount: false,
		refetchOnWindowFocus: false,
		refetchOnReconnect: false,
	});

	const {
		data: threadDetail,
		isLoading: isThreadLoading,
		error: threadError,
	} = threadQuery;

	// Fetch grouped threads for sidebar/navigation; avoid immediate refetch on mount
	const { data: groupedThreads, isLoading: isGroupedThreadsLoading } = useQuery(
		{
			...getGroupedThreadsOptions(),
			initialData: () => queryClient.getQueryData(getGroupedThreadsQueryKey()),
			initialDataUpdatedAt: Date.now(),
			staleTime: 60_000,
			refetchOnMount: false,
			refetchOnWindowFocus: false,
			refetchOnReconnect: false,
		},
	);

	// Vote message mutation
	const voteMessageMut = useMutation(voteMessageMutation());

	// Optimistic votes state: messageId -> isUpvoted
	const [voteState, setVoteState] = useState<
		Record<string, boolean | undefined>
	>({});
	const votes: ChatMessageVote[] = Object.entries(voteState).map(
		([messageId, isUpvoted]) => ({
			messageId,
			isUpvoted,
		}),
	);

	// ---------------------------
	// Artifact/document state
	// ---------------------------

	// When a document overlay is open, maintain a streaming-aware doc controller
	const visibleOverlay = useArtifactStore((s) => s.getVisibleArtifact());
	const artifactDoc = useDocumentArtifact({
		documentId: visibleOverlay?.artifactId?.split(":")[1] ?? "",
	});

	// Versions query for the active document
	// No local version/save handling here; containers manage per-artifact logic

	const openArtifactForDocument = (
		document: Document,
		boundingBox: DOMRect,
	) => {
		// Delegate to global overlay store
		useArtifactStore
			.getState()
			.openArtifact(`text:${document.id}`, boundingBox, document.title);
	};

	const openArtifactById = async (documentId: string, boundingBox: DOMRect) => {
		// Optimistically open overlay; data will be fetched by overlay hook
		useArtifactStore
			.getState()
			.openArtifact(`text:${documentId}`, boundingBox, "Document");
	};

	const closeArtifact = () => {
		useArtifactStore.getState().closeArtifact();
	};

	// No save/version APIs exposed; handled in TextArtifactContainer

	// Create stable transport configuration
	const stableTransport = new DefaultChatTransport({
		api: `http://localhost:8000/mentor/chat`,
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
	});

	// Create stable onFinish callback
	const stableOnFinish = (_options: { message: ChatMessage }) => {
		queryClient.invalidateQueries({ queryKey: getGroupedThreadsQueryKey() });
		if (threadId || stableThreadId) {
			queryClient.invalidateQueries({
				queryKey: getThreadQueryKey({
					path: { threadId: threadId || stableThreadId || "" },
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
		id,
	} = useChat<ChatMessage>({
		id: stableThreadId, // Use stable ID that never changes
		messages: initialMessages, // Start with initial messages only - backend will provide thread history
		generateId: () => uuidv4(), // Generate UUID for all messages
		experimental_throttle: 100, // Add throttling for smoother streaming
		transport: stableTransport,
		onFinish: stableOnFinish,
		onError: stableOnError,
		onData: (dataPart) => {
			if (
				dataPart.type === "data-document-create" ||
				dataPart.type === "data-document-update" ||
				dataPart.type === "data-document-delta" ||
				dataPart.type === "data-document-finish"
			) {
				artifactDoc.onStreamPart(dataPart);
			}
		},
	});

	// Hydrate thread messages once when loaded and not streaming
	const hydratedRef = useRef<string | null>(null);
	useEffect(() => {
		if (!threadId) return;
		if (hydratedRef.current === threadId) return;
		if (status === "streaming" || status === "submitted") return;
		if (!threadDetail?.messages) return;
		setMessages(threadDetail.messages as unknown as ChatMessage[]);
		hydratedRef.current = threadId;
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
	const sendMessage = (text: string) => {
		if (!text.trim()) {
			return;
		}

		originalSendMessage({ text });
	}; // Vote message function
	const voteMessage = (messageId: string, isUpvoted: boolean) => {
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
	};

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
		clearError,

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

		// Overlay actions
		openArtifactForDocument,
		openArtifactById,
		closeArtifact,
	};

	return result;
}
