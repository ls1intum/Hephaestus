import type { UseChatHelpers } from "@ai-sdk/react";
import { motion } from "framer-motion";
import type { RefObject } from "react";
import type { ChatMessageVote } from "@/api/types.gen";
import { ScrollArea } from "@/components/ui/scroll-area";
import type { ChatMessage } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Greeting } from "./Greeting";
import { PreviewMessage, ThinkingMessage } from "./Message";
import type { PartRendererMap } from "./renderers/types";

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
	/** Optional CSS class name */
	className?: string;
	/** Injected renderers for tool parts */
	partRenderers?: PartRendererMap;
}

export function Messages({
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
	className,
	partRenderers,
}: MessagesProps) {
	const isArtifact = variant === "artifact";

	// Determine if a message currently renders any visible content
	const hasVisibleContent = (message: ChatMessage): boolean => {
		const parts = message?.parts ?? [];
		if (parts.length === 0) return false;
		for (const p of parts) {
			if (p.type === "text" && (p.text ?? "").trim().length > 0) return true;
			if (p.type === "reasoning" && (p.text ?? "").trim().length > 0)
				return true;
			if (p.type === "file") return true;
			if (typeof p.type === "string" && p.type.startsWith("tool-")) {
				const state = (p as { state?: string }).state;
				if (state === "input-available" || state === "output-available")
					return true;
			}
		}
		return false;
	};

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

				{messages.map((message, index) => {
					const isLast = index === messages.length - 1;
					const hideEmptyAssistantPlaceholder =
						isLast &&
						message.role === "assistant" &&
						!hasVisibleContent(message) &&
						showThinking &&
						(status === "submitted" || status === "streaming");

					if (hideEmptyAssistantPlaceholder) return null;

					return (
						<PreviewMessage
							key={message.id}
							message={message}
							isLoading={
								status === "streaming" && messages.length - 1 === index
							}
							vote={votes?.find((vote) => vote.messageId === message.id)}
							readonly={readonly}
							variant={variant}
							onMessageEdit={onMessageEdit}
							onCopy={onCopy}
							onVote={onVote}
							partRenderers={partRenderers}
						/>
					);
				})}

				{showThinking &&
					(status === "submitted" || status === "streaming") &&
					(() => {
						if (messages.length === 0) return <ThinkingMessage />;
						const last = messages[messages.length - 1];
						const isUser = last.role === "user";
						const assistantHasVisible =
							last.role === "assistant" && hasVisibleContent(last);
						return isUser || !assistantHasVisible ? <ThinkingMessage /> : null;
					})()}

				<motion.div
					ref={endRef}
					className="shrink-0 min-w-[12px] min-h-[12px]"
					data-testid="scroll-anchor"
				/>
			</div>
		</ScrollArea>
	);
}
