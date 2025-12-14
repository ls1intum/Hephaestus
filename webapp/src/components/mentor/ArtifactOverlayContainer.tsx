import type { UseChatHelpers } from "@ai-sdk/react";
import type { ReactElement } from "react";
import { useEffect } from "react";
import { createPortal } from "react-dom";
import { useWindowSize } from "usehooks-ts";
import type { ChatMessageVote } from "@/lib/types";
import type { ArtifactKind, Attachment, ChatMessage } from "@/lib/types";
import { parseArtifactId } from "@/lib/types";
import { useArtifactStore } from "@/stores/artifact-store";
import type { PartRendererMap } from "./renderers/types";
import { TextArtifactContainer } from "./TextArtifactContainer";

type Props = {
	messages: ChatMessage[];
	votes?: ChatMessageVote[];
	status: UseChatHelpers<ChatMessage>["status"];
	attachments: Attachment[];
	readonly?: boolean;
	onMessageSubmit: (data: { text: string; attachments: Attachment[] }) => void;
	onStop: () => void;
	onFileUpload: (files: File[]) => Promise<Attachment[]>;
	onMessageEdit?: (messageId: string, content: string) => void;
	onCopy?: (content: string) => void;
	onVote?: (messageId: string, isUpvote: boolean) => void;
	partRenderers?: PartRendererMap;
};

export function ArtifactOverlayContainer({
	messages,
	votes,
	status,
	attachments,
	readonly = false,
	onMessageSubmit,
	onStop,
	onFileUpload,
	onMessageEdit,
	onCopy,
	onVote,
	partRenderers,
}: Props) {
	const visibleArtifact = useArtifactStore((s) => s.getVisibleArtifact());
	const closeArtifact = useArtifactStore((s) => s.closeArtifact);
	const clearVisible = useArtifactStore((s) => s.clearVisibleArtifact);
	const removeArtifact = useArtifactStore((s) => s.removeArtifact);

	// Parse artifact id into kind and payload (e.g., "text:docId")
	const { kind, payload } = parseArtifactId(visibleArtifact?.artifactId);

	const { width } = useWindowSize();
	const isMobile = (width ?? 0) < 768;

	// Common props for all artifact containers (chat sidebar + shell wiring)
	const commonProps = {
		isVisible: visibleArtifact?.isVisible ?? false,
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
		onClose: closeArtifact,
	} as const;

	// After closing (isVisible=false), wait for exit animation then unmount/cleanup
	useEffect(() => {
		if (!visibleArtifact) return;
		if (visibleArtifact.isVisible) return;
		const id = visibleArtifact.artifactId;
		const t = setTimeout(() => {
			clearVisible();
			removeArtifact(id);
		}, 450);
		return () => clearTimeout(t);
	}, [visibleArtifact, clearVisible, removeArtifact]);

	if (!visibleArtifact || !kind || !payload) return null;

	// Container registry: map artifact kind to a renderer function
	const containerRegistry: Partial<
		Record<ArtifactKind, (args: { payload: string }) => ReactElement | null>
	> = {
		text: ({ payload }) => (
			<TextArtifactContainer
				overlay={{
					title: visibleArtifact.title,
					documentId: payload,
					status: visibleArtifact.status,
					boundingBox: visibleArtifact.boundingBox,
				}}
				{...commonProps}
			/>
		),
	};

	const element = containerRegistry[kind]?.({ payload }) ?? null;
	if (!element) return null;

	return createPortal(element, document.body);
}
