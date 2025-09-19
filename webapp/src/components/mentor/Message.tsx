import { AnimatePresence, motion } from "framer-motion";
import { useState } from "react";
import { Streamdown } from "streamdown";
import type { ChatMessageVote } from "@/api/types.gen";
import type { ChatMessage } from "@/lib/types";
import { cn, sanitizeText } from "@/lib/utils";
import { MentorAvatar } from "./MentorAvatar";
import { MessageActions } from "./MessageActions";
import { MessageEditor } from "./MessageEditor";
import { MessageReasoning } from "./MessageReasoning";
import { PreviewAttachment } from "./PreviewAttachment";
import type {
	PartRenderer,
	PartRendererMap,
	ToolPart,
} from "./renderers/types";

export interface MessageProps {
	/** The message to display */
	message: ChatMessage;
	/** Current vote state for this message */
	vote?: ChatMessageVote;
	/** Whether the message is currently being processed */
	isLoading?: boolean;
	/** Whether the message is in readonly mode (disables actions) */
	readonly?: boolean;
	/** Layout variant for different contexts */
	variant?: "default" | "artifact";
	/** Handler for message editing submission */
	onMessageEdit?: (messageId: string, newContent: string) => void;
	/** Handler for copying message content */
	onCopy?: (content: string) => void;
	/** Handler for voting on messages */
	onVote?: (messageId: string, isUpvote: boolean) => void;
	/** Optional CSS class name */
	className?: string;
	/** Whether to show the edit mode initially */
	initialEditMode?: boolean;
	/** Injected renderers for tool parts (keeps component presentational) */
	partRenderers?: PartRendererMap;
}

export function PreviewMessage({
	message,
	vote,
	isLoading = false,
	readonly = false,
	variant = "default",
	onMessageEdit,
	onCopy,
	onVote,
	className,
	initialEditMode = false,
	partRenderers,
}: MessageProps) {
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
					{message.role === "assistant" && (
						<MentorAvatar
							streaming={isLoading && message.role === "assistant"}
						/>
					)}

					<div className="flex flex-col gap-4 w-full">
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
								// Consider reasoning "done" as soon as any following text part has started streaming
								const hasAnswerTextStarted = message.parts.some(
									(p, i2) =>
										i2 > index &&
										p.type === "text" &&
										typeof p.text === "string" &&
										p.text.trim().length > 0,
								);
								const isReasoningLoading = Boolean(
									isLoading && !hasAnswerTextStarted,
								);

								return (
									<MessageReasoning
										key={key}
										isLoading={isReasoningLoading}
										reasoning={part.text}
										variant={variant}
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
												// User bubbles should shrink to their content, align right, with a small left inset
												"self-end w-fit min-w-0 bg-primary text-primary-foreground px-3 py-2 rounded-xl ml-5":
													message.role === "user",
											})}
										>
											<Streamdown>{sanitizeText(part.text)}</Streamdown>
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

							if (type.startsWith("tool-")) {
								if (!partRenderers) return null;
								const toolKey =
									(part as { toolCallId?: string }).toolCallId || key;
								const isAnyToolPart = (p: unknown): p is ToolPart =>
									Boolean(
										p &&
											typeof (p as { type?: unknown }).type === "string" &&
											(p as { type: string }).type.startsWith("tool-"),
									);
								if (!isAnyToolPart(part)) return null;
								const renderers = partRenderers as unknown as Record<
									string,
									PartRenderer
								>;
								const Renderer = renderers[type];
								return Renderer ? (
									<Renderer
										key={toolKey}
										message={message}
										part={part}
										variant={variant}
									/>
								) : null;
							}

							return null;
						})}

						{!readonly && (
							<MessageActions
								className="-mt-3"
								key={`action-${message.id}`}
								messageContentToCopy={message.parts
									.filter((p) => p.type === "text")
									.map((p) => p.text)
									.join("\n")}
								messageRole={message.role}
								vote={vote}
								isLoading={isLoading}
								isInEditMode={mode === "edit"}
								variant={variant}
								onCopy={(text) => onCopy?.(text)}
								onVote={
									message.role === "assistant"
										? (isUpvote) => onVote?.(message.id, isUpvote)
										: undefined
								}
								onEdit={
									message.role === "user" ? () => setMode("edit") : undefined
								}
							/>
						)}
					</div>
				</div>
			</motion.div>
		</AnimatePresence>
	);
}

export const ThinkingMessage = () => {
	const role = "assistant";

	return (
		<motion.div
			data-testid="message-assistant-loading"
			className="w-full mx-auto max-w-3xl px-4 group/message min-h-96"
			initial={{ y: 5, opacity: 0 }}
			animate={{ y: 0, opacity: 1 }}
			data-role={role}
		>
			<div
				className={cn(
					"flex gap-4 group-data-[role=user]/message:px-3 w-full group-data-[role=user]/message:w-fit group-data-[role=user]/message:ml-auto group-data-[role=user]/message:max-w-2xl group-data-[role=user]/message:py-2 rounded-xl",
					{
						"group-data-[role=user]/message:bg-muted": true,
					},
				)}
			>
				<MentorAvatar streaming={true} />

				<div className="flex flex-col gap-2 w-full">
					<div className="flex flex-col gap-4 text-muted-foreground">
						Hmm...
					</div>
				</div>
			</div>
		</motion.div>
	);
};
