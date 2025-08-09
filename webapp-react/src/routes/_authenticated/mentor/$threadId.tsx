import { Chat } from "@/components/mentor/Chat";
import { useMentorChat } from "@/hooks/useMentorChat";
import type { ChatMessage } from "@/lib/types";
import { useCallback, useEffect, useMemo, useRef } from "react";

import { createFileRoute, useLocation, useNavigate } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/mentor/$threadId")({
	component: ThreadContainer,
});

function ThreadContainer() {
	const { threadId } = Route.useParams();
	const { state } = useLocation();
	const navigate = useNavigate();

	// Initialize mentor chat hook for existing thread
	const mentorChat = useMentorChat({
		threadId,
		onError: (error: Error) => {
			console.error("Chat error:", error);
		},
	});

	// Detect initial message passed via navigation state and send it once
	const initialMessage: string | undefined = useMemo(() => {
		// Expect shape: { initialMessage?: string, optimistic?: boolean }
		try {
			return (state as { initialMessage?: string } | undefined)?.initialMessage?.trim();
		} catch {
			return undefined;
		}
	}, [state]);
	const initialDispatchedRef = useRef(false);
	useEffect(() => {
		if (initialDispatchedRef.current) return;
		if (initialMessage && initialMessage.length > 0) {
			initialDispatchedRef.current = true;
			mentorChat.sendMessage(initialMessage);
			// Clear navigation state to avoid re-sending on back/forward
			navigate({
				to: Route.fullPath,
				params: { threadId },
				replace: true,
				state: undefined,
			});
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [initialMessage, mentorChat.sendMessage, threadId, navigate]);

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

	const handleMessageEdit = useCallback(
		(messageId: string, content: string) => {
			const idx = mentorChat.messages.findIndex((m) => m.id === messageId);
			if (idx === -1) return;
			mentorChat.setMessages(mentorChat.messages.slice(0, idx));
			mentorChat.sendMessage(content);
		},
		[mentorChat.messages, mentorChat.setMessages, mentorChat.sendMessage],
	);

	// Show loading state while fetching thread
	if (mentorChat.isThreadLoading) {
		return (
			<div className="h-full p-4 sm:p-6">
				<div className="max-w-3xl mx-auto space-y-4">
					<div className="animate-pulse space-y-3">
						<div className="h-4 w-24 bg-muted rounded" />
						<div className="h-20 bg-muted rounded-xl" />
					</div>
					<div className="animate-pulse flex gap-3">
						<div className="h-8 w-8 rounded-full bg-muted" />
						<div className="flex-1 space-y-2">
							<div className="h-4 w-3/5 bg-muted rounded" />
							<div className="h-4 w-4/5 bg-muted rounded" />
							<div className="h-4 w-2/5 bg-muted rounded" />
						</div>
					</div>
					<div className="animate-pulse flex gap-3 justify-end">
						<div className="flex-1 space-y-2 max-w-[70%]">
							<div className="h-4 w-full bg-muted rounded" />
							<div className="h-4 w-2/3 bg-muted rounded" />
						</div>
						<div className="h-8 w-8 rounded-full bg-muted" />
					</div>
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
				votes={mentorChat.votes}
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
				showSuggestedActions={false}
				inputPlaceholder="Continue the conversation..."
				disableAttachments={true}
				className="h-[calc(100dvh-4rem)]"
			/>
		</div>
	);
}
