import type { UseChatHelpers } from "@ai-sdk/react";
import { useChat } from "@ai-sdk/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { DefaultChatTransport } from "ai";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { v4 as uuidv4 } from "uuid";
import {
	getDocumentOptions,
	getDocumentQueryKey,
	getDocumentVersionsOptions,
	getDocumentVersionsQueryKey,
	getGroupedThreadsOptions,
	getGroupedThreadsQueryKey,
	getThreadOptions,
	getThreadQueryKey,
	updateDocumentMutation,
	voteMessageMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	ChatMessageVote,
	ChatThreadDetail,
	ChatThreadGroup,
	Document,
} from "@/api/types.gen";
import type { UIArtifact } from "@/components/mentor/Artifact";
import environment from "@/environment";
import { keycloakService } from "@/integrations/auth";
import type { ChatMessage } from "@/lib/types";

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
	// Artifact/document state and actions
	artifact: UIArtifact | null;
	artifactDocuments: Document[];
	artifactCurrentVersionIndex: number;
	artifactIsCurrentVersion: boolean;
	artifactIsContentDirty: boolean;
	artifactMode: "edit" | "diff";
	openArtifactForDocument: (document: Document, boundingBox: DOMRect) => void;
	openArtifactById: (documentId: string, boundingBox: DOMRect) => Promise<void>;
	closeArtifact: () => void;
	saveArtifactContent: (updatedContent: string, debounce: boolean) => void;
	changeArtifactVersion: (type: "next" | "prev" | "toggle" | "latest") => void;
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
	const votes: ChatMessageVote[] = useMemo(() => {
		return Object.entries(voteState).map(([messageId, isUpvoted]) => ({
			messageId,
			isUpvoted,
		}));
	}, [voteState]);

	// ---------------------------
	// Artifact/document state
	// ---------------------------
	const [artifact, setArtifact] = useState<UIArtifact | null>(null);
	const [activeDocumentId, setActiveDocumentId] = useState<string | null>(null);
	const [selectedVersionIndex, setSelectedVersionIndex] = useState<number>(-1);
	const [artifactMode, setArtifactMode] = useState<"edit" | "diff">("edit");
	const [isContentDirty, setIsContentDirty] = useState<boolean>(false);

	// Versions query for the active document
	const versionsQueryKey = useMemo(
		() => getDocumentVersionsQueryKey({ path: { id: activeDocumentId ?? "" } }),
		[activeDocumentId],
	);
	const { data: versionsPage } = useQuery({
		...getDocumentVersionsOptions({ path: { id: activeDocumentId ?? "" } }),
		enabled: !!activeDocumentId,
		initialData: () =>
			activeDocumentId ? queryClient.getQueryData(versionsQueryKey) : undefined,
		staleTime: 60_000,
		refetchOnMount: false,
		refetchOnWindowFocus: false,
		refetchOnReconnect: false,
	});

	const artifactDocuments: Document[] = useMemo(
		() => versionsPage?.content ?? [],
		[versionsPage?.content],
	);

	// Keep selected index at latest when opening/loading
	useEffect(() => {
		if (!artifactDocuments || artifactDocuments.length === 0) return;
		const latest = artifactDocuments[artifactDocuments.length - 1];
		setSelectedVersionIndex(artifactDocuments.length - 1);
		setArtifact((prev) =>
			prev
				? {
						...prev,
						title: latest.title,
						content: latest.content ?? prev.content,
					}
				: prev,
		);
	}, [artifactDocuments]);

	const isCurrentVersion = useMemo(
		() =>
			artifactDocuments.length > 0 &&
			selectedVersionIndex === artifactDocuments.length - 1,
		[artifactDocuments.length, selectedVersionIndex],
	);

	// Save mutation (creates a new version)
	const updateDocMut = useMutation(updateDocumentMutation());
	const saveTimerRef = useRef<NodeJS.Timeout | null>(null);

	const openArtifactForDocument = useCallback(
		(document: Document, boundingBox: DOMRect) => {
			const kind: UIArtifact["kind"] =
				document.kind === "TEXT" ? "text" : "text";
			const newArtifact: UIArtifact = {
				title: document.title,
				documentId: document.id,
				kind,
				content: document.content ?? "",
				isVisible: true,
				status: "idle",
				boundingBox: {
					top: boundingBox.top,
					left: boundingBox.left,
					width: boundingBox.width,
					height: boundingBox.height,
				},
			};
			setArtifact(newArtifact);
			setActiveDocumentId(document.id);
			setArtifactMode("edit");
			setIsContentDirty(false);
		},
		[],
	);

	const openArtifactById = useCallback(
		async (documentId: string, boundingBox: DOMRect) => {
			try {
				const data = await queryClient.fetchQuery({
					...getDocumentOptions({ path: { id: documentId } }),
					queryKey: getDocumentQueryKey({ path: { id: documentId } }),
				});
				const docToOpen = data as unknown as Document;
				openArtifactForDocument(docToOpen, boundingBox);
			} catch (e) {
				// eslint-disable-next-line no-console
				console.error("Failed to fetch document", e);
			}
		},
		[openArtifactForDocument, queryClient],
	);

	const closeArtifact = useCallback(() => {
		// Trigger exit animation then cleanup
		setArtifact((prev) => (prev ? { ...prev, isVisible: false } : prev));
		setTimeout(() => {
			setArtifact(null);
			setActiveDocumentId(null);
			setSelectedVersionIndex(-1);
			setArtifactMode("edit");
			setIsContentDirty(false);
		}, 600);
	}, []);

	const changeArtifactVersion = useCallback(
		(type: "next" | "prev" | "toggle" | "latest") => {
			if (!artifactDocuments.length) return;
			switch (type) {
				case "next":
					setSelectedVersionIndex((idx) =>
						Math.min(idx + 1, artifactDocuments.length - 1),
					);
					break;
				case "prev":
					setSelectedVersionIndex((idx) => Math.max(idx - 1, 0));
					break;
				case "latest":
					setSelectedVersionIndex(artifactDocuments.length - 1);
					setArtifactMode("edit");
					break;
				case "toggle":
					setArtifactMode((m) => (m === "edit" ? "diff" : "edit"));
					break;
			}
		},
		[artifactDocuments.length],
	);

	const doPersistContent = useCallback(
		async (updatedContent: string) => {
			if (!activeDocumentId || !artifact) return;
			try {
				await updateDocMut.mutateAsync({
					path: { id: activeDocumentId },
					// UpdateDocumentRequest shape mirrors Create: { title, content, kind }
					body: {
						title: artifact.title,
						content: updatedContent,
						// Backend expects enum 'TEXT'
						// biome-ignore lint/suspicious/noExplicitAny: generator type missing
					} as any,
				});
				setIsContentDirty(false);
				// Invalidate queries and jump to latest when refreshed
				await Promise.all([
					queryClient.invalidateQueries({
						queryKey: getDocumentVersionsQueryKey({
							path: { id: activeDocumentId },
						}),
					}),
					queryClient.invalidateQueries({
						queryKey: getDocumentQueryKey({ path: { id: activeDocumentId } }),
					}),
				]);
				setSelectedVersionIndex((len) =>
					artifactDocuments.length > 0 ? artifactDocuments.length - 1 : len,
				);
			} catch (e) {
				// eslint-disable-next-line no-console
				console.error("Failed to save document", e);
			}
		},
		[
			activeDocumentId,
			artifact,
			updateDocMut,
			queryClient,
			artifactDocuments.length,
		],
	);

	const saveArtifactContent = useCallback(
		(updatedContent: string, debounce: boolean) => {
			// Update visible content immediately for UX
			setArtifact((prev) =>
				prev ? { ...prev, content: updatedContent } : prev,
			);
			setIsContentDirty(true);

			// Only allow edits on latest version; if not latest, jump to latest first
			if (!isCurrentVersion) {
				setSelectedVersionIndex(
					artifactDocuments.length > 0 ? artifactDocuments.length - 1 : -1,
				);
			}

			if (debounce) {
				if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
				saveTimerRef.current = setTimeout(() => {
					void doPersistContent(updatedContent);
				}, 800);
			} else {
				void doPersistContent(updatedContent);
			}
		},
		[doPersistContent, isCurrentVersion, artifactDocuments.length],
	);

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

		// Artifact/document state and actions
		artifact,
		artifactDocuments,
		artifactCurrentVersionIndex: selectedVersionIndex,
		artifactIsCurrentVersion: isCurrentVersion,
		artifactIsContentDirty: isContentDirty,
		artifactMode,
		openArtifactForDocument,
		openArtifactById,
		closeArtifact,
		saveArtifactContent,
		changeArtifactVersion,
	};

	return result;
}
