import { Chat } from "@/components/mentor";
import { ComingSoon } from "@/components/shared/ComingSoon";
import environment from "@/environment";
import { keycloakService, useAuth } from "@/integrations/auth";
import { v4 as uuidv4 } from "uuid";

import { useChat } from "@ai-sdk/react";
import { createFileRoute } from "@tanstack/react-router";
import { DefaultChatTransport } from "ai";

export const Route = createFileRoute("/_authenticated/mentor")({
	component: RouteComponent,
});

function RouteComponent() {
	const { hasRole } = useAuth();

	if (!hasRole("mentor_access")) {
		return (
			<div className="h-1/2 flex items-center justify-center">
				<ComingSoon />
			</div>
		);
	}

	return <MentorContainer />;
}

/**
 * AI Mentor route component providing a professional chat interface.
 *
 * This route integrates the AI SDK with our custom Chat components to provide:
 * - Real-time message streaming with proper UX indicators
 * - Professional message formatting and layout
 * - Error handling with retry capabilities
 * - Responsive design with scroll management
 * - Proper separation of smart (data) and presentational (UI) concerns
 */
function MentorContainer() {
	const { error, status, sendMessage, messages, reload, stop } = useChat({
		generateId: () => uuidv4(),
		transport: new DefaultChatTransport({
			api: `${environment.serverUrl}/mentor/chat`,
			headers: {
				Authorization: `Bearer ${keycloakService.getToken()}`,
			},
		}),
	});

	// Transform AI SDK messages to our chat format
	const chatMessages = messages.map((message) => ({
		id: message.id,
		role: message.role as "user" | "assistant",
		parts: message.parts,
	}));

	const isStreaming = status === "streaming" || status === "submitted";
	const currentStreamingId = isStreaming
		? messages[messages.length - 1]?.id
		: undefined;

	const handleMessageSubmit = (text: string) => {
		sendMessage({ text });
	};

	const handleStop = () => {
		stop();
	};

	const handleRetry = () => {
		reload();
	};

	return (
		<div className="h-[calc(100vh-4rem)] max-w-4xl mx-auto p-4">
			<Chat
				messages={chatMessages}
				onMessageSubmit={handleMessageSubmit}
				onStop={handleStop}
				onRetry={handleRetry}
				isStreaming={isStreaming}
				streamingMessageId={currentStreamingId}
				error={error}
				disabled={status === "submitted"}
				placeholder="Ask me anything about software development, best practices, or technical concepts..."
			/>
		</div>
	);
}
