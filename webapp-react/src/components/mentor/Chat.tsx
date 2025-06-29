import { Bot } from "lucide-react";
import { useCallback, useRef } from "react";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import type { UIMessage } from "@ai-sdk/react";
import { ChatInput, type ChatInputRef } from "./ChatInput";
import { ChatMessages } from "./ChatMessages";
import { MessageActions } from "./MessageActions";

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
 * Complete chat interface component with modern design patterns.
 * Provides a professional, full-featured chat experience with AI SDK v5 compatibility.
 *
 * Features:
 * - Professional header with AI Mentor branding
 * - Enhanced messages display with avatars and proper styling
 * - Auto-scrolling message container
 * - Loading states and error handling
 * - Message regeneration for the last assistant message
 * - Modern input with auto-resize and keyboard shortcuts
 */
function Chat({
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
			className={cn(
				"flex flex-col h-full bg-background border rounded-lg overflow-hidden shadow-sm",
				className,
			)}
			{...props}
		>
			{/* Header */}
			<div className="border-b bg-muted/30 px-6 py-4">
				<div className="flex items-center gap-3">
					<Avatar className="h-10 w-10">
						<AvatarFallback className="bg-primary/10 text-primary">
							<Bot className="h-5 w-5" />
						</AvatarFallback>
					</Avatar>

					<div className="flex-1">
						<h2 className="text-lg font-semibold text-foreground">AI Mentor</h2>
						<p className="text-sm text-muted-foreground">
							Your friendly software development companion
						</p>
					</div>

					{/* Status indicator */}
					{isLoading && (
						<div className="flex items-center gap-2 text-sm text-muted-foreground">
							<div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
							<span>Thinking...</span>
						</div>
					)}
				</div>
			</div>

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

export {
	Chat,
	type ChatProps,
	MessageActions, // Re-export for potential future use
};
