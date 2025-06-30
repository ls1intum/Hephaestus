import { getThreadOptions } from "@/api/@tanstack/react-query.gen";
import type { ChatThreadDetail } from "@/api/types.gen";
import { Chat } from "@/components/mentor";
import { ComingSoon } from "@/components/shared/ComingSoon";
import environment from "@/environment";
import { keycloakService, useAuth } from "@/integrations/auth";
import { v4 as uuidv4 } from "uuid";

import { useChat } from "@ai-sdk/react";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { DefaultChatTransport } from "ai";

export const Route = createFileRoute("/_authenticated/mentor/$threadId")({
	component: RouteComponent,
});

function RouteComponent() {
	const { hasRole } = useAuth();
	const { threadId } = Route.useParams();

	if (!hasRole("mentor_access")) {
		return (
			<div className="h-1/2 flex items-center justify-center">
				<ComingSoon />
			</div>
		);
	}

	return <ThreadContainer threadId={threadId} />;
}

/**
 * Container component for loading and displaying an existing chat thread.
 * Fetches the thread details and shows the conversation history, allowing continuation.
 */
function ThreadContainer({ threadId }: { threadId: string }) {
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
	if (isLoading) {
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
	if (fetchError) {
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
	return <ThreadChat threadId={threadId} threadDetail={threadDetail} />;
}

/**
 * Chat component that initializes useChat with the loaded thread details.
 * Only rendered after thread details have been successfully loaded.
 */
function ThreadChat({
	threadId,
	threadDetail,
}: {
	threadId: string;
	threadDetail: ChatThreadDetail;
}) {
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
		onFinish: (message) => {
			console.log("Message finished:", message);
		},
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

	console.log("Messages from useChat:", threadDetail.messages);

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
