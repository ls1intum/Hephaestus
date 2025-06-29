import { Send, Square } from "lucide-react";
import {
	type KeyboardEvent,
	forwardRef,
	useCallback,
	useEffect,
	useImperativeHandle,
	useRef,
	useState,
} from "react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

// Constants for better maintainability and performance
const MIN_TEXTAREA_HEIGHT = 56;
const MAX_TEXTAREA_HEIGHT = 128;
const COMPOSITION_END_DELAY = 100;
const DEFAULT_MAX_LENGTH = 2000;

interface ChatInputProps extends Omit<React.ComponentProps<"div">, "onSubmit"> {
	onSubmit: (message: string) => void;
	onStop?: () => void;
	placeholder?: string;
	disabled?: boolean;
	isStreaming?: boolean;
	maxLength?: number;
	autoFocus?: boolean;
	className?: string;
}

interface ChatInputRef {
	focus: () => void;
	clear: () => void;
	setValue: (value: string) => void;
}

/**
 * Enhanced chat input component with auto-resize, keyboard shortcuts, and streaming support.
 * Inspired by chat-ui design patterns but optimized for AI SDK v5.
 */
const ChatInput = forwardRef<ChatInputRef, ChatInputProps>(
	(
		{
			className,
			onSubmit,
			onStop,
			placeholder = "Type your message...",
			disabled = false,
			isStreaming = false,
			maxLength = DEFAULT_MAX_LENGTH,
			autoFocus = false,
			...props
		},
		ref,
	) => {
		const [input, setInput] = useState("");
		const [isComposing, setIsComposing] = useState(false);
		const textareaRef = useRef<HTMLTextAreaElement>(null);

		// Expose ref methods
		useImperativeHandle(ref, () => ({
			focus: () => textareaRef.current?.focus(),
			clear: () => setInput(""),
			setValue: (value: string) => setInput(value),
		}));

		// Auto-focus on mount if specified
		useEffect(() => {
			if (autoFocus && textareaRef.current) {
				textareaRef.current.focus();
			}
		}, [autoFocus]);

		const adjustHeight = useCallback(() => {
			const textarea = textareaRef.current;
			if (!textarea) return;

			// Reset height to calculate new height
			textarea.style.height = "auto";
			const scrollHeight = textarea.scrollHeight;

			// Calculate new height within bounds
			const newHeight = Math.max(
				Math.min(scrollHeight, MAX_TEXTAREA_HEIGHT),
				MIN_TEXTAREA_HEIGHT,
			);

			textarea.style.height = `${newHeight}px`;
		}, []);

		const handleInputChange = useCallback(
			(e: React.ChangeEvent<HTMLTextAreaElement>) => {
				const newValue = e.target.value;

				// Enforce max length
				if (newValue.length <= maxLength) {
					setInput(newValue);
					adjustHeight();
				}
			},
			[maxLength, adjustHeight],
		);

		const handleSubmit = useCallback(() => {
			const trimmedInput = input.trim();
			if (trimmedInput && !disabled && !isStreaming) {
				onSubmit(trimmedInput);
				setInput("");
				// Reset textarea height after submission
				if (textareaRef.current) {
					textareaRef.current.style.height = `${MIN_TEXTAREA_HEIGHT}px`;
				}
			}
		}, [input, disabled, isStreaming, onSubmit]);

		const handleKeyDown = useCallback(
			(e: KeyboardEvent<HTMLTextAreaElement>) => {
				if (disabled || isStreaming) return;

				if (e.key === "Enter" && !e.shiftKey && !isComposing) {
					e.preventDefault();
					handleSubmit();
				}
			},
			[disabled, isStreaming, isComposing, handleSubmit],
		);

		const handleStop = useCallback(() => {
			onStop?.();
		}, [onStop]);

		const isSubmitDisabled = disabled || !input.trim() || isStreaming;
		const showStopButton = isStreaming && onStop;

		// Adjust height when input changes
		useEffect(() => {
			adjustHeight();
		}, [adjustHeight]);

		return (
			<div
				data-testid="chat-input"
				className={cn(
					"flex shrink-0 flex-col gap-4 border-t bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60 p-4",
					className,
				)}
				{...props}
			>
				<form
					onSubmit={(e) => {
						e.preventDefault();
						handleSubmit();
					}}
					className="flex gap-3 items-end max-w-4xl mx-auto w-full"
				>
					<div className="flex-1 relative">
						<Textarea
							ref={textareaRef}
							value={input}
							onChange={handleInputChange}
							onKeyDown={handleKeyDown}
							onCompositionStart={() => setIsComposing(true)}
							onCompositionEnd={() => {
								setTimeout(() => setIsComposing(false), COMPOSITION_END_DELAY);
							}}
							placeholder={placeholder}
							disabled={disabled}
							maxLength={maxLength}
							className={cn(
								"resize-none border rounded-2xl px-4 py-3 min-h-[56px] max-h-32 overflow-y-auto",
								"focus:ring-2 focus:ring-primary/20 focus:border-primary",
								"disabled:opacity-50 disabled:cursor-not-allowed",
								"transition-all duration-200",
							)}
							style={{ height: `${MIN_TEXTAREA_HEIGHT}px` }}
							rows={1}
							spellCheck={false}
							aria-label="Chat message input"
						/>

						{/* Character counter */}
						<div className="absolute bottom-2 right-3 text-xs text-muted-foreground pointer-events-none select-none">
							{input.length}/{maxLength}
						</div>
					</div>

					{/* Submit/Stop button */}
					{showStopButton ? (
						<Button
							type="button"
							variant="destructive"
							size="icon"
							onClick={handleStop}
							className="rounded-full shrink-0"
							aria-label="Stop generation"
						>
							<Square className="h-4 w-4" fill="currentColor" />
						</Button>
					) : (
						<Button
							type="submit"
							disabled={isSubmitDisabled}
							size="icon"
							className="rounded-full shrink-0"
							aria-label="Send message"
						>
							<Send className="h-4 w-4" />
						</Button>
					)}
				</form>

				{/* Keyboard shortcut hint */}
				<div className="text-xs text-muted-foreground text-center max-w-4xl mx-auto">
					Press{" "}
					<kbd className="px-1.5 py-0.5 text-xs bg-muted rounded border">
						Enter
					</kbd>{" "}
					to send,{" "}
					<kbd className="px-1.5 py-0.5 text-xs bg-muted rounded border">
						Shift + Enter
					</kbd>{" "}
					for new line
				</div>
			</div>
		);
	},
);

ChatInput.displayName = "ChatInput";

export { ChatInput, type ChatInputProps, type ChatInputRef };
