import type { UseChatHelpers } from "@ai-sdk/react";
import { useDebounceCallback } from "usehooks-ts";
import type { ChatMessageVote } from "@/api/types.gen";
import { useDocumentArtifact } from "@/hooks/useDocumentArtifact";
import type { Attachment, ChatMessage } from "@/lib/types";
import type { ArtifactOverlayMeta } from "./ArtifactShell";
import type { ArtifactShellModel, TextContentModel } from "./artifact-model";
import type { PartRendererMap } from "./renderers/types";
import { TextArtifact } from "./TextArtifact";

export interface TextArtifactContainerProps {
	overlay: ArtifactOverlayMeta & { documentId: string };
	isVisible: boolean;
	isMobile?: boolean;
	readonly?: boolean;
	// chat sidebar
	messages: ChatMessage[];
	votes?: ChatMessageVote[];
	status: UseChatHelpers<ChatMessage>["status"];
	attachments: Attachment[];
	partRenderers?: PartRendererMap;
	onMessageSubmit: (data: { text: string; attachments: Attachment[] }) => void;
	onStop: () => void;
	onFileUpload: (files: File[]) => Promise<Attachment[]>;
	onMessageEdit?: (messageId: string, content: string) => void;
	onCopy?: (content: string) => void;
	onVote?: (messageId: string, isUpvote: boolean) => void;
	onClose: () => void;
}

export function TextArtifactContainer({
	overlay,
	isVisible,
	isMobile,
	readonly,
	messages,
	votes,
	status,
	attachments,
	partRenderers,
	onMessageSubmit,
	onStop,
	onFileUpload,
	onMessageEdit,
	onCopy,
	onVote,
	onClose,
}: TextArtifactContainerProps) {
	const doc = useDocumentArtifact({ documentId: overlay.documentId });

	// Save with debounce - doc.saveContent also has internal debounce as a guard
	const onSaveContent = useDebounceCallback((content: string) => {
		doc.saveContent(content, true);
	}, 600);

	const isCurrentVersion = doc.isCurrentVersion;

	const model: ArtifactShellModel<TextContentModel> = {
		overlay: {
			title: doc.draft?.title ?? overlay.title,
			status: overlay.status,
			boundingBox: overlay.boundingBox,
		},
		isSaving: doc.isSaving,
		ui: {
			isVisible,
			isMobile,
			readonly: readonly || doc.isStreaming || !isCurrentVersion,
			className: undefined,
		},
		chat: {
			messages,
			votes,
			status,
			attachments,
			partRenderers,
			onMessageSubmit,
			onStop,
			onFileUpload,
			onMessageEdit,
			onCopy,
			onVote,
			onClose,
		},
		version: {
			isCurrentVersion,
			currentVersionIndex: doc.selectedIndex,
			// Prefer selected document's timestamp; fall back to draft createdAt for new documents
			selectedUpdatedAt:
				(isCurrentVersion
					? (doc.latest?.createdAt ?? doc.draft?.createdAt)
					: doc.selectedVersion?.createdAt) ?? undefined,
			// For new documents (draft exists but no latest), assume version 1
			versionNumber:
				(isCurrentVersion
					? (doc.latest?.versionNumber ?? (doc.draft ? 1 : undefined))
					: doc.selectedVersion?.versionNumber) ?? undefined,
			canPrev: doc.canPrev,
			canNext: doc.canNext,
			onPrevVersion: doc.onPrevVersion,
			onNextVersion: doc.onNextVersion,
			onBackToLatestVersion: doc.onBackToLatestVersion,
			onRestoreSelectedVersion: doc.onRestoreSelectedVersion,
			isRestoring: false,
		},
		content: {
			// Prefer streaming draft content while streaming; otherwise selected version
			content:
				(doc.isStreaming ? doc.draft?.content : undefined) ?? doc.selectedVersion?.content ?? "",
			mode: "edit",
			onSaveContent: (c, _debounce) => {
				// Block saves while streaming or when viewing history
				if (doc.isStreaming || !isCurrentVersion) return;
				onSaveContent(c);
			},
			isLoading: doc.isLoading,
		},
	};

	return <TextArtifact model={model} />;
}
