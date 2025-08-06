import type React from "react";
import {
	type ChangeEvent,
	memo,
	useCallback,
	useEffect,
	useRef,
	useState,
} from "react";
import { useWindowSize } from "usehooks-ts";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { Attachment } from "@/lib/types";
import { cn } from "@/lib/utils";
import { AnimatePresence, motion } from "framer-motion";
import { ArrowDown } from "lucide-react";
import { ArrowUp, Paperclip, Square } from "lucide-react";
import { PreviewAttachment } from "./PreviewAttachment";
import { SuggestedActions } from "./SuggestedActions";

export interface MultimodalInputProps {
	/** Current upload/submission status */
	status: "ready" | "submitted" | "error";
	/** Handler for stopping current submission */
	onStop: () => void;
	/** Current attachments */
	attachments: Array<Attachment>;
	/** Handler for attachment changes */
	onAttachmentsChange: (attachments: Array<Attachment>) => void;
	/** Handler for file upload */
	onFileUpload: (files: File[]) => Promise<Array<Attachment | undefined>>;
	/** Handler for form submission with text and attachments */
	onSubmit: (data: { text: string; attachments: Array<Attachment> }) => void;
	/** Handler for suggested action clicks */
	onSuggestedAction?: (actionMessage: string) => void;
	/** Optional CSS class name */
	className?: string;
	/** Placeholder text for textarea */
	placeholder?: string;
	/** Whether to show suggested actions (requires onSuggestedAction handler, disabled in readonly mode) */
	showSuggestedActions?: boolean;
	/** Initial input value */
	initialInput?: string;
	/** Whether the input should be readonly */
	readonly?: boolean;
	/** Whether to disable attachment functionality */
	disableAttachments?: boolean;
	/** Whether the scroll container is at the bottom (for scroll button visibility) */
	isAtBottom?: boolean;
	/** Function to scroll to bottom of the chat */
	scrollToBottom?: () => void;
	/** Whether viewing an old version (affects button styling) */
	isCurrentVersion?: boolean;
}

