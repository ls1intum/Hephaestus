import type {
	CreateDocumentInput,
	Document,
	UpdateDocumentInput,
} from "@/api/types.gen";
import { useDocumentArtifact } from "@/hooks/useDocumentArtifact";
import { DocumentPreview } from "../DocumentPreview";
import { DocumentToolRenderer } from "./DocumentToolRenderer";
import type { PartRenderer } from "./types";

export const DocumentToolRendererContainer: PartRenderer<
	"createDocument" | "updateDocument"
> = ({ message, part, variant }) => {
	let documentId = "";
	if (part.type === "tool-createDocument") {
		const input = (part.input ?? {}) as CreateDocumentInput & {
			document_id: string;
		};
		documentId = input.document_id;
	} else if (part.type === "tool-updateDocument") {
		const input = (part.input ?? {}) as UpdateDocumentInput;
		documentId = input.id;
	}

	const { latest, draft, selectedVersion, isLoading, isStreaming, openOverlay } =
		useDocumentArtifact({ documentId });

	const currentDoc = isStreaming ? draft ?? latest ?? selectedVersion : selectedVersion || latest;
	const hasContent =
		typeof currentDoc?.content === "string" && currentDoc.content.length > 0;

	const isEmbeddedInArtifact = variant === "artifact";
	if (!isEmbeddedInArtifact && part.type === "tool-createDocument" && (hasContent || latest || (draft?.content.length ?? 0) > 0)) {
		const doc: Document =
			(isStreaming ? (draft as Document | undefined) : latest) ??
			({
				id: documentId,
				title: currentDoc?.title ?? "Document",
				kind: "TEXT",
				content: currentDoc?.content ?? "",
				createdAt: new Date(),
				userId: "",
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
