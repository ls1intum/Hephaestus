import type { UseChatHelpers } from "@ai-sdk/react";
import equal from "fast-deep-equal";
import { memo } from "react";
import { createPortal } from "react-dom";
import { useWindowSize } from "usehooks-ts";
import type { ChatMessageVote, Document } from "@/api/types.gen";
import { useScrollToBottom } from "@/hooks/use-scroll-to-bottom";
import type { Attachment, ChatMessage } from "@/lib/types";
import { cn } from "@/lib/utils";
import type { UIArtifact } from "./Artifact";
import { Artifact } from "./Artifact";
import { Messages } from "./Messages";
import { MultimodalInput } from "./MultimodalInput";

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
	/** Handler called when document preview is clicked */
	onDocumentClick?: (documentId: string, boundingBox: DOMRect) => void;
	/** Whether to show suggested actions in input */
	showSuggestedActions?: boolean;
	/** Placeholder text for input */
	inputPlaceholder?: string;
	/** Whether to disable attachment functionality */
	disableAttachments?: boolean;
	/** Optional CSS class name */
	className?: string;
	/** Artifact overlay state (from hook) */
	artifact?: UIArtifact | null;
	artifactDocuments?: Document[];
	artifactCurrentVersionIndex?: number;
	artifactIsCurrentVersion?: boolean;
	artifactIsContentDirty?: boolean;
	artifactMode?: "edit" | "diff";
	/** Artifact handlers (from hook) */
	onOpenArtifactById?: (documentId: string, boundingBox: DOMRect) => void;
	onOpenArtifact?: (document: Document, boundingBox: DOMRect) => void;
	onCloseArtifact?: () => void;
	onSaveArtifactContent?: (content: string, debounce: boolean) => void;
	onChangeArtifactVersion?: (
		type: "next" | "prev" | "toggle" | "latest",
	) => void;
}

function PureChat({
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
	artifact,
	artifactDocuments,
	artifactCurrentVersionIndex,
	artifactIsCurrentVersion,
	artifactIsContentDirty,
	artifactMode,
	onOpenArtifactById,
	// onOpenArtifact not yet used within Chat (artifact opens from Message -> handler)
	onCloseArtifact,
	onSaveArtifactContent,
	onChangeArtifactVersion,
}: ChatProps) {
	const { width } = useWindowSize();
	const isMobile = width ? width < 768 : false;

	// Internal scroll management for the chat container
	const { containerRef, endRef, isAtBottom, scrollToBottom } =
		useScrollToBottom();

	// Use internal scroll management if parent doesn't provide it
	const actualIsAtBottom = parentScrollToBottom ? parentIsAtBottom : isAtBottom;
	const actualScrollToBottom = parentScrollToBottom || scrollToBottom;

	// Derived values from provided artifact/doc state
	const currentDocument =
		artifactDocuments?.[artifactCurrentVersionIndex ?? -1] ||
		artifactDocuments?.[0];
	const isCurrentVersion =
		artifactIsCurrentVersion ??
		(!!artifactDocuments &&
			(artifactCurrentVersionIndex ?? -1) === artifactDocuments.length - 1);

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
						onDocumentClick={onOpenArtifactById}
						onDocumentSave={(content) => onSaveArtifactContent?.(content, true)}
					/>

					{/* Absolutely anchored input at the bottom of SidebarInset content area */}
					<div className="flex flex-col gap-2 items-center w-full px-4 pb-2 -mt-20 relative z-10 bg-gradient-to-t from-muted dark:from-background/30 from-60% to-transparent pt-8">
						{!readonly && (
							<div className="w-full max-w-3xl">
								<MultimodalInput
									status={
										status === "submitted"
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
									isCurrentVersion={isCurrentVersion}
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

			{artifact &&
				createPortal(
					<Artifact
						artifact={artifact}
						documents={artifactDocuments}
						currentDocument={currentDocument}
						currentVersionIndex={artifactCurrentVersionIndex ?? -1}
						isCurrentVersion={isCurrentVersion}
						isContentDirty={artifactIsContentDirty ?? false}
						mode={artifactMode ?? "edit"}
						isVisible={artifact.isVisible}
						isMobile={isMobile}
						readonly={readonly}
						messages={messages}
						votes={votes}
						status={status}
						attachments={attachments}
						metadata={{}}
						onClose={onCloseArtifact ?? (() => {})}
						onContentSave={(content: string) =>
							onSaveArtifactContent?.(content, true)
						}
						onVersionChange={onChangeArtifactVersion ?? (() => {})}
						onMessageSubmit={onMessageSubmit}
						onStop={onStop}
						onFileUpload={onFileUpload}
						onMessageEdit={onMessageEdit}
						onCopy={onCopy}
						onVote={onVote}
						onDocumentClick={onOpenArtifactById}
						onDocumentSave={(content) => onSaveArtifactContent?.(content, true)}
						onMetadataUpdate={() => {}}
					/>,
					document.body,
				)}
		</>
	);
}

export const Chat = memo(PureChat, (prevProps, nextProps) => {
	// Compare messages arrays
	if (!equal(prevProps.messages, nextProps.messages)) return false;

	// Compare votes arrays
	if (!equal(prevProps.votes, nextProps.votes)) return false;

	// Compare attachments arrays
	if (!equal(prevProps.attachments, nextProps.attachments)) return false;

	// Compare status
	if (prevProps.status !== nextProps.status) return false;

	// Compare readonly state
	if (prevProps.readonly !== nextProps.readonly) return false;

	// Compare scroll state
	if (prevProps.isAtBottom !== nextProps.isAtBottom) return false;

	// Compare configuration props
	if (prevProps.showSuggestedActions !== nextProps.showSuggestedActions)
		return false;
	if (prevProps.disableAttachments !== nextProps.disableAttachments)
		return false;
	if (prevProps.inputPlaceholder !== nextProps.inputPlaceholder) return false;

	// Compare function handlers (by reference - they should be stable)
	if (prevProps.onMessageSubmit !== nextProps.onMessageSubmit) return false;
	if (prevProps.onStop !== nextProps.onStop) return false;
	if (prevProps.onFileUpload !== nextProps.onFileUpload) return false;
	if (prevProps.onAttachmentsChange !== nextProps.onAttachmentsChange)
		return false;

	// If all comparisons pass, component should not re-render
	return true;
});