function PureMultimodalInput({
	status,
	onStop,
	attachments,
	onAttachmentsChange,
	onFileUpload,
	onSubmit,
	onSuggestedAction,
	className,
	placeholder = "Send a message...",
	showSuggestedActions,
	initialInput = "",
	readonly = false,
	disableAttachments = false,
	isAtBottom = true,
	scrollToBottom,
	isCurrentVersion = true,
}: MultimodalInputProps) {
	const textareaRef = useRef<HTMLTextAreaElement>(null);
	const fileInputRef = useRef<HTMLInputElement>(null);
	const { width } = useWindowSize();
	const [uploadQueue, setUploadQueue] = useState<Array<string>>([]);

	// Simple internal state management
	const [input, setInput] = useState(initialInput);

	// Update internal state when initialInput changes
	useEffect(() => {
		setInput(initialInput);
	}, [initialInput]);

	// Show suggested actions if explicitly enabled, handler provided, and not readonly
	const shouldShowSuggestedActions =
		showSuggestedActions && onSuggestedAction !== undefined && !readonly;

	const adjustHeight = useCallback(() => {
		if (textareaRef.current) {
			textareaRef.current.style.height = "auto";
			textareaRef.current.style.height = `${textareaRef.current.scrollHeight + 2}px`;
		}
	}, []);

	const resetHeight = useCallback(() => {
		if (textareaRef.current) {
			textareaRef.current.style.height = "auto";
			textareaRef.current.style.height = "98px";
		}
	}, []);

	// Handle initial setup and height adjustment
	useEffect(() => {
		if (textareaRef.current) {
			adjustHeight();
		}
	}, [adjustHeight]);

	// biome-ignore lint/correctness/useExhaustiveDependencies: We need to adjust height when input changes
	useEffect(() => {
		adjustHeight();
	}, [input, adjustHeight]);

	const handleInput = (event: React.ChangeEvent<HTMLTextAreaElement>) => {
		setInput(event.target.value);
	};

	const handleSuggestedAction = useCallback(
		(actionText: string) => {
			setInput(actionText);
			onSuggestedAction?.(actionText);
		},
		[onSuggestedAction],
	);

	const submitForm = useCallback(() => {
		onSubmit({
			text: input,
			attachments,
		});

		// Reset UI state after submission
		setInput("");
		resetHeight();

		if (width && width > 768) {
			textareaRef.current?.focus();
		}
	}, [input, attachments, onSubmit, resetHeight, width]);

	const handleFileChange = useCallback(
		async (event: ChangeEvent<HTMLInputElement>) => {
			const files = Array.from(event.target.files || []);
			if (files.length === 0) return;

			setUploadQueue(files.map((file) => file.name));

			try {
				const uploadedAttachments = await onFileUpload(files);
				const successfullyUploadedAttachments = uploadedAttachments.filter(
					(attachment) => attachment !== undefined,
				);

				onAttachmentsChange([
					...attachments,
					...successfullyUploadedAttachments,
				]);
			} catch (error) {
				console.error("Error uploading files!", error);
			} finally {
				setUploadQueue([]);
			}
		},
		[attachments, onAttachmentsChange, onFileUpload],
	);

	useEffect(() => {
		if (status === "submitted" && scrollToBottom) {
			scrollToBottom();
		}
	}, [status, scrollToBottom]);

	const canSubmit =
		input.trim().length > 0 && uploadQueue.length === 0 && !readonly;

	return (
		<div className="relative w-full flex flex-col gap-4">
			<AnimatePresence>
				{!isAtBottom && isCurrentVersion && (
					<motion.div
						initial={{ opacity: 0, y: 10 }}
						animate={{ opacity: 1, y: 0 }}
						exit={{ opacity: 0, y: 10 }}
						transition={{ type: "spring", stiffness: 300, damping: 20 }}
						className="absolute left-1/2 -top-12 -translate-x-1/2 z-[70] backdrop-blur-sm rounded-full"
					>
						<Button
							data-testid="scroll-to-bottom-button"
							className="rounded-full bg-background/80 dark:bg-background/80 border-border/50 shadow-lg hover:bg-background/90 dark:hover:bg-background/90"
							size="icon"
							variant="outline"
							onClick={(event) => {
								event.preventDefault();
								scrollToBottom?.();
							}}
						>
							<ArrowDown />
						</Button>
					</motion.div>
				)}
			</AnimatePresence>

			{shouldShowSuggestedActions && (
				<SuggestedActions onAction={handleSuggestedAction} />
			)}

			{!disableAttachments && (
				<input
					type="file"
					className="fixed -top-4 -left-4 size-0.5 opacity-0 pointer-events-none"
					ref={fileInputRef}
					multiple
					onChange={handleFileChange}
					tabIndex={-1}
				/>
			)}

			{(attachments.length > 0 || uploadQueue.length > 0) && (
				<div
					data-testid="attachments-preview"
					className="flex flex-row gap-2 overflow-x-scroll items-end"
				>
					{attachments.map((attachment) => (
						<PreviewAttachment key={attachment.url} attachment={attachment} />
					))}

					{uploadQueue.map((filename) => (
						<PreviewAttachment
							key={filename}
							attachment={{
								url: "",
								name: filename,
								contentType: "",
							}}
							isUploading={true}
						/>
					))}
				</div>
			)}

			<div
				className={cn(
					// Base textarea styling - exact copy from ui/textarea with focus-within
					"border-input placeholder:text-muted-foreground focus-within:border-ring focus-within:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive dark:bg-input/30 flex field-sizing-content min-h-16 w-full rounded-xl border bg-transparent px-3 py-2 text-base shadow-xs transition-[color,box-shadow] outline-none focus-within:ring-[3px] disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
					// Custom layout styling
					"flex-col gap-1",
					// Make container clickable to focus input
					"cursor-text",
					readonly && "cursor-not-allowed opacity-60",
					className,
				)}
				onClick={() => {
					if (!readonly && textareaRef.current) {
						textareaRef.current.focus();
					}
				}}
				onKeyDown={(event) => {
					if (event.key === "Enter" || event.key === " ") {
						if (!readonly && textareaRef.current) {
							textareaRef.current.focus();
						}
					}
				}}
			>
				<div className="flex-1">
					<Textarea
						data-testid="multimodal-input"
						ref={textareaRef}
						placeholder={placeholder}
						value={input}
						onChange={handleInput}
						readOnly={readonly}
						className="border-0 bg-transparent outline-none overflow-hidden resize-none !text-base w-full p-0 shadow-none focus-visible:ring-0 min-h-0"
						rows={2}
						autoFocus={!readonly}
						onKeyDown={(event) => {
							if (
								event.key === "Enter" &&
								!event.shiftKey &&
								!event.nativeEvent.isComposing
							) {
								event.preventDefault();

								if (status !== "ready") {
									// Let parent handle status messages
									return;
								}

								if (canSubmit) {
									submitForm();
								}
							}
						}}
					/>
				</div>

				<div className="flex gap-2 justify-between">
					<div className="flex gap-2">
						{!disableAttachments && (
							<AttachmentsButton
								fileInputRef={fileInputRef}
								status={status}
								readonly={readonly}
							/>
						)}
					</div>
					<div>
						{status === "submitted" ? (
							<StopButton onStop={onStop} />
						) : (
							<SendButton onSubmit={submitForm} disabled={!canSubmit} />
						)}
					</div>
				</div>
			</div>
		</div>
	);
}

