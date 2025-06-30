import { getGroupedThreadsQueryKey, getThreadOptions } from "@/api/@tanstack/react-query.gen";
import type { ChatThreadDetail } from "@/api/types.gen";
import { Chat } from "@/components/mentor";
import environment from "@/environment";
import { keycloakService } from "@/integrations/auth";
import { v4 as uuidv4 } from "uuid";

import { useChat } from "@ai-sdk/react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, useRouterState } from "@tanstack/react-router";
import { DefaultChatTransport } from "ai";
import { useEffect, useRef } from "react";

export const Route = createFileRoute("/_authenticated/mentor/$threadId")({
	component: ThreadContainer,
});

function ThreadContainer() {
	const { threadId } = Route.useParams();

	// Fetch the thread details
	const {
		data: threadDetail,
		isLoading,
		error: fetchError,
	} = useQuery({
		...getThreadOptions({
			path: { threadId },
		}),
	});

	// Show loading state while fetching thread
	if (!threadDetail && isLoading) {
		return (
			<div className="h-[calc(100vh-4rem)] max-w-5xl mx-auto p-6 flex items-center justify-center">
				<div className="text-center">
					<div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto mb-4" />
					<p className="text-muted-foreground">Loading conversation...</p>
				</div>
			</div>
		);
	}

	// Show error state if thread fetch failed
	if (!threadDetail && fetchError) {
		return (
			<div className="h-[calc(100vh-4rem)] max-w-5xl mx-auto p-6 flex items-center justify-center">
				<div className="text-center">
					<p className="text-destructive mb-4">
						Failed to load conversation. Thread may not exist or you don't have
						access to it.
					</p>
					<p className="text-sm text-muted-foreground">
						Try refreshing the page or go back to the main chat.
					</p>
				</div>
			</div>
		);
	}

	// Show error if no thread data
	if (!threadDetail) {
		return (
			<div className="h-[calc(100vh-4rem)] max-w-5xl mx-auto p-6 flex items-center justify-center">
				<div className="text-center">
					<p className="text-muted-foreground">Conversation not found.</p>
				</div>
			</div>
		);
	}

	// Once we have thread details, render the chat component
	return <ChatContainer threadId={threadId} threadDetail={threadDetail} />;
}

/**
 * Chat component that initializes useChat with the loaded thread details.
 * Only rendered after thread details have been successfully loaded.
 */
function ChatContainer({
	threadId,
	threadDetail,
}: {
	threadId: string;
	threadDetail: ChatThreadDetail;
}) {
	const queryClient = useQueryClient();
	const pendingMessage = useRouterState({ select: s => s.location.state.pendingMentorMessage });
	const sentPendingMessage = useRef(false);

	// Initialize useChat for new messages in this thread
	const { error, status, sendMessage, messages, regenerate, stop } = useChat({
		generateId: () => uuidv4(),
		id: threadId, // Use thread ID as the chat ID
		transport: new DefaultChatTransport({
			api: `${environment.serverUrl}/mentor/chat`,
			headers: {
				Authorization: `Bearer ${keycloakService.getToken()}`,
			},
		}),
		// @ts-ignore
		messages: threadDetail.messages || [], // Use existing messages, safely available here
		onFinish: () => {
			queryClient.invalidateQueries({
				queryKey: getGroupedThreadsQueryKey(),
			});
		}
	});

	useEffect(() => {
		// If there's a pending message from navigation state, add it to the chat
		if (pendingMessage && !sentPendingMessage.current && messages.length === 0) {
			sendMessage({ text: pendingMessage });
			sentPendingMessage.current = true; // Prevent sending it again
		}
	});

	const isStreaming = status === "streaming" || status === "submitted";

	const handleSendMessage = (text: string) => {
		sendMessage({ text });
	};

	const handleStop = () => {
		stop();
	};

	const handleRegenerate = () => {
		regenerate();
	};

	return (
		<Chat
			messages={messages}
			onSendMessage={handleSendMessage}
			onStop={handleStop}
			onRegenerate={handleRegenerate}
			isLoading={isStreaming}
			error={error}
			disabled={status === "submitted"}
			placeholder="Continue the conversation..."
			className="h-[calc(100%-8rem)]"
		/>
	);
}
