import type { ChatMessageVote, Document } from "@/api/types.gen";
import type { ChatMessage } from "@/lib/types";
import { cn, sanitizeText } from "@/lib/utils";
import cx from "classnames";
import equal from "fast-deep-equal";
import { AnimatePresence, motion } from "framer-motion";
import { memo, useState } from "react";
import { DocumentPreview } from "./DocumentPreview";
import { DocumentTool } from "./DocumentTool";
import { Markdown } from "./Markdown";
import { MentorAvatar } from "./MentorAvatar";
import { MessageActions } from "./MessageActions";
import { MessageEditor } from "./MessageEditor";
import { MessageReasoning } from "./MessageReasoning";
import { PreviewAttachment } from "./PreviewAttachment";
import { WeatherTool } from "./WeatherTool";

export interface MessageProps {
	/** The message to display */
	message: ChatMessage;
	/** Current vote state for this message */
	vote?: ChatMessageVote;
	/** Whether the message is currently being processed */
	isLoading?: boolean;
	/** Whether the message is in readonly mode (disables actions) */
	readonly?: boolean;
	/** Whether to add extra padding for smooth scrolling */
	requiresScrollPadding?: boolean;
	/** Layout variant for different contexts */
	variant?: "default" | "artifact";
	/** Handler for message editing submission */
	onMessageEdit?: (messageId: string, newContent: string) => void;
	/** Handler for copying message content */
	onCopy?: (content: string) => void;
	/** Handler for voting on messages */
	onVote?: (messageId: string, isUpvote: boolean) => void;
	/** Handler for document interactions */
	onDocumentClick?: (document: Document, boundingBox: DOMRect) => void;
	/** Handler for document content changes */
	onDocumentSave?: (documentId: string, content: string) => void;
	/** Optional CSS class name */
	className?: string;
	/** Whether to show the edit mode initially */
	initialEditMode?: boolean;
}

