import { createFileRoute, useLocation, useNavigate } from "@tanstack/react-router";
import { useEffect, useRef } from "react";
import { ArtifactOverlayContainer } from "@/components/mentor/ArtifactOverlayContainer";
import { Chat } from "@/components/mentor/Chat";
import { defaultPartRenderers } from "@/components/mentor/renderers";
import { Skeleton } from "@/components/ui/skeleton";
import { useMentorChat } from "@/hooks/useMentorChat";
import type { ChatMessage } from "@/lib/types";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/mentor/$threadId")({
	component: ThreadContainer,
});

function ThreadContainer() {
	const { workspaceSlug, threadId } = Route.useParams();
	const { state } = useLocation();
	const navigate = useNavigate({ from: Route.fullPath });

	// Check if we should auto-trigger a greeting (for new threads)
	const autoGreeting = state?.autoGreeting === true;

	// Initialize mentor chat hook
	const mentorChat = useMentorChat({
		threadId,
		autoGreeting,
		onError: (error: Error) => {
			console.error("Chat error:", error);
		},
	});

	// Clear navigation state after mounting to prevent re-triggering on back/forward
	const stateCleared = useRef(false);
	useEffect(() => {
		if (stateCleared.current || !autoGreeting) return;
		stateCleared.current = true;
		navigate({
			to: Route.fullPath,
			params: { workspaceSlug, threadId },
			replace: true,
			state: undefined,
		});
	}, [autoGreeting, navigate, workspaceSlug, threadId]);
	const handleMessageSubmit = ({ text }: { text: string }) => {
		if (!text.trim()) return;
		mentorChat.sendMessage(text);
	};

	const handleVote = (messageId: string, isUpvote: boolean) => {
		mentorChat.voteMessage(messageId, isUpvote);
	};

	const handleCopy = (content: string) => {
		navigator.clipboard.writeText(content).catch((error) => {
			console.error("Failed to copy to clipboard:", error);
		});
	};

	const handleMessageEdit = (messageId: string, content: string) => {
		const idx = mentorChat.messages.findIndex((m) => m.id === messageId);
		if (idx === -1) return;
		mentorChat.setMessages(mentorChat.messages.slice(0, idx));
		mentorChat.sendMessage(content);
	};

	// Show loading state while fetching thread with a chat-like skeleton
	if (mentorChat.isThreadLoading) {
		return (
			<div className="flex flex-col flex-1 min-h-0">
				<div className="relative h-[calc(100dvh-4rem)] flex flex-col">
					{/* Messages area */}
					<div className="flex-1 overflow-y-auto p-4 sm:p-6">
						<div className="flex flex-col w-full pb-16 min-w-0 gap-8 flex-1 pt-4 relative mx-auto md:max-w-3xl">
							{/* User bubble */}
							<div className="flex items-start gap-3 justify-end">
								<div className="space-y-2 max-w-[75%] text-right">
									<Skeleton className="h-4 w-56 ml-auto" />
									<Skeleton className="h-4 w-28 ml-auto" />
								</div>
							</div>

							{/* Assistant bubble */}
							<div className="flex items-start gap-3">
								<Skeleton className="h-8 w-8 rounded-full" />
								<div className="space-y-2 max-w-[75%]">
									<Skeleton className="h-4 w-40" />
									<Skeleton className="h-4 w-64" />
									<Skeleton className="h-4 w-32" />
								</div>
							</div>

							{/* User bubble */}
							<div className="flex items-start gap-3 justify-end">
								<div className="space-y-2 max-w-[75%] text-right">
									<Skeleton className="h-4 w-75 ml-auto" />
									<Skeleton className="h-4 w-34 ml-auto" />
									<Skeleton className="h-4 w-53 ml-auto" />
								</div>
							</div>

							{/* Assistant bubble */}
							<div className="flex items-start gap-3">
								<Skeleton className="h-8 w-8 rounded-full" />
								<div className="space-y-2 max-w-[75%]">
									<Skeleton className="h-4 w-72" />
									<Skeleton className="h-4 w-52" />
									<Skeleton className="h-4 w-24" />
								</div>
							</div>
						</div>
					</div>

					{/* Bottom input bar */}
					<div className="flex flex-col gap-2 items-center w-full px-4 pb-2 -mt-20 relative z-10 bg-gradient-to-t from-muted dark:from-background/30 from-60% to-transparent pt-8">
						<div className="w-full max-w-3xl space-y-2">
							<Skeleton className="h-20 flex-1" />
						</div>
						<Skeleton className="h-3 w-64" />
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
						Failed to load conversation. Thread may not exist or you don't have access to it.
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
				inputPlaceholder="Continue the conversation..."
				disableAttachments={true}
				className="h-[calc(100dvh-4rem)]"
				partRenderers={defaultPartRenderers}
			/>
			<ArtifactOverlayContainer
				messages={mentorChat.messages as unknown as ChatMessage[]}
				votes={mentorChat.votes}
				status={mentorChat.status}
				attachments={[]}
				readonly={false}
				onMessageSubmit={handleMessageSubmit}
				onStop={mentorChat.stop}
				onFileUpload={() => Promise.resolve([])}
				onMessageEdit={handleMessageEdit}
				onCopy={handleCopy}
				onVote={handleVote}
				partRenderers={defaultPartRenderers}
			/>
		</div>
	);
}
