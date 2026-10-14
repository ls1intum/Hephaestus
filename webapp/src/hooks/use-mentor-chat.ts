import { Chat, type UseChatHelpers, useChat } from "@ai-sdk/react";
import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type ChatInit, DefaultChatTransport } from "ai";
import { useEffect, useState } from "react";
import { v4 as uuidv4 } from "uuid";

import {
	getThreadOptions,
	getThreadQueryKey,
	listThreadsQueryKey,
	voteMutation,
} from "@/api/@tanstack/react-query.gen";
import type { ChatMessageVote } from "@/api/types.gen";
import environment from "@/environment";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { type MentorTurnState, mentorStatus, mentorTurn } from "@/lib/chat-validation";
import { hasText } from "@/lib/text";
import type { ChatMessage, ChatTurn } from "@/lib/types";
import { csrfHeaders } from "@/runtime/auth/auth-client";
import { csrfFetch } from "@/runtime/auth/csrf";
import { userViewHeaders } from "@/runtime/user-view/session";

/**
 * `addToolResult` is dropped because it is the SDK's deprecated alias for the forwarded
 * `addToolOutput`, and `status` because `turn` reads it together with the server's own state.
 */
interface UseMentorChatReturn extends Omit<
	UseChatHelpers<ChatMessage>,
	"sendMessage" | "addToolResult" | "status"
> {
	sendMessage: (text: string) => void;
	/** Starts a separate floating conversation, leaving the previous stored thread available. */
	startNewChat: () => Promise<void>;
	/** Answers the latest prompt again, replacing the reply that failed. */
	retry: () => void;
	/** Drops the message and everything after it, then sends `text` as a new prompt. */
	editMessage: (messageId: string, text: string) => void;
	isLoading: boolean;
	currentThreadId: string | undefined;
	voteMessage: (messageId: string, isUpvoted: boolean) => void;
	votes: ChatMessageVote[];
	turn: ChatTurn;
}

/**
 * One conversation as the SDK's own `Chat`, with what the conversation keeps beside its messages.
 * Whoever holds it decides how long a reply outlives the view that asked for it: the panel's lives
 * above the routes, so the full chat page can take it over mid-reply.
 */
export interface MentorChatSession {
	workspaceSlug: string;
	chat: Chat<ChatMessage>;
	/**
	 * The server reports a turn's state on a transient `data-mentor-status` part, which `Chat` hands to
	 * `onData` and never stores, so the latest one is held here until the next turn starts.
	 */
	serverState: MentorTurnState | undefined;
	setServerState: (state: MentorTurnState | undefined) => void;
	/**
	 * `regenerate` drops the failed reply before it posts, and only accepts a reply still in the list,
	 * so after a retry refused before a new reply started, the target travels as request metadata.
	 */
	retryTarget: string | undefined;
	setRetryTarget: (messageId: string | undefined) => void;
	/**
	 * Overlaid on the server's record: an entry wins while its mutation is in flight, so dropping it on
	 * failure falls straight back to the server without a second request.
	 */
	castVotes: ReadonlyMap<string, boolean>;
	voteMessage: (messageId: string, isUpvoted: boolean) => void;
	/** Starts a separate conversation, leaving the previous stored thread available. */
	startNewChat: () => Promise<void>;
}

interface MentorChatSessionOptions {
	workspaceSlug: string;
	threadId?: string;
	/** The stored conversation, read before the chat mounts: `Chat` takes it once, on creation. */
	initialMessages?: ChatMessage[];
	/** A new value starts a new conversation, as a change of account or workspace must. */
	resetKey?: string;
}

