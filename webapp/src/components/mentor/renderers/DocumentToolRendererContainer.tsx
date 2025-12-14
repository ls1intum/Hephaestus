import { toolCallIdToUuid } from "@intelligence-service/chat/tool-call-id";
import type { Document } from "@/api/types.gen";
import { useDocumentArtifact } from "@/hooks/useDocumentArtifact";
import type { CreateDocumentOutput, UpdateDocumentOutput } from "@/lib/types";
import { DocumentPreview } from "../DocumentPreview";
import { DocumentToolRenderer } from "./DocumentToolRenderer";
import type { PartRenderer } from "./types";

export const DocumentToolRendererContainer: PartRenderer<
	"createDocument" | "updateDocument"
> = ({ message, part, variant }) => {
	let documentId = "";
	if (part.type === "tool-createDocument") {
		const input = (part.input ?? {}) as {
			document_id?: string;
			id?: string;
		};
		documentId = input.document_id ?? input.id ?? "";
	} else if (part.type === "tool-updateDocument") {
		const input = (part.input ?? {}) as { id?: string };
		documentId = input.id ?? "";
	}

	if (!documentId && part.toolCallId) {
		documentId = toolCallIdToUuid(part.toolCallId);
	}

	if (!documentId && part.output && typeof part.output === "object") {
		const output = part.output as
			| CreateDocumentOutput
			| UpdateDocumentOutput
			| { id?: string };
		documentId = output?.id ?? "";
	}

	const {
		latest,
		draft,
		selectedVersion,
		isLoading,
		isStreaming,
		openOverlay,
	} = useDocumentArtifact({ documentId });

	const currentDoc = isStreaming
		? (draft ?? latest ?? selectedVersion)
		: selectedVersion || latest;
	const hasContent =
		typeof currentDoc?.content === "string" && currentDoc.content.length > 0;

	const isEmbeddedInArtifact = variant === "artifact";
	if (
		!isEmbeddedInArtifact &&
		part.type === "tool-createDocument" &&
		(hasContent || latest || (draft?.content.length ?? 0) > 0)
	) {
		const doc: Document =
			(isStreaming ? (draft as Document | undefined) : latest) ??
			({
				id: documentId,
				title: currentDoc?.title ?? "Document",
				kind: "TEXT",
				content: currentDoc?.content ?? "",
				createdAt: new Date(),
				userId: 0,
				versionNumber: 0,
			} as unknown as Document);

		const streaming = isStreaming || part.state !== "output-available";
		return (
			<DocumentPreview
				document={doc}
				isLoading={isLoading}
				isStreaming={streaming}
				onDocumentClick={(rect) => openOverlay(rect)}
			/>
		);
	}

	return (
		<DocumentToolRenderer
			message={message}
			part={part}
			onDocumentClick={(rect) => openOverlay(rect)}
		/>
	);
};
