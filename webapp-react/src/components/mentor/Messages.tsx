import type { ChatMessageVote } from "@/api/types.gen";
import { ScrollArea } from "@/components/ui/scroll-area";
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
	/** Whether to show greeting when no messages */
	showGreeting?: boolean;
	/** Layout variant for different contexts */
	variant?: "default" | "artifact";
	/** Container ref for scroll management */
	containerRef?: RefObject<HTMLDivElement | null>;
	/** End ref for scroll management */
	endRef?: RefObject<HTMLDivElement | null>;
	/** Handler for message editing */
	onMessageEdit?: (messageId: string, newContent: string) => void;
	/** Handler for copying message content */
	onCopy?: (content: string) => void;
	/** Handler for voting on messages */
	onVote?: (messageId: string, isUpvote: boolean) => void;
	/** Handler for document interactions */
	onDocumentClick?: (documentId: string, boundingBox: DOMRect) => void;
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
	showGreeting = true,
	variant = "default",
	containerRef,
	endRef,
	onMessageEdit,
	onCopy,
	onVote,
	onDocumentClick,
	onDocumentSave,
	className,
}: MessagesProps) {
	const isArtifact = variant === "artifact";

	return (
		<ScrollArea
			className="flex flex-col w-full flex-1 min-h-0"
			ref={containerRef}
		>
			<div
				className={cn(
					"flex flex-col w-full pb-16",
					{
						// Default layout - centered like the input
						"min-w-0 gap-2 flex-1 pt-4 relative mx-auto md:max-w-3xl":
							!isArtifact,
						// Artifact layout
						"gap-2 flex-1 px-0 pt-4": isArtifact,
						"gap-4": readonly,
					},
					className,
				)}
			>
				{messages.length === 0 && showGreeting && <Greeting />}

				{messages.map((message, index) => (
					<PreviewMessage
						key={message.id}
						message={message}
						isLoading={status === "streaming" && messages.length - 1 === index}
						vote={votes?.find((vote) => vote.messageId === message.id)}
						readonly={readonly}
						variant={variant}
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
					className="shrink-0 min-w-[12px] min-h-[12px]"
					data-testid="scroll-anchor"
				/>
			</div>
		</ScrollArea>
	);
}

export const Messages = memo(PureMessages, (prevProps, nextProps) => {
	if (prevProps.status !== nextProps.status) return false;
	if (prevProps.readonly !== nextProps.readonly) return false;
	if (prevProps.showThinking !== nextProps.showThinking) return false;
	if (prevProps.showGreeting !== nextProps.showGreeting) return false;
	if (prevProps.variant !== nextProps.variant) return false;
	if (prevProps.messages.length !== nextProps.messages.length) return false;
	if (!equal(prevProps.messages, nextProps.messages)) return false;
	if (!equal(prevProps.votes, nextProps.votes)) return false;

	return true;
});
