import { useEffect, useRef } from "react";

import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";
import { Message, type MessagePart } from "./Message";

interface ChatMessage {
	id: string;
	role: "user" | "assistant";
	parts: MessagePart[];
}

interface MessagesContainerProps
	extends Omit<React.ComponentProps<"div">, "dir"> {
	messages: ChatMessage[];
	isStreaming?: boolean;
	streamingMessageId?: string;
}

/**
 * Container component for displaying a list of chat messages with auto-scroll behavior.
 */
function MessagesContainer({
	className,
	messages,
	isStreaming = false,
	streamingMessageId,
	...props
}: MessagesContainerProps) {
	const scrollAreaRef = useRef<HTMLDivElement>(null);

	// Auto-scroll to bottom when new messages arrive or streaming updates
	useEffect(() => {
		if (scrollAreaRef.current) {
			const scrollContainer = scrollAreaRef.current.querySelector(
				"[data-radix-scroll-area-viewport]",
			);
			if (scrollContainer) {
				scrollContainer.scrollTop = scrollContainer.scrollHeight;
			}
		}
	});

	if (messages.length === 0) {
		return (
			<div
				data-slot="messages-container-empty"
				className={cn("flex flex-1 items-center justify-center p-8", className)}
				{...props}
			>
				<div className="text-center text-muted-foreground">
					<div className="text-lg font-medium mb-2">
						Ready to help you learn and grow
					</div>
					<div className="text-sm">
						Ask me anything about software development, best practices, or
						technical concepts.
					</div>
				</div>
			</div>
		);
	}

	return (
		<ScrollArea
			ref={scrollAreaRef}
			data-slot="messages-container"
			className={cn("flex-1 px-4", className)}
			{...props}
		>
			<div className="space-y-4 py-4">
				{messages.map((message) => (
					<Message
						key={message.id}
						role={message.role}
						parts={message.parts}
						isStreaming={isStreaming && message.id === streamingMessageId}
					/>
				))}
			</div>
		</ScrollArea>
	);
}

export { MessagesContainer, type MessagesContainerProps, type ChatMessage };
