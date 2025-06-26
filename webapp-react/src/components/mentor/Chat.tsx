import { cn } from "@/lib/utils";
import { MessageComposer } from "./MessageComposer";
import { type ChatMessage, MessagesContainer } from "./MessagesContainer";

interface ChatProps extends React.ComponentProps<"div"> {
	messages: ChatMessage[];
	onMessageSubmit: (message: string) => void;
	onStop?: () => void;
	isStreaming?: boolean;
	streamingMessageId?: string;
	disabled?: boolean;
	placeholder?: string;
	error?: Error | null;
	onRetry?: () => void;
}

/**
 * Complete chat interface combining messages display and input composer.
 * Provides a full-featured chat experience with streaming support.
 */
function Chat({
	className,
	messages,
	onMessageSubmit,
	onStop,
	isStreaming = false,
	streamingMessageId,
	disabled = false,
	placeholder,
	error,
	onRetry,
	...props
}: ChatProps) {
	const showError = error && !isStreaming;

	return (
		<div
			data-slot="chat"
			className={cn(
				"flex flex-col h-full bg-background border rounded-lg overflow-hidden",
				className,
			)}
			{...props}
		>
			{/* Header */}
			<div className="border-b bg-muted/50 px-4 py-3">
				<div className="flex items-center justify-between">
					<div>
						<h2 className="text-lg font-semibold">AI Mentor</h2>
						<p className="text-sm text-muted-foreground">
							Your friendly coding companion
						</p>
					</div>
					<div className="flex items-center gap-2">
						{isStreaming && (
							<div className="flex items-center gap-2 text-sm text-muted-foreground">
								<div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
								Thinking...
							</div>
						)}
					</div>
				</div>
			</div>

			{/* Messages */}
			<MessagesContainer
				messages={messages}
				isStreaming={isStreaming}
				streamingMessageId={streamingMessageId}
				className="flex-1"
			/>

			{/* Error State */}
			{showError && (
				<div className="border-t bg-destructive/5 border-destructive/20 p-4">
					<div className="flex items-center justify-between max-w-4xl mx-auto">
						<div className="flex items-center gap-2 text-destructive">
							<div className="w-2 h-2 bg-destructive rounded-full" />
							<span className="text-sm font-medium">
								Something went wrong. Please try again.
							</span>
						</div>
						{onRetry && (
							<button
								type="button"
								onClick={onRetry}
								className="text-sm text-destructive hover:underline"
							>
								Retry
							</button>
						)}
					</div>
				</div>
			)}

			{/* Message Composer */}
			<MessageComposer
				onSubmit={onMessageSubmit}
				onStop={onStop}
				isStreaming={isStreaming}
				disabled={disabled || !!error}
				placeholder={placeholder}
			/>
		</div>
	);
}

export { Chat, type ChatProps };
