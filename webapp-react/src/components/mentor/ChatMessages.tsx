import { Bot, RefreshCw } from "lucide-react";
import { useEffect, useRef } from "react";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
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
 * Enhanced empty state component with welcoming message and optional suggestions.
 */
function ChatMessagesEmpty() {
	return (
		<div
			data-testid="chat-messages-empty"
			className="flex flex-1 items-center justify-center p-8"
		>
			<div className="text-center max-w-md mx-auto">
				<div className="mb-6">
					<Avatar className="h-16 w-16 mx-auto">
						<AvatarFallback className="bg-primary/10 text-primary">
							<Bot className="h-8 w-8" />
						</AvatarFallback>
					</Avatar>
				</div>

				<div className="space-y-3">
					<h3 className="text-lg font-semibold text-foreground">
						Welcome to AI Mentor
					</h3>
					<p className="text-sm text-muted-foreground leading-relaxed">
						I'm here to help you learn and grow as a developer. Ask me about
						software development, best practices, code reviews, or any technical
						concepts you'd like to explore.
					</p>
				</div>

				{/* Suggested topics */}
				<div className="mt-6 space-y-2">
					<p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
						Try asking about:
					</p>
					<div className="flex flex-wrap gap-2 justify-center">
						{[
							"React best practices",
							"Code organization",
							"Testing strategies",
							"Performance optimization",
						].map((topic) => (
							<span
								key={topic}
								className="px-3 py-1 bg-muted/50 text-muted-foreground text-xs rounded-full"
							>
								{topic}
							</span>
						))}
					</div>
				</div>
			</div>
		</div>
	);
}

/**
 * Loading indicator component with typing animation.
 */
function ChatMessagesLoading() {
	return (
		<div
			data-testid="chat-messages-loading"
			className="flex items-start gap-3 px-4 py-3"
		>
			<Avatar className="h-8 w-8 shrink-0">
				<AvatarFallback className="bg-primary/10 text-primary">
					<Bot className="h-4 w-4" />
				</AvatarFallback>
			</Avatar>

			<div className="flex-1 space-y-2">
				<div className="flex items-center gap-2">
					<div className="flex space-x-1">
						{[0, 1, 2].map((i) => (
							<div
								key={i}
								className="w-2 h-2 bg-muted-foreground/60 rounded-full animate-pulse"
								style={{
									animationDelay: `${i * 0.2}s`,
									animationDuration: "1s",
								}}
							/>
						))}
					</div>
					<span className="text-xs text-muted-foreground">
						AI Mentor is thinking...
					</span>
				</div>

				{/* Skeleton text lines */}
				<div className="space-y-2">
					<Skeleton className="h-4 w-3/4" />
					<Skeleton className="h-4 w-1/2" />
				</div>
			</div>
		</div>
	);
}

/**
 * Error state component with retry option.
 */
function ChatMessagesError({
	error,
	onRegenerate,
}: {
	error: Error;
	onRegenerate?: () => void;
}) {
	return (
		<div
			data-testid="chat-messages-error"
			className="flex items-start gap-3 px-4 py-3 border-l-2 border-destructive bg-destructive/5"
		>
			<Avatar className="h-8 w-8 shrink-0">
				<AvatarFallback className="bg-destructive/10 text-destructive">
					<Bot className="h-4 w-4" />
				</AvatarFallback>
			</Avatar>

			<div className="flex-1 space-y-3">
				<div>
					<p className="text-sm font-medium text-destructive">
						Something went wrong
					</p>
					<p className="text-xs text-muted-foreground mt-1">
						{error.message || "An unexpected error occurred. Please try again."}
					</p>
				</div>

				{onRegenerate && (
					<Button
						variant="outline"
						size="sm"
						onClick={onRegenerate}
						className="text-xs h-8"
					>
						<RefreshCw className="h-3 w-3 mr-1" />
						Try again
					</Button>
				)}
			</div>
		</div>
	);
}

/**
 * Enhanced messages container with auto-scroll, loading states, and error handling.
 * Inspired by chat-ui patterns but optimized for AI SDK v5.
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

	// Show empty state when no messages
	if (messages.length === 0 && !isLoading && !error) {
		return (
			<div
				data-testid="chat-messages"
				className={cn("flex flex-1 flex-col", className)}
				{...props}
			>
				<ChatMessagesEmpty />
			</div>
		);
	}

	return (
		<div
			data-testid="chat-messages"
			className={cn("flex flex-1 flex-col", className)}
			{...props}
		>
			<ScrollArea ref={scrollAreaRef} className="flex-1">
				<div className="space-y-6 p-4">
					{/* Render messages */}
					{messages.map((message) => (
						<Message key={message.id} message={message} />
					))}

					{/* Show loading indicator */}
					{isLoading && <ChatMessagesLoading />}

					{/* Show error state */}
					{error && !isLoading && (
						<ChatMessagesError error={error} onRegenerate={onRegenerate} />
					)}
				</div>
			</ScrollArea>
		</div>
	);
}

export {
	ChatMessages,
	ChatMessagesEmpty,
	ChatMessagesLoading,
	ChatMessagesError,
	type ChatMessagesProps,
};
