import type { ChatMessageVote, Document } from "@/api/types.gen";
import { useScrollToBottom } from "@/hooks/use-scroll-to-bottom";
import type { Attachment, ChatMessage } from "@/lib/types";
import { cn } from "@/lib/utils";
import type { UseChatHelpers } from "@ai-sdk/react";
import { formatDistance } from "date-fns";
import { AnimatePresence, motion } from "framer-motion";
import { Copy, History, Redo2, Undo2 } from "lucide-react";
import { useDebounceCallback, useWindowSize } from "usehooks-ts";
import { type ArtifactAction, ArtifactActions } from "./ArtifactActions";
import { ArtifactCloseButton } from "./ArtifactCloseButton";
import { Messages } from "./Messages";
import { MultimodalInput } from "./MultimodalInput";
import { VersionFooter } from "./VersionFooter";
import { textArtifact } from "./artifacts/text";

export const artifactDefinitions = [textArtifact];

export type ArtifactKind = (typeof artifactDefinitions)[number]["kind"];

export interface UIArtifact {
	title: string;
	documentId: string;
	kind: ArtifactKind;
	content: string;
	isVisible: boolean;
	status: "streaming" | "idle";
	boundingBox: {
		top: number;
		left: number;
		width: number;
		height: number;
	};
}

export interface ArtifactProps {
	/** Core artifact data */
	artifact: UIArtifact;
	/** Array of document versions */
	documents?: Document[];
	/** Current document being displayed */
	currentDocument?: Document;
	/** Index of current version being viewed */
	currentVersionIndex?: number;
	/** Whether viewing the latest version */
	isCurrentVersion?: boolean;
	/** Whether content has unsaved changes */
	isContentDirty?: boolean;
	/** Display mode for content */
	mode?: "edit" | "diff";
	/** Whether the artifact is visible */
	isVisible: boolean;
	/** Whether on mobile device */
	isMobile?: boolean;
	/** Whether in readonly mode */
	readonly?: boolean;
	/** Chat messages to display in sidebar */
	messages: ChatMessage[];
	/** Message votes */
	votes?: ChatMessageVote[];
	/** Current chat status */
	status: UseChatHelpers<ChatMessage>["status"];
	/** Current input attachments */
	attachments: Attachment[];
	/** Metadata for artifact rendering */
	metadata?: Record<string, unknown>;
	/** Handler for closing the artifact */
	onClose: () => void;
	/** Handler for saving content changes */
	onContentSave: (content: string) => void;
	/** Handler for version navigation */
	onVersionChange: (type: "next" | "prev" | "toggle" | "latest") => void;
	/** Handler for message submission */
	onMessageSubmit: (data: { text: string; attachments: Attachment[] }) => void;
	/** Handler for stopping current operation */
	onStop: () => void;
	/** Handler for file uploads */
	onFileUpload: (files: File[]) => Promise<Attachment[]>;
	/** Handler for message editing */
	onMessageEdit?: (messageId: string, content: string) => void;
	/** Handler for copying content */
	onCopy?: (content: string) => void;
	/** Handler for voting on messages */
	onVote?: (messageId: string, isUpvote: boolean) => void;
	/** Handler for document interactions */
	onDocumentClick?: (documentId: string, boundingBox: DOMRect) => void;
	/** Handler for document save operations */
	onDocumentSave?: (documentId: string, content: string) => void;
	/** Handler for metadata updates */
	onMetadataUpdate?: (metadata: Record<string, unknown>) => void;
	/** Optional CSS class name */
	className?: string;
}

