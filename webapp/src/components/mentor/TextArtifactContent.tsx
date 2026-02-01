import { ScrollArea } from "@/components/ui/scroll-area";
import { DocumentSkeleton } from "./DocumentSkeleton";
import { TextEditor } from "./TextEditor";

export interface TextArtifactContentProps {
	content: string;
	mode: "edit" | "diff";
	status: "streaming" | "idle";
	isCurrentVersion: boolean;
	onSaveContent: (updatedContent: string, debounce: boolean) => void;
	isLoading?: boolean;
}

export function TextArtifactContent({
	content,
	mode,
	status,
	isCurrentVersion,
	onSaveContent,
	isLoading = false,
}: TextArtifactContentProps) {
	if (isLoading) {
		return (
			<div className="px-4 py-8 md:px-10">
				<DocumentSkeleton artifactKind="text" />
			</div>
		);
	}

	if (mode === "diff") {
		// Diff view can be implemented using a dedicated presentational DiffView later
		return null;
	}

	return (
		<ScrollArea className="h-full">
			{/* biome-ignore lint/a11y/noStaticElementInteractions: Container focuses inner editor for better UX */}
			<div
				className="flex flex-col px-4 py-8 md:px-10 cursor-text"
				style={{ minHeight: "92dvh" }}
				onClick={(e) => {
					const editorElement = e.currentTarget.querySelector(".ProseMirror");
					if (editorElement) (editorElement as HTMLElement).focus();
				}}
				onKeyDown={(e) => {
					if (e.key === "Enter" || e.key === " ") {
						const editorElement = e.currentTarget.querySelector(".ProseMirror");
						if (editorElement && document.activeElement !== editorElement) {
							e.preventDefault();
							(editorElement as HTMLElement).focus();
						}
					}
				}}
				role="presentation"
			>
				<TextEditor
					content={content}
					isCurrentVersion={isCurrentVersion}
					status={status}
					onSaveContent={onSaveContent}
				/>
			</div>
		</ScrollArea>
	);
}
