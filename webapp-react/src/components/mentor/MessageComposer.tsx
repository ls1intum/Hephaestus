import { Send, Square } from "lucide-react";
import { type KeyboardEvent, useState } from "react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

interface MessageComposerProps
	extends Omit<React.ComponentProps<"div">, "onSubmit"> {
	onSubmit: (message: string) => void;
	onStop?: () => void;
	placeholder?: string;
	disabled?: boolean;
	isStreaming?: boolean;
	maxLength?: number;
}

/**
 * Message composer component for typing and sending chat messages.
 * Supports keyboard shortcuts and streaming state management.
 */
function MessageComposer({
	className,
	onSubmit,
	onStop,
	placeholder = "Ask me anything about software development...",
	disabled = false,
	isStreaming = false,
	maxLength = 2000,
	...props
}: MessageComposerProps) {
	const [message, setMessage] = useState("");

	const handleSubmit = () => {
		const trimmedMessage = message.trim();
		if (trimmedMessage && !disabled && !isStreaming) {
			onSubmit(trimmedMessage);
			setMessage("");
		}
	};

	const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
		if (e.key === "Enter" && !e.shiftKey) {
			e.preventDefault();
			handleSubmit();
		}
	};

	const handleStop = () => {
		if (onStop) {
			onStop();
		}
	};

	const isSubmitDisabled = disabled || !message.trim() || isStreaming;
	const showStopButton = isStreaming && onStop;

	return (
		<div
			data-slot="message-composer"
			className={cn(
				"border-t bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60 p-4",
				className,
			)}
			{...props}
		>
			<div className="flex gap-2 items-end max-w-4xl mx-auto">
				<div className="flex-1 relative">
					<Textarea
						value={message}
						onChange={(e) => setMessage(e.target.value)}
						onKeyDown={handleKeyDown}
						placeholder={placeholder}
						disabled={disabled}
						maxLength={maxLength}
						className="min-h-[60px] max-h-32 resize-none pr-12"
						rows={1}
					/>
					<div className="absolute bottom-2 right-2 text-xs text-muted-foreground">
						{message.length}/{maxLength}
					</div>
				</div>

				{showStopButton ? (
					<Button
						variant="destructive"
						size="lg"
						onClick={handleStop}
						className="shrink-0"
					>
						<Square className="h-4 w-4" />
						<span className="sr-only">Stop generation</span>
					</Button>
				) : (
					<Button
						onClick={handleSubmit}
						disabled={isSubmitDisabled}
						size="lg"
						className="shrink-0"
					>
						<Send className="h-4 w-4" />
						<span className="sr-only">Send message</span>
					</Button>
				)}
			</div>

			<div className="mt-2 text-xs text-muted-foreground text-center">
				Press <kbd className="px-1 py-0.5 text-xs bg-muted rounded">Enter</kbd>{" "}
				to send,
				<kbd className="px-1 py-0.5 text-xs bg-muted rounded ml-1">
					Shift + Enter
				</kbd>{" "}
				for new line
			</div>
		</div>
	);
}

export { MessageComposer, type MessageComposerProps };
