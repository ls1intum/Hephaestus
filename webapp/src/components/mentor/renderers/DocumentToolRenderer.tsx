import {
	parseCreateDocumentInput,
	parseCreateDocumentOutput,
	parseUpdateDocumentInput,
	parseUpdateDocumentOutput,
} from "@/lib/types";
import { DocumentTool } from "../DocumentTool";
import type { PartRendererProps } from "./types";

type DocumentToolRendererProps = PartRendererProps<
	"createDocument" | "updateDocument"
> & {
	onDocumentClick?: (rect: DOMRect) => void;
};

export const DocumentToolRenderer = ({
	part,
	onDocumentClick,
}: DocumentToolRendererProps) => {
	// Handle loading state (input-available)
	if (part.state === "input-available") {
		if (part.type === "tool-createDocument") {
			// Use parser, but allow partial input during streaming
			const input = parseCreateDocumentInput(part.input);
			return (
				<DocumentTool
					type="create"
					isLoading
					args={{ title: input?.title ?? "", kind: "TEXT" }}
					onDocumentClick={onDocumentClick}
				/>
			);
		}
		if (part.type === "tool-updateDocument") {
			const input = parseUpdateDocumentInput(part.input);
			return (
				<DocumentTool
					type="update"
					isLoading
					args={{ id: input?.id ?? "", description: input?.description ?? "" }}
					onDocumentClick={onDocumentClick}
				/>
			);
		}
		return null;
	}

	// Handle completed state (output-available)
	if (part.state === "output-available") {
		if (part.type === "tool-createDocument") {
			const output = parseCreateDocumentOutput(part.output);
			if (!output) return null;
			return (
				<DocumentTool
					type="create"
					result={{ id: output.id, title: output.title, kind: "TEXT" }}
					onDocumentClick={onDocumentClick}
				/>
			);
		}
		if (part.type === "tool-updateDocument") {
			const output = parseUpdateDocumentOutput(part.output);
			if (!output) return null;
			return (
				<DocumentTool
					type="update"
					result={{ id: output.id, title: output.title, kind: "TEXT" }}
					onDocumentClick={onDocumentClick}
				/>
			);
		}
	}

	return null;
};