const PurePreviewMessage = ({
	message,
	vote,
	isLoading = false,
	readonly = false,
	requiresScrollPadding = false,
	variant = "default",
	onMessageEdit,
	onCopy,
	onVote,
	onDocumentClick,
	onDocumentSave,
	className,
	initialEditMode = false,
}: MessageProps) => {
	const [mode, setMode] = useState<"view" | "edit">(
		initialEditMode ? "edit" : "view",
	);

	const attachmentsFromMessage = message.parts.filter(
		(part) => part.type === "file",
	);

	const isArtifact = variant === "artifact";

	return (
		<AnimatePresence>
			<motion.div
				data-testid={`message-${message.role}`}
				className={cn(
					"w-full max-w-3xl px-4 group/message",
					{
						"pl-16": isArtifact && message.role === "user" && mode !== "edit",
					},
					className,
				)}
				initial={{ y: 5, opacity: 0 }}
				animate={{ y: 0, opacity: 1 }}
				data-role={message.role}
			>
				<div
					className={cn(
						"flex gap-4 w-full group-data-[role=user]/message:ml-auto group-data-[role=user]/message:max-w-2xl",
						{
							"w-full": mode === "edit",
							"group-data-[role=user]/message:w-fit": mode !== "edit",
						},
					)}
				>
					{message.role === "assistant" && <MentorAvatar />}

					<div
						className={cn("flex flex-col gap-4 w-full", {
							"min-h-96": message.role === "assistant" && requiresScrollPadding,
						})}
					>
						{attachmentsFromMessage.length > 0 && (
							<div
								data-testid="message-attachments"
								className="flex flex-row justify-end gap-2"
							>
								{attachmentsFromMessage.map((attachment) => (
									<PreviewAttachment
										key={attachment.url}
										attachment={{
											name: attachment.filename ?? "file",
											contentType: attachment.mediaType,
											url: attachment.url,
										}}
									/>
								))}
							</div>
						)}

						{message.parts?.map((part, index) => {
							const { type } = part;
							const key = `message-${message.id}-part-${index}`;

							if (type === "reasoning" && part.text?.trim().length > 0) {
								return (
									<MessageReasoning
										key={key}
										isLoading={isLoading}
										reasoning={part.text}
									/>
								);
							}

							if (type === "text") {
								if (mode === "view") {
									return (
										<div
											key={key}
											data-testid="message-content"
											className={cn("flex flex-col gap-4", {
												"bg-primary text-primary-foreground px-3 py-2 rounded-xl":
													message.role === "user",
											})}
										>
											<Markdown>{sanitizeText(part.text)}</Markdown>
										</div>
									);
								}

								if (mode === "edit") {
									return (
										<div key={key} className="flex flex-row gap-2 items-start">
											<div className="size-8" />

											<MessageEditor
												key={message.id}
												initialContent={part.text}
												onCancel={() => setMode("view")}
												onSend={(content) => {
													onMessageEdit?.(message.id, content);
													setMode("view");
												}}
											/>
										</div>
									);
								}
							}

							if (type === "tool-getWeather") {
								const { toolCallId, state } = part;

								if (state === "input-available") {
									return (
										<div key={toolCallId} className="skeleton">
											<WeatherTool />
										</div>
									);
								}

								if (state === "output-available") {
									const { output } = part;
									// TODO: Fix WeatherTool type integration
									return (
										<div key={toolCallId}>
											<div className="p-4 border rounded">
												Weather data: {JSON.stringify(output)}
											</div>
										</div>
									);
								}
							}

							if (type === "tool-createDocument") {
								const { toolCallId, state } = part;

								if (state === "input-available") {
									const { input } = part;
									return (
										<div key={toolCallId}>
											<DocumentTool
												type="create"
												isLoading={true}
												args={input as { title: string; kind: "text" }}
											/>
										</div>
									);
								}

								if (state === "output-available") {
									const { output } = part;

									if (
										output &&
										typeof output === "object" &&
										"error" in output
									) {
										return (
											<div
												key={toolCallId}
												className="text-red-500 p-2 border rounded"
											>
												Error: {String(output.error)}
											</div>
										);
									}

									return (
										<div key={toolCallId}>
											<DocumentPreview
												document={output as Document}
												isLoading={false}
												isStreaming={false}
												onDocumentClick={onDocumentClick}
												onSaveContent={(content) => {
													onDocumentSave?.((output as Document).id, content);
												}}
											/>
										</div>
									);
								}
							}

							if (type === "tool-updateDocument") {
								const { toolCallId, state } = part;

								if (state === "input-available") {
									const { input } = part;

									return (
										<div key={toolCallId}>
											<DocumentTool
												type="update"
												isLoading={true}
												args={input as { id: string; description: string }}
											/>
										</div>
									);
								}

								if (state === "output-available") {
									const { output } = part;

									if (
										output &&
										typeof output === "object" &&
										"error" in output
									) {
										return (
											<div
												key={toolCallId}
												className="text-red-500 p-2 border rounded"
											>
												Error: {String(output.error)}
											</div>
										);
									}

									return (
										<div key={toolCallId}>
											<DocumentPreview
												document={output as Document}
												isLoading={false}
												isStreaming={false}
												onDocumentClick={onDocumentClick}
												onSaveContent={(content) => {
													onDocumentSave?.((output as Document).id, content);
												}}
											/>
										</div>
									);
								}
							}

							if (type === "tool-requestSuggestions") {
								const { toolCallId, state } = part;

								if (state === "input-available") {
									const { input } = part;
									return (
										<div key={toolCallId}>
											<DocumentTool
												type="request-suggestions"
												isLoading={true}
												args={input as { documentId: string }}
											/>
										</div>
									);
								}

								if (state === "output-available") {
									const { output } = part;

									if (
										output &&
										typeof output === "object" &&
										"error" in output
									) {
										return (
											<div
												key={toolCallId}
												className="text-red-500 p-2 border rounded"
											>
												Error: {String(output.error)}
											</div>
										);
									}

									return (
										<div key={toolCallId}>
											<DocumentPreview
												document={output as Document}
												isLoading={false}
												isStreaming={false}
												onDocumentClick={onDocumentClick}
												onSaveContent={(content) => {
													onDocumentSave?.((output as Document).id, content);
												}}
											/>
										</div>
									);
								}
							}
						})}

						{!readonly && (
							<MessageActions
								className="-mt-3"
								key={`action-${message.id}`}
								messageContentToCopy={message.parts
									.filter((part) => part.type === "text")
									.map((part) => part.text)
									.join("\n")}
								messageRole={message.role}
								vote={vote}
								isLoading={isLoading}
								isInEditMode={mode === "edit"}
								variant={variant}
								onCopy={(text) => {
									onCopy?.(text);
								}}
								onVote={
									message.role === "assistant"
										? (isUpvote) => {
												onVote?.(message.id, isUpvote);
											}
										: undefined
								}
								onEdit={
									message.role === "user"
										? () => {
												setMode("edit");
											}
										: undefined
								}
							/>
						)}
					</div>
				</div>
			</motion.div>
		</AnimatePresence>
	);
};

export const PreviewMessage = memo(
	PurePreviewMessage,
	(prevProps, nextProps) => {
		if (prevProps.isLoading !== nextProps.isLoading) return false;
		if (prevProps.message.id !== nextProps.message.id) return false;
		if (prevProps.requiresScrollPadding !== nextProps.requiresScrollPadding)
			return false;
		if (prevProps.readonly !== nextProps.readonly) return false;
		if (prevProps.variant !== nextProps.variant) return false;
		if (prevProps.initialEditMode !== nextProps.initialEditMode) return false;
		if (!equal(prevProps.message.parts, nextProps.message.parts)) return false;
		if (!equal(prevProps.vote, nextProps.vote)) return false;

		return true;
	},
);

export const ThinkingMessage = () => {
	const role = "assistant";

	return (
		<motion.div
			data-testid="message-assistant-loading"
			className="w-full mx-auto max-w-3xl px-4 group/message min-h-96"
			initial={{ y: 5, opacity: 0 }}
			animate={{ y: 0, opacity: 1, transition: { delay: 1 } }}
			data-role={role}
		>
			<div
				className={cx(
					"flex gap-4 group-data-[role=user]/message:px-3 w-full group-data-[role=user]/message:w-fit group-data-[role=user]/message:ml-auto group-data-[role=user]/message:max-w-2xl group-data-[role=user]/message:py-2 rounded-xl",
					{
						"group-data-[role=user]/message:bg-muted": true,
					},
				)}
			>
				<MentorAvatar />

				<div className="flex flex-col gap-2 w-full">
					<div className="flex flex-col gap-4 text-muted-foreground">
						Hmm...
					</div>
				</div>
			</div>
		</motion.div>
	);
};
