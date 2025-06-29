import { Chat } from "@/components/mentor";
import { ComingSoon } from "@/components/shared/ComingSoon";
import environment from "@/environment";
import { keycloakService, useAuth } from "@/integrations/auth";
import { v4 as uuidv4 } from "uuid";

import { useChat } from "@ai-sdk/react";
import { createFileRoute } from "@tanstack/react-router";
import { DefaultChatTransport } from "ai";

export const Route = createFileRoute("/_authenticated/mentor/")({
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
 * This route integrates the AI SDK v5 with our enhanced Chat components to provide:
 * - Real-time message streaming with proper UX indicators
 * - Professional message formatting and layout with avatars
 * - Error handling with retry capabilities
 * - Message regeneration for improved user experience
 * - Responsive design with intelligent auto-scroll
 * - Proper separation of smart (data) and presentational (UI) concerns
 */
function MentorContainer() {
	const { error, status, sendMessage, messages, regenerate, stop } = useChat({
		generateId: () => uuidv4(),
		transport: new DefaultChatTransport({
			api: `${environment.serverUrl}/mentor/chat`,
			headers: {
				Authorization: `Bearer ${keycloakService.getToken()}`,
			},
		}),
	});

	const isLoading = status === "streaming" || status === "submitted";

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
		<div className="h-[calc(100vh-4rem)] max-w-5xl mx-auto p-6">
			<Chat
				messages={messages}
				onSendMessage={handleSendMessage}
				onStop={handleStop}
				onRegenerate={handleRegenerate}
				isLoading={isLoading}
				error={error}
				disabled={status === "submitted"}
				placeholder="Ask me anything about software development, best practices, or technical concepts..."
				className="h-full"
			/>
		</div>
	);
}
