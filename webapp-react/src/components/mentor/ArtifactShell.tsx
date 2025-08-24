import type { UseChatHelpers } from "@ai-sdk/react";
import { AnimatePresence, motion } from "framer-motion";
import { useWindowSize } from "usehooks-ts";
import type { ChatMessageVote } from "@/api/types.gen";
import { useScrollToBottom } from "@/hooks/use-scroll-to-bottom";
import type { Attachment, ChatMessage } from "@/lib/types";
import { cn } from "@/lib/utils";
import { type ArtifactAction, ArtifactActions } from "./ArtifactActions";
import { ArtifactCloseButton } from "./ArtifactCloseButton";
import { Messages } from "./Messages";
import { MultimodalInput } from "./MultimodalInput";
import type { PartRendererMap } from "./renderers/types";

export interface ArtifactOverlayMeta {
	title: string;
	status: "streaming" | "idle";
	boundingBox: {
		top: number;
		left: number;
		width: number;
		height: number;
	};
}

export interface ArtifactShellHeaderMeta {
	// Optional precomputed subtitle shown under the title (e.g. "Version 11 • Created 2 minutes ago")
	subtitle?: string;
	isSaving?: boolean;
}

export interface ArtifactShellProps {
	overlay: ArtifactOverlayMeta;
	isVisible: boolean;
	isMobile?: boolean;
	readonly?: boolean;
	// Chat sidebar
	messages: ChatMessage[];
	votes?: ChatMessageVote[];
	status: UseChatHelpers<ChatMessage>["status"];
	attachments: Attachment[];
	partRenderers?: PartRendererMap;
	onMessageSubmit: (data: { text: string; attachments: Attachment[] }) => void;
	onStop: () => void;
	onFileUpload: (files: File[]) => Promise<Attachment[]>;
	onMessageEdit?: (messageId: string, content: string) => void;
	onCopy?: (content: string) => void;
	onVote?: (messageId: string, isUpvote: boolean) => void;

	// Header/actions
	headerMeta?: ArtifactShellHeaderMeta;
	actions?: ArtifactAction[];
	onClose: () => void;

	// Content area
	children: React.ReactNode;
	interactionDisabled?: boolean; // e.g. when viewing non-latest version
	footer?: React.ReactNode;
	className?: string;
}

export function ArtifactShell({
	overlay,
	isVisible,
	isMobile = false,
	readonly = false,
	messages,
	votes,
	status,
	attachments,
	partRenderers,
	onMessageSubmit,
	onStop,
	onFileUpload,
	onMessageEdit,
	onCopy,
	onVote,
	headerMeta,
	actions = [],
	onClose,
	children,
	interactionDisabled = false,
	footer,
	className,
}: ArtifactShellProps) {
	const { width: windowWidth, height: windowHeight } = useWindowSize();

	// Scroll management for the chat sidebar
	const { containerRef, endRef, isAtBottom, scrollToBottom } =
		useScrollToBottom();

	return (
		<AnimatePresence mode="wait">
			{isVisible && (
				<motion.div
					data-testid="artifact"
					className={cn(
						"flex flex-row h-dvh w-dvw fixed top-0 left-0 z-100 bg-transparent",
						className,
					)}
					initial={{ opacity: 1 }}
					animate={{ opacity: 1 }}
					exit={{
						opacity: 0,
						transition: { delay: 0.3, duration: 0.2, ease: "easeInOut" },
					}}
				>
					{!isMobile && (
						<motion.div
							className="fixed bg-background h-dvh"
							initial={{ width: windowWidth, right: 0 }}
							animate={{ width: windowWidth, right: 0 }}
							exit={{
								width: windowWidth,
								right: 0,
								transition: { duration: 0.3, ease: [0.32, 0.72, 0, 1] },
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
								transition: { duration: 0.3, ease: [0.32, 0.72, 0, 1] },
							}}
						>
							<AnimatePresence>
								{interactionDisabled && (
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
									partRenderers={partRenderers}
								/>

								<div className="flex flex-col gap-1 items-center w-full px-4 pb-2 -mt-20 relative z-10 bg-gradient-to-t from-muted dark:from-background/30 from-60% to-transparent pt-8">
									<MultimodalInput
										status={status === "streaming" ? "submitted" : "ready"}
										onStop={onStop}
										attachments={attachments}
										onAttachmentsChange={() => {}}
										onFileUpload={onFileUpload}
										onSubmit={onMessageSubmit}
										className="bg-background dark:bg-muted"
										readonly={readonly}
										isAtBottom={isAtBottom}
										scrollToBottom={scrollToBottom}
										isCurrentVersion={!interactionDisabled}
									/>
									<p className="text-center text-balance text-xs text-muted-foreground px-4">
										Heph can make mistakes.
									</p>
								</div>
							</div>
						</motion.div>
					)}

					<motion.div
						className="fixed dark:bg-muted bg-background h-dvh flex flex-col md:border-l dark:border-zinc-700 border-zinc-200"
						initial={{
							opacity: 1,
							x: overlay.boundingBox.left,
							y: overlay.boundingBox.top,
							height: overlay.boundingBox.height,
							width: overlay.boundingBox.width,
							borderRadius: 50,
						}}
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
									<div className="font-medium">{overlay.title}</div>
									{headerMeta?.isSaving ? (
										<div className="text-sm text-muted-foreground">
											Saving changes...
										</div>
									) : headerMeta?.subtitle ? (
										<div className="text-sm text-muted-foreground">
											{headerMeta.subtitle}
										</div>
									) : (
										<div className="w-32 h-3 mt-2 bg-muted-foreground/20 rounded-md animate-pulse" />
									)}
								</div>
							</div>

							<ArtifactActions
								actions={actions}
								isStreaming={overlay.status === "streaming"}
							/>
						</div>

						<div className="flex-1 min-h-0 h-full overflow-hidden">
							{children}
						</div>

						<AnimatePresence>{footer}</AnimatePresence>
					</motion.div>
				</motion.div>
			)}
		</AnimatePresence>
	);
}
