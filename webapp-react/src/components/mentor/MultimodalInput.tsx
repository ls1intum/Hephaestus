import cx from "classnames";
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
import { useScrollToBottom } from "@/hooks/use-scroll-to-bottom";
import type { Attachment } from "@/lib/types";
import { AnimatePresence, motion } from "framer-motion";
import { ArrowDown } from "lucide-react";
import { ArrowUpIcon, PaperclipIcon, StopIcon } from "./Icons";
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

	const { isAtBottom, scrollToBottom } = useScrollToBottom();

	useEffect(() => {
		if (status === "submitted") {
			scrollToBottom();
		}
	}, [status, scrollToBottom]);

	const canSubmit =
		input.trim().length > 0 && uploadQueue.length === 0 && !readonly;

	return (
		<div className="relative w-full flex flex-col gap-4">
			<AnimatePresence>
				{!isAtBottom && (
					<motion.div
						initial={{ opacity: 0, y: 10 }}
						animate={{ opacity: 1, y: 0 }}
						exit={{ opacity: 0, y: 10 }}
						transition={{ type: "spring", stiffness: 300, damping: 20 }}
						className="absolute left-1/2 -top-12 -translate-x-1/2 z-50"
					>
						<Button
							data-testid="scroll-to-bottom-button"
							className="rounded-full"
							size="icon"
							variant="outline"
							onClick={(event) => {
								event.preventDefault();
								scrollToBottom();
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

			<Textarea
				data-testid="multimodal-input"
				ref={textareaRef}
				placeholder={placeholder}
				value={input}
				onChange={handleInput}
				readOnly={readonly}
				className={cx(
					"min-h-[24px] max-h-[calc(75dvh)] overflow-hidden resize-none rounded-2xl !text-base bg-muted pb-10 dark:border-zinc-700",
					readonly && "cursor-not-allowed opacity-60",
					className,
				)}
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

			<div className="absolute bottom-0 p-2 w-fit flex flex-row justify-start">
				{!disableAttachments && (
					<AttachmentsButton
						fileInputRef={fileInputRef}
						status={status}
						readonly={readonly}
					/>
				)}
			</div>

			<div className="absolute bottom-0 right-0 p-2 w-fit flex flex-row justify-end">
				{status === "submitted" ? (
					<StopButton onStop={onStop} />
				) : (
					<SendButton onSubmit={submitForm} disabled={!canSubmit} />
				)}
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
			className="rounded-md rounded-bl-lg p-[7px] h-fit dark:border-zinc-700 hover:dark:bg-zinc-900 hover:bg-zinc-200"
			onClick={(event) => {
				event.preventDefault();
				fileInputRef.current?.click();
			}}
			disabled={status !== "ready" || readonly}
			variant="ghost"
		>
			<PaperclipIcon size={14} />
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
			<StopIcon size={14} />
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
			<ArrowUpIcon size={14} />
		</Button>
	);
}

const SendButton = memo(PureSendButton);
