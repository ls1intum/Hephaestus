import type { ChatMessageVote, Document } from "@/api/types.gen";
import { useScrollToBottom } from "@/hooks/use-scroll-to-bottom";
import type { Attachment, ChatMessage } from "@/lib/types";
import { cn } from "@/lib/utils";
import type { UseChatHelpers } from "@ai-sdk/react";
import equal from "fast-deep-equal";
import { memo, useCallback, useState } from "react";
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
	/** Handler for suggested action clicks */
	onSuggestedAction?: (actionMessage: string) => void;
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
	onSuggestedAction,
	onDocumentClick,
	showSuggestedActions = false,
	inputPlaceholder = "Send a message...",
	disableAttachments = false,
	className,
}: ChatProps) {
	const { width } = useWindowSize();
	const isMobile = width ? width < 768 : false;

	// Internal scroll management for the chat container
	const { containerRef, endRef, isAtBottom, scrollToBottom } =
		useScrollToBottom();

	// Use internal scroll management if parent doesn't provide it
	const actualIsAtBottom = parentScrollToBottom ? parentIsAtBottom : isAtBottom;
	const actualScrollToBottom = parentScrollToBottom || scrollToBottom;

	// Internal artifact state management - completely self-contained
	const [artifact, setArtifact] = useState<UIArtifact | null>(null);
	const [documents, setDocuments] = useState<Document[]>([]);
	const [currentVersionIndex, setCurrentVersionIndex] = useState(-1);
	const [isContentDirty, setIsContentDirty] = useState(false);
	const [artifactMode, setArtifactMode] = useState<"edit" | "diff">("edit");

	// Handle document clicks from messages - convert to artifact opening
	const handleDocumentClick = useCallback(
		(document: Document, boundingBox: DOMRect) => {
			if (artifact?.isVisible) {
				// Artifact is already open - just switch to the new document (no animation)
				setArtifact((prev) =>
					prev
						? {
								...prev,
								title: document.title,
								documentId: document.id,
								content: document.content || "",
								// Keep existing boundingBox and isVisible to prevent re-animation
							}
						: null,
				);
				setDocuments([document]);
				setCurrentVersionIndex(0);
				setIsContentDirty(false);
				setArtifactMode("edit");
			} else {
				// No artifact open - create new one with opening animation
				const newArtifact: UIArtifact = {
					title: document.title,
					documentId: document.id,
					kind: document.kind === "TEXT" ? "text" : "text",
					content: document.content || "",
					isVisible: true,
					status: "idle",
					boundingBox: {
						top: boundingBox.top,
						left: boundingBox.left,
						width: boundingBox.width,
						height: boundingBox.height,
					},
				};

				setArtifact(newArtifact);
				setDocuments([document]);
				setCurrentVersionIndex(0);
				setIsContentDirty(false);
				setArtifactMode("edit");
			}

			// Call external handler for side effects (fetching more versions, etc.)
			onDocumentClick?.(document.id, boundingBox);
		},
		[onDocumentClick, artifact],
	);

	// Handle document clicks by ID (from DocumentTool components)
	const handleDocumentClickById = useCallback(
		(documentId: string, boundingBox: DOMRect) => {
			// Look up the document from the messages
			let foundDocument: Document | null = null;

			for (const message of messages) {
				for (const part of message.parts) {
					if (
						(part.type === "tool-createDocument" ||
							part.type === "tool-updateDocument" ||
							part.type === "tool-requestSuggestions") &&
						part.state === "output-available"
					) {
						const doc = part.output as Document;
						if (doc.id === documentId) {
							foundDocument = doc;
							break;
						}
					}
				}
				if (foundDocument) break;
			}

			if (foundDocument) {
				handleDocumentClick(foundDocument, boundingBox);
			}
		},
		[messages, handleDocumentClick],
	);

	// Handle artifact closing
	const handleArtifactClose = useCallback(() => {
		// First, trigger the exit animation
		setArtifact((prev) => (prev ? { ...prev, isVisible: false } : null));

		// After animation completes, clean up the state
		setTimeout(() => {
			setArtifact(null);
			setDocuments([]);
			setCurrentVersionIndex(-1);
			setIsContentDirty(false);
			setArtifactMode("edit");
		}, 600); // Wait for animation to complete
	}, []);

	// Handle artifact content saving
	const handleArtifactContentSave = useCallback(
		(content: string) => {
			if (artifact) {
				setArtifact((prev) => (prev ? { ...prev, content } : null));
				setIsContentDirty(true);
			}
		},
		[artifact],
	);

	// Handle artifact version navigation
	const handleArtifactVersionChange = useCallback(
		(type: "next" | "prev" | "toggle" | "latest") => {
			switch (type) {
				case "next":
					setCurrentVersionIndex((prev) =>
						Math.min(prev + 1, documents.length - 1),
					);
					break;
				case "prev":
					setCurrentVersionIndex((prev) => Math.max(prev - 1, 0));
					break;
				case "toggle":
					setArtifactMode((prev) => (prev === "edit" ? "diff" : "edit"));
					break;
				case "latest":
					setCurrentVersionIndex(documents.length - 1);
					break;
			}
		},
		[documents.length],
	);

	// API for external components to control artifact state (can be used by parent containers)
	// These are kept for potential future use but not exposed in props to keep API clean

	const currentDocument = documents[currentVersionIndex] || documents[0];
	const isCurrentVersion = currentVersionIndex === documents.length - 1;

	// Suggested actions should only show when there are no messages and explicitly enabled
	const shouldShowSuggestedActions =
		showSuggestedActions && messages.length === 0;

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
					showGreeting={messages.length === 0}
					variant="default"
					containerRef={containerRef}
					endRef={endRef}
					onMessageEdit={onMessageEdit}
					onCopy={onCopy}
					onVote={onVote}
					onDocumentClick={handleDocumentClickById}
					onDocumentSave={handleArtifactContentSave}
				/>

				<div className="relative">
					<div className="flex flex-row gap-2 items-end w-full px-4 pb-4 md:pb-6 -mt-20 relative z-10 bg-gradient-to-t from-muted dark:from-background/30 from-60% to-transparent pt-8">
						<div className="flex mx-auto gap-2 w-full md:max-w-3xl">
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
									showSuggestedActions={shouldShowSuggestedActions}
									readonly={readonly}
									disableAttachments={disableAttachments}
									isAtBottom={actualIsAtBottom}
									scrollToBottom={actualScrollToBottom}
									isCurrentVersion={isCurrentVersion}
									className="bg-background dark:bg-muted"
								/>
							)}
						</div>
					</div>
				</div>
			</div>

			{artifact && (
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
					metadata={{}}
					onClose={handleArtifactClose}
					onContentSave={handleArtifactContentSave}
					onVersionChange={handleArtifactVersionChange}
					onMessageSubmit={onMessageSubmit}
					onStop={onStop}
					onFileUpload={onFileUpload}
					onMessageEdit={onMessageEdit}
					onCopy={onCopy}
					onVote={onVote}
					onDocumentClick={handleDocumentClickById}
					onDocumentSave={handleArtifactContentSave}
					onMetadataUpdate={() => {}}
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
