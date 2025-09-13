import {
	type ChangeEvent,
	type KeyboardEvent,
	useEffect,
	useRef,
	useState,
} from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

interface MessageEditorProps {
	/** Initial text content to edit */
	initialContent: string;
	/** Whether the editor is currently submitting */
	isSubmitting?: boolean;
	/** Placeholder text for the textarea */
	placeholder?: string;
	/** Callback when cancel is clicked */
	onCancel: () => void;
	/** Callback when send is clicked with the edited content */
	onSend: (content: string) => void;
	/** Optional CSS class name */
	className?: string;
}

export function MessageEditor({
	initialContent,
	isSubmitting = false,
	placeholder = "",
	onCancel,
	onSend,
	className,
}: MessageEditorProps) {
	const [draftContent, setDraftContent] = useState(initialContent);
	const textareaRef = useRef<HTMLTextAreaElement>(null);

	const adjustHeight = () => {
		if (textareaRef.current) {
			textareaRef.current.style.height = "auto";
			textareaRef.current.style.height = `${textareaRef.current.scrollHeight + 2}px`;
		}
	};

	// Initial height adjustment
	// biome-ignore lint/correctness/useExhaustiveDependencies: run once on mount
	useEffect(() => {
		if (textareaRef.current) {
			adjustHeight();
		}
	}, []);

	// Update internal state when initialContent changes
	useEffect(() => {
		setDraftContent(initialContent);
	}, [initialContent]);

	const handleInput = (event: ChangeEvent<HTMLTextAreaElement>) => {
		setDraftContent(event.target.value);
		adjustHeight();
	};

	const handleSend = () => {
		onSend(draftContent);
	};

	const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
		if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
			event.preventDefault();
			handleSend();
		}
		if (event.key === "Escape") {
			event.preventDefault();
			onCancel();
		}
	};

	const hasChanges = draftContent !== initialContent;
	const canSend = draftContent.trim().length > 0 && hasChanges && !isSubmitting;

	return (
		<div
			className={cn(
				// Base textarea styling from ui/textarea - exact copy
				"border-input placeholder:text-muted-foreground focus-within:border-ring focus-within:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive dark:bg-input flex field-sizing-content min-h-16 w-full rounded-xl border bg-white px-3 py-2 text-base shadow-xs transition-[color,box-shadow] outline-none focus-within:ring-[3px] disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
				// Custom layout styling
				"flex-col gap-1",
				className,
			)}
		>
			<div className="flex-1">
				<Textarea
					data-testid="message-editor"
					ref={textareaRef}
					className="border-0 bg-transparent outline-none overflow-hidden resize-none !text-base w-full p-0 shadow-none focus-visible:ring-0 min-h-0"
					placeholder={placeholder}
					value={draftContent}
					onChange={handleInput}
					onKeyDown={handleKeyDown}
					disabled={isSubmitting}
					autoFocus
				/>
			</div>

			<div className="flex gap-2 justify-end">
				<Button
					variant="outline"
					className="rounded-full h-8 px-3"
					onClick={onCancel}
					disabled={isSubmitting}
					size="sm"
				>
					Cancel
				</Button>
				<Button
					data-testid="message-editor-send-button"
					variant="default"
					className="rounded-full h-8 px-3"
					disabled={!canSend}
					onClick={handleSend}
					size="sm"
				>
					{isSubmitting ? "Sending..." : "Send"}
				</Button>
			</div>
		</div>
	);
}
