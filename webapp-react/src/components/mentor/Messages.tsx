import type { ChatMessageVote, Document } from "@/api/types.gen";
import type { ChatMessage } from "@/lib/types";
import { cn } from "@/lib/utils";
import type { UseChatHelpers } from "@ai-sdk/react";
import equal from "fast-deep-equal";
import { motion } from "framer-motion";
import { type RefObject, memo } from "react";
import { Greeting } from "./Greeting";
import { PreviewMessage, ThinkingMessage } from "./Message";

export interface MessagesProps {
	/** Array of chat messages to display */
	messages: ChatMessage[];
	/** Array of votes for messages */
	votes?: Array<ChatMessageVote>;
	/** Current chat status */
	status: UseChatHelpers<ChatMessage>["status"];
	/** Whether the interface is in readonly mode */
	readonly?: boolean;
	/** Whether to show thinking message for submissions */
	showThinking?: boolean;
	/** Whether to add padding to the last message for smooth scrolling */
	requiresScrollPadding?: boolean;
	/** Container ref for scroll management */
	containerRef?: RefObject<HTMLDivElement>;
	/** End ref for scroll management */
	endRef?: RefObject<HTMLDivElement>;
	/** Handler for viewport enter events */
	onViewportEnter?: () => void;
	/** Handler for viewport leave events */
	onViewportLeave?: () => void;
	/** Handler for message editing */
	onMessageEdit?: (messageId: string, newContent: string) => void;
	/** Handler for copying message content */
	onCopy?: (content: string) => void;
	/** Handler for voting on messages */
	onVote?: (messageId: string, isUpvote: boolean) => void;
	/** Handler for document interactions */
	onDocumentClick?: (document: Document, boundingBox: DOMRect) => void;
	/** Handler for document content changes */
	onDocumentSave?: (documentId: string, content: string) => void;
	/** Optional CSS class name */
	className?: string;
}

function PureMessages({
	messages,
	votes,
	status,
	readonly = false,
	showThinking = true,
	requiresScrollPadding = false,
	containerRef,
	endRef,
	onViewportEnter,
	onViewportLeave,
	onMessageEdit,
	onCopy,
	onVote,
	onDocumentClick,
	onDocumentSave,
	className,
}: MessagesProps) {
	return (
		<div
			ref={containerRef}
			className={cn(
				"flex flex-col min-w-0 gap-2 flex-1 overflow-y-scroll pt-4 relative",
				{
					"gap-6": readonly,
				},
				className,
			)}
		>
			{messages.length === 0 && <Greeting />}

			{messages.map((message, index) => (
				<PreviewMessage
					key={message.id}
					message={message}
					isLoading={status === "streaming" && messages.length - 1 === index}
					vote={votes?.find((vote) => vote.messageId === message.id)}
					readonly={readonly}
					requiresScrollPadding={
						requiresScrollPadding && index === messages.length - 1
					}
					onMessageEdit={onMessageEdit}
					onCopy={onCopy}
					onVote={onVote}
					onDocumentClick={onDocumentClick}
					onDocumentSave={onDocumentSave}
				/>
			))}

			{showThinking &&
				status === "submitted" &&
				messages.length > 0 &&
				messages[messages.length - 1].role === "user" && <ThinkingMessage />}

			<motion.div
				ref={endRef}
				className="shrink-0 min-w-[24px] min-h-[24px]"
				onViewportLeave={onViewportLeave}
				onViewportEnter={onViewportEnter}
			/>
		</div>
	);
}

export const Messages = memo(PureMessages, (prevProps, nextProps) => {
	if (prevProps.status !== nextProps.status) return false;
	if (prevProps.readonly !== nextProps.readonly) return false;
	if (prevProps.showThinking !== nextProps.showThinking) return false;
	if (prevProps.requiresScrollPadding !== nextProps.requiresScrollPadding)
		return false;
	if (prevProps.messages.length !== nextProps.messages.length) return false;
	if (!equal(prevProps.messages, nextProps.messages)) return false;
	if (!equal(prevProps.votes, nextProps.votes)) return false;

	return true;
});
