import { useCallback, useRef } from "react";

import { cn } from "@/lib/utils";
import type { UIMessage } from "@ai-sdk/react";
import { ChatInput, type ChatInputRef } from "./ChatInput";
import { ChatMessages } from "./ChatMessages";

interface ChatProps extends React.ComponentProps<"div"> {
	messages: UIMessage[];
	onSendMessage: (message: string) => void;
	onStop?: () => void;
	onRegenerate?: () => void;
	isLoading?: boolean;
	error?: Error | null;
	disabled?: boolean;
	placeholder?: string;
	className?: string;
}

/**
 * Minimal chat interface component focused on messaging without visual clutter.
 * Provides a clean, distraction-free chat experience with AI SDK v5 compatibility.
 *
 * Features:
 * - Clean, minimal design without borders or headers
 * - Enhanced messages display with proper styling
 * - Auto-scrolling message container
 * - Loading states and error handling
 * - Message regeneration for the last assistant message
 * - Modern input with auto-resize and keyboard shortcuts
 */
function ChatOld({
	className,
	messages,
	onSendMessage,
	onStop,
	onRegenerate,
	isLoading = false,
	error,
	disabled = false,
	placeholder = "Ask me anything about software development, best practices, or technical concepts...",
	...props
}: ChatProps) {
	const inputRef = useRef<ChatInputRef>(null);

	const handleSendMessage = useCallback(
		(message: string) => {
			onSendMessage(message);
			// Focus back on input after sending
			setTimeout(() => inputRef.current?.focus(), 100);
		},
		[onSendMessage],
	);

	const handleRegenerate = useCallback(() => {
		if (onRegenerate) {
			onRegenerate();
			inputRef.current?.focus();
		}
	}, [onRegenerate]);

	// Check if we can regenerate (last message is from assistant)
	const lastMessage = messages[messages.length - 1];
	const canRegenerate =
		lastMessage?.role === "assistant" && !isLoading && !!onRegenerate;

	return (
		<div
			data-testid="chat"
			className={cn("flex flex-col h-full", className)}
			{...props}
		>
			{/* Messages */}
			<ChatMessages
				messages={messages}
				isLoading={isLoading}
				error={error}
				onRegenerate={canRegenerate ? handleRegenerate : undefined}
				className="flex-1"
			/>

			{/* Input */}
			<ChatInput
				ref={inputRef}
				onSubmit={handleSendMessage}
				onStop={onStop}
				isStreaming={isLoading}
				disabled={disabled}
				placeholder={placeholder}
				autoFocus
			/>
		</div>
	);
}

export { ChatOld, type ChatProps };
