import type { UseChatHelpers } from "@ai-sdk/react";
import type { ReactNode } from "react";
import type { ChatMessageVote, Document } from "@/lib/types";
import type { Attachment, ChatMessage } from "@/lib/types";
import type { ArtifactOverlayMeta } from "./ArtifactShell";
import type { PartRendererMap } from "./renderers/types";

export type ArtifactUIModel = {
	isVisible: boolean;
	isMobile?: boolean;
	readonly?: boolean;
	className?: string;
};

export type ArtifactChatModel = {
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
};

export type ArtifactVersionModel = {
	selectedUpdatedAt?: Date;
	isCurrentVersion: boolean;
	currentVersionIndex: number;
	versionNumber?: number;
	canPrev?: boolean;
	canNext?: boolean;
	onPrevVersion?: () => void;
	onNextVersion?: () => void;
	onBackToLatestVersion?: () => void;
	onRestoreSelectedVersion?: () => void;
	isRestoring?: boolean;
	documents?: Document[];
};

export type TextContentModel = {
	content: string;
	mode: "edit" | "diff";
	onSaveContent: (updatedContent: string, debounce: boolean) => void;
	isLoading?: boolean;
};

export type ArtifactShellModel<TContent = unknown> = {
	overlay: ArtifactOverlayMeta;
	ui: ArtifactUIModel;
	chat: ArtifactChatModel;
	version?: ArtifactVersionModel; // optional: not all artifacts support versions
	content: TContent;
	toolbar?: ReactNode; // reserved for per-artifact toolbars in the future
	isSaving?: boolean;
};