export function useMentorChatSession({
	workspaceSlug,
	threadId,
	initialMessages = [],
	resetKey,
}: MentorChatSessionOptions): MentorChatSession {
	const queryClient = useQueryClient();
	// Stamped with the conversation it came from: a replaced `Chat` can still report a part before its
	// stop lands, and that part says nothing about the conversation that replaced it.
	const [streamed, setStreamed] = useState<{ chatId: string; state: MentorTurnState }>();
	const [retryTarget, setRetryTarget] = useState<string>();
	const [castVotes, setCastVotes] = useState<ReadonlyMap<string, boolean>>(() => new Map());
	const conversation = (id: string, messages: ChatMessage[]) =>
		new Chat<ChatMessage>(
			mentorChatInit({
				queryClient,
				workspaceSlug,
				id,
				messages,
				onState: (state) => setStreamed({ chatId: id, state }),
			}),
		);
	const [chat, setChat] = useState(() => conversation(threadId ?? uuidv4(), initialMessages));

	const [owner, setOwner] = useState(resetKey);
	if (owner !== resetKey) {
		setOwner(resetKey);
		setChat(conversation(uuidv4(), []));
		setStreamed(undefined);
		setRetryTarget(undefined);
		setCastVotes(new Map());
	}

	// The owner, not a view, ends the reply: a replaced or unmounted conversation has nobody left to
	// show it to. `useChat` only unsubscribes, and `stop` aborts nothing while no reply is open.
	useEffect(
		() => () => {
			void chat.stop();
		},
		[chat],
	);

	const voteMessageMut = useMutation(voteMutation());

	const voteMessage = (messageId: string, isUpvoted: boolean) => {
		if (!hasText(workspaceSlug)) {
			return;
		}
		const path = { workspaceSlug, threadId: chat.id };
		setCastVotes((prev) => new Map(prev).set(messageId, isUpvoted));
		voteMessageMut.mutate(
			{ path: { ...path, messageId }, body: { isUpvoted } },
			{
				onError: () => {
					setCastVotes((prev) => {
						const next = new Map(prev);
						next.delete(messageId);
						return next;
					});
				},
				onSettled: () => {
					void queryClient.invalidateQueries({ queryKey: getThreadQueryKey({ path }) });
				},
			},
		);
	};

	const startNewChat = async () => {
		await chat.stop();
		setStreamed(undefined);
		setRetryTarget(undefined);
		setCastVotes(new Map());
		setChat(conversation(uuidv4(), []));
	};

	return {
		workspaceSlug,
		chat,
		serverState: streamed?.chatId === chat.id ? streamed.state : undefined,
		setServerState: (state) =>
			setStreamed(state === undefined ? undefined : { chatId: chat.id, state }),
		retryTarget,
		setRetryTarget,
		castVotes,
		voteMessage,
		startNewChat,
	};
}

/**
 * One stored conversation. A conversation is read once and then lives in its `Chat`, so neither a
 * remount nor a focus reads it again.
 */
export function mentorThreadOptions(workspaceSlug: string, threadId: string) {
	return {
		...getThreadOptions({ path: { workspaceSlug, threadId } }),
		staleTime: 60_000,
		refetchOnMount: false,
		refetchOnWindowFocus: false,
		refetchOnReconnect: false,
	};
}

