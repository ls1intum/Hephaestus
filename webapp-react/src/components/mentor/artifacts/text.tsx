import { toast } from "sonner";
// import { DiffView } from '@/components/diffview';
import { DocumentSkeleton } from "../DocumentSkeleton";
import {
	ClockRewind,
	CopyIcon,
	MessageIcon,
	PenIcon,
	RedoIcon,
	UndoIcon,
} from "../Icons";
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
		getDocumentContentById,
		isLoading,
		metadata,
	}) => {
		if (isLoading) {
			return <DocumentSkeleton artifactKind="text" />;
		}

		if (mode === "diff") {
			const oldContent = getDocumentContentById(currentVersionIndex - 1);
			const newContent = getDocumentContentById(currentVersionIndex);

			return null; // <DiffView oldContent={oldContent} newContent={newContent} />;
		}

		return (
			<div className="flex flex-row py-8 md:p-20 px-4">
				<TextEditor
					content={content}
					isCurrentVersion={isCurrentVersion}
					currentVersionIndex={currentVersionIndex}
					status={status}
					onSaveContent={onSaveContent}
				/>
			</div>
		);
	},
	actions: [
		{
			icon: <ClockRewind size={18} />,
			description: "View changes",
			onClick: ({ handleVersionChange }) => {
				handleVersionChange("toggle");
			},
			isDisabled: ({ currentVersionIndex, setMetadata }) => {
				if (currentVersionIndex === 0) {
					return true;
				}

				return false;
			},
		},
		{
			icon: <UndoIcon size={18} />,
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
			icon: <RedoIcon size={18} />,
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
			icon: <CopyIcon size={18} />,
			description: "Copy to clipboard",
			onClick: ({ content }) => {
				navigator.clipboard.writeText(content);
				toast.success("Copied to clipboard!");
			},
		},
	],
	toolbar: [
		{
			icon: <PenIcon />,
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
			icon: <MessageIcon />,
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
