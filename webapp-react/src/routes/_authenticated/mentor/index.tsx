import { Chat } from "@/components/mentor/Chat";
import { useMentorChat } from "@/hooks/useMentorChat";
import type { ChatMessage } from "@/lib/types";
import { useCallback } from "react";

import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/mentor/")({
	component: MentorContainer,
});

function MentorContainer() {
	// Initialize mentor chat hook for new conversations
	const mentorChat = useMentorChat({
		onError: (error: Error) => {
			console.error("Chat error:", error);
		},
	});

	const handleMessageSubmit = useCallback(
		({ text }: { text: string }) => {
			if (!text.trim()) return;
			mentorChat.sendMessage(text);
		},
		[mentorChat.sendMessage], // Only depend on the specific function
	);

	const handleCopy = useCallback((content: string) => {
		navigator.clipboard.writeText(content).catch((error) => {
			console.error("Failed to copy to clipboard:", error);
		});
	}, []);

	const handleVote = useCallback(
		(messageId: string, isUpvote: boolean) => {
			mentorChat.voteMessage(messageId, isUpvote);
		},
		[mentorChat.voteMessage], // Only depend on the specific function
	);

	const handleMessageEdit = useCallback(
		(messageId: string, content: string) => {
			const idx = mentorChat.messages.findIndex((m) => m.id === messageId);
			if (idx === -1) return;
			mentorChat.setMessages(mentorChat.messages.slice(0, idx));
			mentorChat.sendMessage(content);
		},
		[mentorChat.messages, mentorChat.setMessages, mentorChat.sendMessage],
	);

	return (
		<div className="flex flex-col flex-1 min-h-0">
			<Chat
				id={mentorChat.currentThreadId || mentorChat.id}
				messages={mentorChat.messages as ChatMessage[]} // Use UIMessage directly - they're compatible
				status={mentorChat.status}
				readonly={false}
				attachments={[]} // Empty since attachments are disabled
				onMessageSubmit={handleMessageSubmit}
				onMessageEdit={handleMessageEdit}
				onStop={mentorChat.stop}
				onFileUpload={() => Promise.resolve([])} // No-op since attachments are disabled
				onAttachmentsChange={() => {}} // No-op since attachments are disabled
				onCopy={handleCopy}
				onVote={handleVote}
				showSuggestedActions={true}
				inputPlaceholder="Ask me anything about software development, best practices, or agile concepts..."
				disableAttachments={true}
				className="h-[calc(100dvh-4rem)]"
			/>
		</div>
	);
}
