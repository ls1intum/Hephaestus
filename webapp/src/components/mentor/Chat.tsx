import type { UseChatHelpers } from "@ai-sdk/react";
import type { ChatMessageVote } from "@/api/types.gen";
import { useScrollToBottom } from "@/hooks/use-scroll-to-bottom";
import type { Attachment, ChatMessage } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Messages } from "./Messages";
import { MultimodalInput } from "./MultimodalInput";
import type { PartRendererMap } from "./renderers/types";

export interface ChatProps {
	/** Unique identifier for the chat session */
	id: string;
	/** Array of chat messages to display */
	messages: ChatMessage[];
	/** Array of votes for messages */
	votes?: ChatMessageVote[];
	/** Current chat status */
	status: UseChatHelpers<ChatMessage>["status"];
	/** Whether the interface is in readonly mode */
	readonly?: boolean;
	/** Whether the scroll container is at the bottom */
	isAtBottom?: boolean;
	/** Function to scroll to bottom of the chat */
	scrollToBottom?: () => void;
	/** Current input attachments */
	attachments: Attachment[];
	/** Handler for message submission */
	onMessageSubmit: (data: { text: string; attachments: Attachment[] }) => void;
	/** Handler for stopping current operation */
	onStop: () => void;
	/** Handler for file uploads */
	onFileUpload: (files: File[]) => Promise<Attachment[]>;
	/** Handler for attachment changes */
	onAttachmentsChange: (attachments: Attachment[]) => void;
	/** Handler for message editing */
	onMessageEdit?: (messageId: string, content: string) => void;
	/** Handler for copying message content */
	onCopy?: (content: string) => void;
	/** Handler for voting on messages */
	onVote?: (messageId: string, isUpvote: boolean) => void;
	/** Whether to show suggested actions in input */
	showSuggestedActions?: boolean;
	/** Placeholder text for input */
	inputPlaceholder?: string;
	/** Whether to disable attachment functionality */
	disableAttachments?: boolean;
	/** Optional CSS class name */
	className?: string;
	/** Injected renderers for tool parts (keep presentational) */
	partRenderers?: PartRendererMap;
}

export function Chat({
	messages,
	votes,
	status,
	readonly = false,
	isAtBottom: parentIsAtBottom = true,
	scrollToBottom: parentScrollToBottom,
	attachments,
	onMessageSubmit,
	onStop,
	onFileUpload,
	onAttachmentsChange,
	onMessageEdit,
	onCopy,
	onVote,
	showSuggestedActions = false,
	inputPlaceholder = "Send a message...",
	disableAttachments = false,
	className,
	partRenderers,
}: ChatProps) {
	// Internal scroll management for the chat container
	const { containerRef, endRef, isAtBottom, scrollToBottom } =
		useScrollToBottom();

	// Use internal scroll management if parent doesn't provide it
	const actualIsAtBottom = parentScrollToBottom ? parentIsAtBottom : isAtBottom;
	const actualScrollToBottom = parentScrollToBottom || scrollToBottom;

	// Suggested actions should only show when there are no messages and explicitly enabled
	const shouldShowSuggestedActions =
		showSuggestedActions && messages.length === 0;

	return (
		<>
			<div className={cn("relative h-full", className)}>
				<div className="flex flex-col h-full">
					<Messages
						messages={messages}
						votes={votes}
						status={status}
						readonly={readonly}
						showThinking={status === "submitted" || status === "streaming"}
						showGreeting={messages.length === 0}
						variant="default"
						containerRef={containerRef}
						endRef={endRef}
						onMessageEdit={onMessageEdit}
						onCopy={onCopy}
						onVote={onVote}
						partRenderers={partRenderers}
					/>

					{/* Absolutely anchored input at the bottom of SidebarInset content area */}
					<div className="flex flex-col gap-2 items-center w-full px-4 pb-2 -mt-20 relative z-10 bg-gradient-to-t from-muted dark:from-background/30 from-60% to-transparent pt-8">
						{!readonly && (
							<div className="w-full max-w-3xl">
								<MultimodalInput
									status={
										status === "submitted" || status === "streaming"
											? "submitted"
											: status === "error"
												? "error"
												: "ready"
									}
									onStop={onStop}
									attachments={attachments}
									onAttachmentsChange={onAttachmentsChange}
									onFileUpload={onFileUpload}
									onSubmit={onMessageSubmit}
									placeholder={inputPlaceholder}
									showSuggestedActions={shouldShowSuggestedActions}
									readonly={readonly}
									disableAttachments={disableAttachments}
									isAtBottom={actualIsAtBottom}
									scrollToBottom={actualScrollToBottom}
									isCurrentVersion={true}
									className="bg-background dark:bg-muted"
								/>
							</div>
						)}
						{/* AI Disclaimer */}
						<p className="text-center text-balance text-xs text-muted-foreground px-4">
							Heph can make mistakes. Consider verifying important information.
						</p>
					</div>
				</div>
			</div>

			{/* Overlay is injected by route containers, not here */}
		</>
	);
}