function PureArtifact({
	artifact,
	documents = [],
	currentDocument,
	currentVersionIndex = -1,
	isCurrentVersion = true,
	isContentDirty = false,
	mode = "edit",
	isVisible,
	isMobile = false,
	readonly = false,
	messages,
	votes,
	status,
	attachments,
	metadata = {},
	onContentSave,
	onVersionChange,
	onMessageSubmit,
	onStop,
	onFileUpload,
	onMessageEdit,
	onCopy,
	onVote,
	onDocumentClick,
	onDocumentSave,
	onMetadataUpdate,
	onClose,
	className,
}: ArtifactProps) {
	const { width: windowWidth, height: windowHeight } = useWindowSize();

	// Scroll management for the chat sidebar
	const { containerRef, endRef, isAtBottom, scrollToBottom } =
		useScrollToBottom();

	const artifactDefinition = artifactDefinitions.find(
		(definition) => definition.kind === artifact.kind,
	);

	if (!artifactDefinition) {
		throw new Error("Artifact definition not found!");
	}

	const getDocumentContentById = (index: number) => {
		if (!documents || !documents[index]) return "";
		return documents[index].content ?? "";
	};

	// Debounced content change handler
	const debouncedOnContentSave = useDebounceCallback(onContentSave, 2000);

	const handleMultimodalSubmit = (data: {
		text: string;
		attachments: Attachment[];
	}) => {
		onMessageSubmit(data);
	};

	const handleAttachmentsChange = () => {
		// Attachments are managed by parent - this is a no-op for now
		// Could add handler if needed for internal attachment state
	};

	// Create artifact actions for version navigation and copy functionality
	const actions: ArtifactAction[] = [
		// View changes/diff toggle
		{
			id: "view-changes",
			icon: <History size={16} />,
			description: mode === "edit" ? "View changes" : "View content",
			disabled: !documents || documents.length <= 1,
			onClick: () => onVersionChange("toggle"),
		},
		// Previous version navigation
		{
			id: "prev-version",
			icon: <Undo2 size={16} />,
			description: "View previous version",
			disabled: !documents || documents.length <= 1 || currentVersionIndex <= 0,
			onClick: () => onVersionChange("prev"),
		},
		// Next version navigation
		{
			id: "next-version",
			icon: <Redo2 size={16} />,
			description: "View next version",
			disabled:
				!documents ||
				documents.length <= 1 ||
				currentVersionIndex >= documents.length - 1,
			onClick: () => onVersionChange("next"),
		},
		// Copy to clipboard action
		{
			id: "copy",
			icon: <Copy size={16} />,
			description: "Copy content to clipboard",
			onClick: () => {
				const content = isCurrentVersion
					? artifact.content
					: getDocumentContentById(currentVersionIndex);
				onCopy?.(content);
			},
		},
	];

	return (
		<AnimatePresence mode="wait">
			{isVisible && (
				<motion.div
					key="artifact-container" // Stable key - doesn't change when switching documents
					data-testid="artifact"
					className={cn(
						"flex flex-row h-dvh w-dvw fixed top-0 left-0 z-100 bg-transparent",
						className,
					)}
					initial={{ opacity: 1 }}
					animate={{ opacity: 1 }}
					exit={{
						opacity: 0,
						transition: {
							delay: 0.3,
							duration: 0.2,
							ease: "easeInOut",
						},
					}}
				>
					{!isMobile && (
						<motion.div
							className="fixed bg-background h-dvh"
							initial={{
								width: windowWidth,
								right: 0,
							}}
							animate={{
								width: windowWidth,
								right: 0,
							}}
							exit={{
								width: windowWidth,
								right: 0,
								transition: {
									duration: 0.3,
									ease: [0.32, 0.72, 0, 1],
								},
							}}
						/>
					)}

					{!isMobile && (
						<motion.div
							className="relative w-[400px] bg-muted dark:bg-background h-dvh shrink-0"
							initial={{ opacity: 0, x: 10, scale: 1 }}
							animate={{
								opacity: 1,
								x: 0,
								scale: 1,
								transition: {
									delay: 0.2,
									type: "spring",
									stiffness: 200,
									damping: 30,
								},
							}}
							exit={{
								opacity: 0,
								scale: 0.95,
								transition: {
									duration: 0.3,
									ease: [0.32, 0.72, 0, 1],
								},
							}}
						>
							<AnimatePresence>
								{!isCurrentVersion && (
									<motion.div
										className="left-0 absolute h-dvh w-[400px] top-0 bg-zinc-900/50 z-[100]"
										initial={{ opacity: 0 }}
										animate={{ opacity: 1 }}
										exit={{ opacity: 0 }}
									/>
								)}
							</AnimatePresence>

							<div className="flex flex-col h-full">
								<Messages
									messages={messages}
									votes={votes}
									status={status}
									readonly={readonly}
									variant="artifact"
									containerRef={containerRef}
									endRef={endRef}
									onMessageEdit={onMessageEdit}
									onCopy={onCopy}
									onVote={onVote}
									onDocumentClick={onDocumentClick}
									onDocumentSave={onDocumentSave}
								/>

								<div className="flex flex-col gap-1 items-center w-full px-4 pb-2 -mt-20 relative z-10 bg-gradient-to-t from-muted dark:from-background/30 from-60% to-transparent pt-8">
									<MultimodalInput
										status={status === "streaming" ? "submitted" : "ready"}
										onStop={onStop}
										attachments={attachments}
										onAttachmentsChange={handleAttachmentsChange}
										onFileUpload={onFileUpload}
										onSubmit={handleMultimodalSubmit}
										className="bg-background dark:bg-muted"
										readonly={readonly}
										isAtBottom={isAtBottom}
										scrollToBottom={scrollToBottom}
										isCurrentVersion={isCurrentVersion}
									/>
									{/* AI Disclaimer */}
									<p className="text-center text-balance text-xs text-muted-foreground px-4">
										Hephaestus can make mistakes. 
									</p>
								</div>
							</div>
						</motion.div>
					)}

					<motion.div
						className="fixed dark:bg-muted bg-background h-dvh flex flex-col md:border-l dark:border-zinc-700 border-zinc-200"
						initial={
							isMobile
								? {
										opacity: 1,
										x: artifact.boundingBox.left,
										y: artifact.boundingBox.top,
										height: artifact.boundingBox.height,
										width: artifact.boundingBox.width,
										borderRadius: 50,
									}
								: {
										opacity: 1,
										x: artifact.boundingBox.left,
										y: artifact.boundingBox.top,
										height: artifact.boundingBox.height,
										width: artifact.boundingBox.width,
										borderRadius: 50,
									}
						}
						animate={
							isMobile
								? {
										opacity: 1,
										x: 0,
										y: 0,
										height: windowHeight,
										width: windowWidth ? windowWidth : "calc(100dvw)",
										borderRadius: 0,
										transition: {
											delay: 0,
											type: "spring",
											stiffness: 200,
											damping: 30,
											duration: 5000,
										},
									}
								: {
										opacity: 1,
										x: 400,
										y: 0,
										height: windowHeight,
										width: windowWidth
											? windowWidth - 400
											: "calc(100dvw-400px)",
										borderRadius: 0,
										transition: {
											delay: 0,
											type: "spring",
											stiffness: 200,
											damping: 30,
											duration: 5000,
										},
									}
						}
						exit={{
							opacity: 0,
							scale: 0.95,
							transition: {
								duration: 0.35,
								ease: [0.32, 0.72, 0, 1],
								delay: 0.05,
							},
						}}
					>
						<div className="p-2 flex flex-row justify-between items-start">
							<div className="flex flex-row gap-4 items-start">
								<ArtifactCloseButton onClose={onClose} />

								<div className="flex flex-col">
									<div className="font-medium">{artifact.title}</div>{" "}
									{isContentDirty ? (
										<div className="text-sm text-muted-foreground">
											Saving changes...
										</div>
									) : currentDocument ? (
										<div className="text-sm text-muted-foreground">
											{`Updated ${formatDistance(
												new Date(currentDocument.createdAt),
												new Date(),
												{
													addSuffix: true,
												},
											)}`}
										</div>
									) : (
										<div className="w-32 h-3 mt-2 bg-muted-foreground/20 rounded-md animate-pulse" />
									)}
								</div>
							</div>

							<ArtifactActions
								actions={actions}
								isStreaming={artifact.status === "streaming"}
							/>
						</div>
						<div className="flex-1 min-h-0 h-full overflow-hidden">
							<artifactDefinition.content
								title={artifact.title}
								content={
									isCurrentVersion
										? artifact.content
										: getDocumentContentById(currentVersionIndex)
								}
								mode={mode}
								status={artifact.status}
								currentVersionIndex={currentVersionIndex}
								onSaveContent={debouncedOnContentSave}
								isInline={false}
								isCurrentVersion={isCurrentVersion}
								getDocumentContentById={getDocumentContentById}
								isLoading={false}
								metadata={metadata}
								setMetadata={(value) => {
									if (typeof value === "function") {
										const newMetadata = value(
											metadata as Record<string, unknown>,
										);
										onMetadataUpdate?.(newMetadata as Record<string, unknown>);
									} else {
										onMetadataUpdate?.(value as Record<string, unknown>);
									}
								}}
							/>
						</div>
						<AnimatePresence>
							{!isCurrentVersion && (
								<VersionFooter
									currentVersionIndex={currentVersionIndex}
									documents={documents}
									handleVersionChange={onVersionChange}
								/>
							)}
						</AnimatePresence>
					</motion.div>
				</motion.div>
			)}
		</AnimatePresence>
	);
}

export const Artifact = PureArtifact;
