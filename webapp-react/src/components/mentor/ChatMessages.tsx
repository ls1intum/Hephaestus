import { useEffect, useRef } from "react";

import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";
import type { UIMessage } from "@ai-sdk/react";
import { Message } from "./Message";

interface ChatMessagesProps extends Omit<React.ComponentProps<"div">, "dir"> {
	messages: UIMessage[];
	isLoading?: boolean;
	error?: Error | null;
	onRegenerate?: () => void;
	className?: string;
}

/**
 * Clean messages container with auto-scroll functionality.
 * Uses Message component to handle all message states (regular, loading, error).
 */
function ChatMessages({
	className,
	messages,
	isLoading = false,
	error,
	onRegenerate,
	...props
}: ChatMessagesProps) {
	const scrollAreaRef = useRef<HTMLDivElement>(null);
	const shouldAutoScroll = useRef(true);
	const lastMessageCount = useRef(messages.length);

	// Auto-scroll logic with user scroll detection
	useEffect(() => {
		const scrollElement = scrollAreaRef.current?.querySelector(
			"[data-radix-scroll-area-viewport]",
		) as HTMLElement;

		if (!scrollElement) return;

		// Check if user has scrolled up
		const handleScroll = () => {
			const { scrollTop, scrollHeight, clientHeight } = scrollElement;
			const isNearBottom = scrollHeight - scrollTop - clientHeight < 100;
			shouldAutoScroll.current = isNearBottom;
		};

		scrollElement.addEventListener("scroll", handleScroll);

		return () => scrollElement.removeEventListener("scroll", handleScroll);
	}, []);

	// Auto-scroll when new messages arrive or when loading
	useEffect(() => {
		if (
			shouldAutoScroll.current ||
			messages.length > lastMessageCount.current
		) {
			const scrollElement = scrollAreaRef.current?.querySelector(
				"[data-radix-scroll-area-viewport]",
			) as HTMLElement;

			if (scrollElement) {
				scrollElement.scrollTop = scrollElement.scrollHeight;
			}
		}

		lastMessageCount.current = messages.length;
	}, [messages]);

	return (
		<div
			data-testid="chat-messages"
			className={cn("flex flex-1 flex-col", className)}
			{...props}
		>
			<ScrollArea ref={scrollAreaRef} className="flex-1">
				<div className="space-y-6 p-4">
					{/* Render regular messages */}
					{messages.map((message) => (
						<Message key={message.id} type="message" message={message} />
					))}

					{/* Show loading state */}
					{isLoading && <Message type="loading" />}

					{/* Show error state */}
					{error && !isLoading && (
						<Message type="error" error={error} onRetry={onRegenerate} />
					)}
				</div>
			</ScrollArea>
		</div>
	);
}

export { ChatMessages, type ChatMessagesProps };
