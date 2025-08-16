import { Copy, History, MessageCircle, Pen, Redo2, Undo2 } from "lucide-react";
import { toast } from "sonner";
// import { DiffView } from '@/components/diffview';
import { TextArtifact } from "../TextArtifact";
import { Artifact } from "./create-artifact";

// biome-ignore lint/suspicious/noEmptyInterface: Empty interface is used for metadata
interface TextArtifactMetadata {}

export const textArtifact = new Artifact<"text", TextArtifactMetadata>({
	kind: "text",
	description: "Useful for text content, like drafting essays and emails.",
	onStreamPart: ({ streamPart, setArtifact }) => {
		const part = streamPart as unknown as { type: string; data: unknown };
		// Handle generic data stream parts from tools
		if (part.type === "data-id") {
			setArtifact((draft) => ({
				...draft,
				documentId: String(part.data ?? ""),
				status: "streaming",
			}));
			return;
		}
		if (part.type === "data-title") {
			setArtifact((draft) => ({
				...draft,
				title: String(part.data ?? ""),
				status: "streaming",
			}));
			return;
		}
		if (part.type === "data-kind") {
			// no-op for text artifact; ensure status reflects streaming
			setArtifact((draft) => ({ ...draft, status: "streaming" }));
			return;
		}
		if (part.type === "data-clear") {
			setArtifact((draft) => ({ ...draft, content: "", status: "streaming" }));
			return;
		}
		if (part.type === "data-finish") {
			setArtifact((draft) => ({ ...draft, status: "idle" }));
			return;
		}
		if (streamPart.type === "data-textDelta") {
			setArtifact((draftArtifact) => {
				return {
					...draftArtifact,
					content: draftArtifact.content + streamPart.data,
					isVisible:
						draftArtifact.status === "streaming" &&
						draftArtifact.content.length > 400 &&
						draftArtifact.content.length < 450
							? true
							: draftArtifact.isVisible,
					status: "streaming",
				};
			});
		}
	},
	content: ({
		mode,
		status,
		content,
		isCurrentVersion,
		currentVersionIndex,
		onSaveContent,
		getDocumentContentById: _getDocumentContentById,
		isLoading,
	}) => (
		<TextArtifact
			content={content}
			mode={mode}
			status={status}
			isCurrentVersion={isCurrentVersion}
			currentVersionIndex={currentVersionIndex}
			onSaveContent={onSaveContent}
			isLoading={isLoading}
		/>
	),
	actions: [
		{
			icon: <History size={18} />,
			description: "View changes",
			onClick: ({ handleVersionChange }) => {
				handleVersionChange("toggle");
			},
			isDisabled: ({ currentVersionIndex }) => {
				if (currentVersionIndex === 0) {
					return true;
				}

				return false;
			},
		},
		{
			icon: <Undo2 size={18} />,
			description: "View Previous version",
			onClick: ({ handleVersionChange }) => {
				handleVersionChange("prev");
			},
			isDisabled: ({ currentVersionIndex }) => {
				if (currentVersionIndex === 0) {
					return true;
				}

				return false;
			},
		},
		{
			icon: <Redo2 size={18} />,
			description: "View Next version",
			onClick: ({ handleVersionChange }) => {
				handleVersionChange("next");
			},
			isDisabled: ({ isCurrentVersion }) => {
				if (isCurrentVersion) {
					return true;
				}

				return false;
			},
		},
		{
			icon: <Copy size={18} />,
			description: "Copy to clipboard",
			onClick: ({ content }) => {
				navigator.clipboard.writeText(content);
				toast.success("Copied to clipboard!");
			},
		},
	],
	toolbar: [
		{
			icon: <Pen />,
			description: "Add final polish",
			onClick: ({ sendMessage }) => {
				sendMessage({
					role: "user",
					parts: [
						{
							type: "text",
							text: "Please add final polish and check for grammar, add section titles for better structure, and ensure everything reads smoothly.",
						},
					],
				});
			},
		},
		{
			icon: <MessageCircle />,
			description: "Request suggestions",
			onClick: ({ sendMessage }) => {
				sendMessage({
					role: "user",
					parts: [
						{
							type: "text",
							text: "Please add suggestions you have that could improve the writing.",
						},
					],
				});
			},
		},
	],
});
