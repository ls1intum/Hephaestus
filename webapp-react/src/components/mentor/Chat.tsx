import type { ChatMessageVote, Document } from "@/api/types.gen";
import type { Attachment, ChatMessage } from "@/lib/types";
import { cn } from "@/lib/utils";
import type { UseChatHelpers } from "@ai-sdk/react";
import equal from "fast-deep-equal";
import { memo } from "react";
import { useWindowSize } from "usehooks-ts";
import { Artifact } from "./Artifact";
import type { UIArtifact } from "./Artifact";
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
	/** Current artifact state */
	artifact?: UIArtifact;
	/** Array of document versions for the artifact */
	documents?: Document[];
	/** Current document being displayed in artifact */
	currentDocument?: Document;
	/** Index of current version being viewed in artifact */
	currentVersionIndex?: number;
	/** Whether viewing the latest version of the artifact */
	isCurrentVersion?: boolean;
	/** Whether artifact content has unsaved changes */
	isContentDirty?: boolean;
	/** Display mode for artifact content */
	artifactMode?: "edit" | "diff";
	/** Metadata for artifact rendering */
	artifactMetadata?: Record<string, unknown>;
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
	/** Handler for document interactions in messages */
	onDocumentClick?: (document: Document, boundingBox: DOMRect) => void;
	/** Handler for document content changes in messages */
	onDocumentSave?: (documentId: string, content: string) => void;
	/** Handler for artifact content saving */
	onArtifactContentSave?: (content: string) => void;
	/** Handler for artifact version navigation */
	onArtifactVersionChange?: (
		type: "next" | "prev" | "toggle" | "latest",
	) => void;
	/** Handler for closing the artifact */
	onArtifactClose?: () => void;
	/** Handler for artifact metadata updates */
	onArtifactMetadataUpdate?: (metadata: Record<string, unknown>) => void;
	/** Handler for suggested action clicks */
	onSuggestedAction?: (actionMessage: string) => void;
	/** Whether to show suggested actions in input */
	showSuggestedActions?: boolean;
	/** Placeholder text for input */
	inputPlaceholder?: string;
	/** Whether to disable attachment functionality */
	disableAttachments?: boolean;
	/** Optional CSS class name */
	className?: string;
}

function PureChat({
	messages,
	votes,
	status,
	readonly = false,
	isAtBottom = true,
	scrollToBottom,
	artifact,
	documents,
	currentDocument,
	currentVersionIndex = -1,
	isCurrentVersion = true,
	isContentDirty = false,
	artifactMode = "edit",
	artifactMetadata = {},
	attachments,
	onMessageSubmit,
	onStop,
	onFileUpload,
	onAttachmentsChange,
	onMessageEdit,
	onCopy,
	onVote,
	onDocumentClick,
	onDocumentSave,
	onArtifactContentSave,
	onArtifactVersionChange,
	onArtifactClose,
	onArtifactMetadataUpdate,
	onSuggestedAction,
	showSuggestedActions = false,
	inputPlaceholder = "Send a message...",
	disableAttachments = false,
	className,
}: ChatProps) {
	const { width } = useWindowSize();
	const isMobile = width ? width < 768 : false;

	return (
		<>
			<div
				className={cn("flex flex-col min-w-0 h-dvh bg-background", className)}
			>
				<Messages
					messages={messages}
					votes={votes}
					status={status}
					readonly={readonly}
					showThinking={status === "submitted"}
					requiresScrollPadding={true}
					showGreeting={messages.length === 0}
					variant="default"
					onMessageEdit={onMessageEdit}
					onCopy={onCopy}
					onVote={onVote}
					onDocumentClick={onDocumentClick}
					onDocumentSave={onDocumentSave}
				/>

				<form className="flex mx-auto px-4 bg-background pb-4 md:pb-6 gap-2 w-full md:max-w-3xl">
					{!readonly && (
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
							onSuggestedAction={onSuggestedAction}
							placeholder={inputPlaceholder}
							showSuggestedActions={showSuggestedActions}
							readonly={readonly}
							disableAttachments={disableAttachments}
							isAtBottom={isAtBottom}
							scrollToBottom={scrollToBottom}
							isCurrentVersion={isCurrentVersion}
						/>
					)}
				</form>
			</div>

			{artifact?.isVisible && onArtifactClose && (
				<Artifact
					artifact={artifact}
					documents={documents}
					currentDocument={currentDocument}
					currentVersionIndex={currentVersionIndex}
					isCurrentVersion={isCurrentVersion}
					isContentDirty={isContentDirty}
					mode={artifactMode}
					isVisible={artifact.isVisible}
					isMobile={isMobile}
					readonly={readonly}
					messages={messages}
					votes={votes}
					status={status}
					attachments={attachments}
					metadata={artifactMetadata}
					onClose={onArtifactClose}
					onContentSave={onArtifactContentSave || (() => {})}
					onVersionChange={onArtifactVersionChange || (() => {})}
					onMessageSubmit={onMessageSubmit}
					onStop={onStop}
					onFileUpload={onFileUpload}
					onMessageEdit={onMessageEdit}
					onCopy={onCopy}
					onVote={onVote}
					onDocumentClick={onDocumentClick}
					onDocumentSave={onDocumentSave}
					onMetadataUpdate={onArtifactMetadataUpdate}
				/>
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

	// Compare artifact state
	if (!equal(prevProps.artifact, nextProps.artifact)) return false;

	// Compare documents arrays
	if (!equal(prevProps.documents, nextProps.documents)) return false;

	// Compare readonly state
	if (prevProps.readonly !== nextProps.readonly) return false;

	// Compare scroll state
	if (prevProps.isAtBottom !== nextProps.isAtBottom) return false;

	// Compare artifact-related props
	if (prevProps.currentVersionIndex !== nextProps.currentVersionIndex)
		return false;
	if (prevProps.isCurrentVersion !== nextProps.isCurrentVersion) return false;
	if (prevProps.isContentDirty !== nextProps.isContentDirty) return false;
	if (prevProps.artifactMode !== nextProps.artifactMode) return false;

	// Compare configuration props
	if (prevProps.showSuggestedActions !== nextProps.showSuggestedActions)
		return false;
	if (prevProps.disableAttachments !== nextProps.disableAttachments)
		return false;
	if (prevProps.inputPlaceholder !== nextProps.inputPlaceholder) return false;

	// If all comparisons pass, component should not re-render
	return true;
});
