import { Copy, History, MessageCircle, Pen, Redo2, Undo2 } from "lucide-react";
import { toast } from "sonner";
import { ScrollArea } from "@/components/ui/scroll-area";
// import { DiffView } from '@/components/diffview';
import { DocumentSkeleton } from "../DocumentSkeleton";
import { TextEditor } from "../TextEditor";
import { Artifact } from "./create-artifact";

// biome-ignore lint/suspicious/noEmptyInterface: Empty interface is used for metadata
interface TextArtifactMetadata {}

export const textArtifact = new Artifact<"text", TextArtifactMetadata>({
	kind: "text",
	description: "Useful for text content, like drafting essays and emails.",
	onStreamPart: ({ streamPart, setArtifact }) => {
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
	}) => {
		if (isLoading) {
			return <DocumentSkeleton artifactKind="text" />;
		}

		if (mode === "diff") {
			// Diff view not implemented yet
			return null; // <DiffView oldContent={old} newContent={new} />
		}

		return (
			<ScrollArea className="h-full">
				{/* biome-ignore lint/a11y/noStaticElementInteractions: Container focuses inner editor for better UX */}
				<div
					className="flex flex-col px-4 py-8 md:px-10 cursor-text"
					style={{ minHeight: "92dvh" }}
					onClick={(e) => {
						// Focus the editor when clicking in the content area
						const editorElement = e.currentTarget.querySelector(".ProseMirror");
						if (editorElement) {
							(editorElement as HTMLElement).focus();
						}
					}}
					onKeyDown={(e) => {
						// Handle keyboard activation (Enter or Space) only if editor is not focused
						if (e.key === "Enter" || e.key === " ") {
							const editorElement =
								e.currentTarget.querySelector(".ProseMirror");
							if (editorElement && document.activeElement !== editorElement) {
								e.preventDefault();
								(editorElement as HTMLElement).focus();
							}
						}
					}}
					// Use presentation role since this is just a click target for the editor
					role="presentation"
				>
					<TextEditor
						content={content}
						isCurrentVersion={isCurrentVersion}
						currentVersionIndex={currentVersionIndex}
						status={status}
						onSaveContent={onSaveContent}
					/>
				</div>
			</ScrollArea>
		);
	},
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
