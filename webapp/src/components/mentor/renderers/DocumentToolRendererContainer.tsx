import { toolCallIdToUuid } from "@intelligence-service-utils/tool-call-id";
import type { Document } from "@/api/types.gen";
import { useDocumentArtifact } from "@/hooks/useDocumentArtifact";
import {
	hasDocumentId,
	parseCreateDocumentOutput,
	parseUpdateDocumentInput,
	parseUpdateDocumentOutput,
} from "@/lib/types";
import { DocumentPreview } from "../DocumentPreview";
import { DocumentToolRenderer } from "./DocumentToolRenderer";
import type { PartRenderer } from "./types";

/**
 * Extract document ID from tool part input/output using type-safe parsing.
 * Falls back through multiple sources: input → toolCallId → output
 */
function extractDocumentId(part: {
	type: string;
	input?: unknown;
	output?: unknown;
	toolCallId?: string;
}): string {
	// 1. Try to get ID from input (updateDocument has id in input)
	if (part.type === "tool-updateDocument") {
		const input = parseUpdateDocumentInput(part.input);
		if (input?.id) return input.id;
	}

	// 2. Derive from toolCallId (createDocument uses this pattern)
	if (part.toolCallId) {
		return toolCallIdToUuid(part.toolCallId);
	}

	// 3. Try to get ID from output (both tools return id in output)
	if (part.type === "tool-createDocument") {
		const output = parseCreateDocumentOutput(part.output);
		if (output?.id) return output.id;
	} else if (part.type === "tool-updateDocument") {
		const output = parseUpdateDocumentOutput(part.output);
		if (output?.id) return output.id;
	}

	// 4. Last resort: check if output has id property (partial/streaming output)
	if (hasDocumentId(part.output)) {
		return part.output.id;
	}

	return "";
}

export const DocumentToolRendererContainer: PartRenderer<
	"createDocument" | "updateDocument"
> = ({ message, part, variant }) => {
	const documentId = extractDocumentId(part);

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
		// Build Document object from available data
		const baseDoc = isStreaming ? draft : latest;
		const doc: Document = baseDoc ?? {
			id: documentId,
			title: currentDoc?.title ?? "Document",
			kind: "text",
			content: currentDoc?.content ?? "",
			createdAt: new Date(),
			userId: 0,
			versionNumber: 0,
		};

		// Use the store's isStreaming as the authoritative source
		return (
			<DocumentPreview
				document={doc}
				isLoading={isLoading}
				isStreaming={isStreaming}
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
