import { Chat } from "@/components/mentor/Chat";
import { useMentorChat } from "@/hooks/useMentorChat";
import type { ChatMessage } from "@/lib/types";
import { useCallback } from "react";

import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/mentor/$threadId")({
	component: ThreadContainer,
});

function ThreadContainer() {
	const { threadId } = Route.useParams();

	// Initialize mentor chat hook for existing thread
	const mentorChat = useMentorChat({
		threadId,
		onError: (error: Error) => {
			console.error("Chat error:", error);
		},
	});

	const handleMessageSubmit = useCallback(
		({ text }: { text: string }) => {
			if (!text.trim()) return;
			mentorChat.sendMessage(text);
		},
		[mentorChat.sendMessage],
	);

	const handleVote = useCallback(
		(messageId: string, isUpvote: boolean) => {
			mentorChat.voteMessage(messageId, isUpvote);
		},
		[mentorChat.voteMessage],
	);

	const handleCopy = useCallback((content: string) => {
		navigator.clipboard.writeText(content).catch((error) => {
			console.error("Failed to copy to clipboard:", error);
		});
	}, []);

	// Show loading state while fetching thread
	if (mentorChat.isThreadLoading) {
		return (
			<div className="h-full flex items-center justify-center p-6">
				<div className="text-center">
					<div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto mb-4" />
					<p className="text-muted-foreground">Loading conversation...</p>
				</div>
			</div>
		);
	}

	// Show error state if thread fetch failed
	if (mentorChat.threadError) {
		return (
			<div className="h-full flex items-center justify-center p-6">
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
	if (!mentorChat.threadDetail && !mentorChat.isThreadLoading) {
		return (
			<div className="h-full flex items-center justify-center p-6">
				<div className="text-center">
					<p className="text-muted-foreground">Conversation not found.</p>
				</div>
			</div>
		);
	}

	return (
		<div className="flex flex-col flex-1 min-h-0">
			<Chat
				id={mentorChat.currentThreadId || threadId}
				messages={mentorChat.messages as unknown as ChatMessage[]}
				status={mentorChat.status}
				readonly={false}
				attachments={[]} // Empty since attachments are disabled
				onMessageSubmit={handleMessageSubmit}
				onStop={mentorChat.stop}
				onFileUpload={() => Promise.resolve([])} // No-op since attachments are disabled
				onAttachmentsChange={() => {}} // No-op since attachments are disabled
				onCopy={handleCopy}
				onVote={handleVote}
				showSuggestedActions={false}
				inputPlaceholder="Continue the conversation..."
				disableAttachments={true}
				className="h-[calc(100dvh-4rem)]"
			/>
		</div>
	);
}