/** A view of `session`'s conversation; the view can come and go while a reply streams on. */
export function useMentorChat(
	session: MentorChatSession,
	{ threadId }: { threadId?: string } = {},
): UseMentorChatReturn {
	const { isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const { workspaceSlug, chat, serverState, setServerState, retryTarget, setRetryTarget } = session;
	const hasWorkspace = hasText(workspaceSlug);

	const { data: threadDetail, isLoading: isThreadLoading } = useQuery({
		...mentorThreadOptions(workspaceSlug, threadId ?? ""),
		enabled: hasText(threadId) && hasWorkspace,
	});

	const voteState: Record<string, boolean> = { ...threadDetail?.votes };
	for (const [messageId, isUpvoted] of session.castVotes) {
		voteState[messageId] = isUpvoted;
	}

	// `updatedAt` stays unset: it is the server's stamp on a stored vote, and no surface renders it.
	const votes: ChatMessageVote[] = Object.entries(voteState).map(([messageId, isUpvoted]) => ({
		messageId,
		isUpvoted,
	}));

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
		addToolOutput,
		addToolApprovalResponse,
		id,
	} = useChat<ChatMessage>({ chat });

	const sendMessage = (text: string) => {
		if (!text.trim() || !hasWorkspace) {
			return;
		}

		setRetryTarget(undefined);
		setServerState(undefined);
		void originalSendMessage({ text });
	};

	// Not `sendMessage({ text, messageId })`, which resends the edited message under its own id: the
	// server has stored that id already and refuses the turn as a duplicate.
	const editMessage = (messageId: string, text: string) => {
		const index = messages.findIndex((message) => message.id === messageId);
		if (index === -1) {
			return;
		}
		setMessages(messages.slice(0, index));
		sendMessage(text);
	};

	const retry = () => {
		setServerState(undefined);
		const last = messages.at(-1);
		if (last?.role === "assistant") {
			setRetryTarget(last.id);
			void regenerate({ messageId: last.id });
			return;
		}
		void regenerate(retryTarget === undefined ? undefined : { metadata: { retryOf: retryTarget } });
	};

	// No greeting request: the server has no greeting flag, so a POST asking for one comes back
	// "User message text is empty." `Chat` renders a static greeting instead.

	const isLoading =
		isWorkspaceLoading ||
		status === "submitted" ||
		(status === "streaming" && messages.length === 0) ||
		(hasText(threadId) && isThreadLoading);

	return {
		messages,
		error,
		stop,
		regenerate,
		setMessages,
		resumeStream,
		addToolOutput,
		addToolApprovalResponse,
		id,
		clearError,
		sendMessage,
		startNewChat: session.startNewChat,
		retry,
		editMessage,
		currentThreadId: threadId ?? id,
		voteMessage: session.voteMessage,
		votes,
		isLoading,
		turn: mentorTurn(status, serverState),
	};
}

function mentorChatInit({
	queryClient,
	workspaceSlug,
	id,
	messages,
	onState,
}: {
	queryClient: QueryClient;
	workspaceSlug: string;
	id: string;
	messages: ChatMessage[];
	onState: (state: MentorTurnState) => void;
}): ChatInit<ChatMessage> {
	return {
		id,
		messages,
		generateId: () => uuidv4(),
		// No throttle: the mentor's delta cadence is already LLM-bound, so batching re-renders on top of
		// it makes tokens arrive in visible clumps instead of typing out. The markdown renderer is cheap
		// enough to take every delta.
		transport: new DefaultChatTransport<ChatMessage>({
			api: `${environment.serverUrl}/workspaces/${workspaceSlug}/mentor/chat`,
			fetch: csrfFetch,
			prepareSendMessagesRequest: ({
				id: requestId,
				messages: sent,
				trigger,
				messageId,
				requestMetadata,
			}) => ({
				// Only the latest message travels: the server rebuilds context and parent linkage from the
				// thread id, so anything else in `messages` is bytes it ignores. A custom body replaces the
				// SDK's default one, so the trigger and the replaced reply have to be carried over by hand.
				body: {
					id: requestId || id,
					message: sent.at(-1),
					trigger,
					messageId: messageId ?? retriedReplyOf(requestMetadata),
				},
				// Cookie-session auth (ADR 0017): session cookie rides credentials:include;
				// CSRF double-submit header for this state-changing POST.
				credentials: "include",
				headers: { ...csrfHeaders(), ...userViewHeaders() },
			}),
		}),
		onFinish: () => {
			if (hasText(workspaceSlug)) {
				void queryClient.invalidateQueries({
					queryKey: listThreadsQueryKey({ path: { workspaceSlug } }),
				});
			}
			void queryClient.invalidateQueries({
				queryKey: getThreadQueryKey({ path: { workspaceSlug, threadId: id } }),
			});
		},
		onData: (part) => {
			const state = mentorStatus(part);
			if (state !== undefined) {
				onState(state);
			}
		},
	};
}

function retriedReplyOf(requestMetadata: unknown): string | undefined {
	return typeof requestMetadata === "object" &&
		requestMetadata !== null &&
		"retryOf" in requestMetadata &&
		typeof requestMetadata.retryOf === "string"
		? requestMetadata.retryOf
		: undefined;
}