export const MultimodalInput = memo(
	PureMultimodalInput,
	(prevProps, nextProps) => {
		// Compare all relevant props for re-rendering
		if (prevProps.status !== nextProps.status) return false;
		if (prevProps.attachments.length !== nextProps.attachments.length)
			return false;
		if (prevProps.initialInput !== nextProps.initialInput) return false;
		if (prevProps.showSuggestedActions !== nextProps.showSuggestedActions)
			return false;
		if (prevProps.readonly !== nextProps.readonly) return false;
		if (prevProps.disableAttachments !== nextProps.disableAttachments)
			return false;
		if (prevProps.isAtBottom !== nextProps.isAtBottom) return false;
		if (prevProps.isCurrentVersion !== nextProps.isCurrentVersion) return false;
		return true;
	},
);

function PureAttachmentsButton({
	fileInputRef,
	status,
	readonly,
}: {
	fileInputRef: React.RefObject<HTMLInputElement | null>;
	status: "ready" | "submitted" | "error";
	readonly: boolean;
}) {
	return (
		<Button
			data-testid="attachments-button"
			className="rounded-md rounded-bl-lg p-[7px] dark:border-zinc-700 hover:dark:bg-zinc-900 hover:bg-zinc-200"
			onClick={(event) => {
				event.preventDefault();
				fileInputRef.current?.click();
			}}
			disabled={status !== "ready" || readonly}
			variant="ghost"
			size="icon"
		>
			<Paperclip size={14} />
		</Button>
	);
}

const AttachmentsButton = memo(PureAttachmentsButton);

function PureStopButton({
	onStop,
}: {
	onStop: () => void;
}) {
	return (
		<Button
			data-testid="stop-button"
			className="rounded-full p-1.5 border dark:border-zinc-600"
			onClick={(event) => {
				event.preventDefault();
				onStop();
			}}
			size="icon"
		>
			<Square fill="currentColor" strokeWidth={0} />
		</Button>
	);
}

const StopButton = memo(PureStopButton);

function PureSendButton({
	onSubmit,
	disabled,
}: {
	onSubmit: () => void;
	disabled: boolean;
}) {
	return (
		<Button
			data-testid="send-button"
			className="rounded-full p-1.5 border dark:border-zinc-600"
			onClick={(event) => {
				event.preventDefault();
				onSubmit();
			}}
			disabled={disabled}
			size="icon"
		>
			<ArrowUp size={14} strokeWidth={3} />
		</Button>
	);
}

const SendButton = memo(PureSendButton);
