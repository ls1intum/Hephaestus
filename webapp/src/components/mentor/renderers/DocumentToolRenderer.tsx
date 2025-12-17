import { AlertCircle, XCircle } from "lucide-react";
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

/**
 * Renders loading state (skeleton) for document tools.
 */
const renderLoadingState = (
	part: DocumentToolRendererProps["part"],
	onDocumentClick?: (rect: DOMRect) => void,
) => {
	if (part.type === "tool-createDocument") {
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
};

/**
 * Renders completed state for document tools.
 */
const renderOutputState = (
	part: DocumentToolRendererProps["part"],
	onDocumentClick?: (rect: DOMRect) => void,
) => {
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
	return null;
};

export const DocumentToolRenderer = ({
	part,
	onDocumentClick,
}: DocumentToolRendererProps) => {
	const state = part.state;

	// Exhaustive switch to handle all AI SDK v6 tool states
	switch (state) {
		case "input-streaming":
		case "input-available":
			// Show loading skeleton while input is streaming or available
			return renderLoadingState(part, onDocumentClick);

		case "approval-requested":
		case "approval-responded":
			// Approval states: show loading state (could add approval UI here)
			return renderLoadingState(part, onDocumentClick);

		case "output-available":
			// Tool completed successfully
			return renderOutputState(part, onDocumentClick);

		case "output-error":
			// Tool execution failed
			return (
				<div className="flex items-center gap-2 p-3 rounded-lg border border-destructive/50 bg-destructive/10 text-destructive text-sm">
					<AlertCircle className="size-4 shrink-0" />
					<span>
						Failed to{" "}
						{part.type === "tool-createDocument" ? "create" : "update"} document
						{part.errorText ? `: ${part.errorText}` : ""}
					</span>
				</div>
			);

		case "output-denied":
			// Tool execution was denied by user
			return (
				<div className="flex items-center gap-2 p-3 rounded-lg border border-muted-foreground/30 bg-muted text-muted-foreground text-sm">
					<XCircle className="size-4 shrink-0" />
					<span>
						Document{" "}
						{part.type === "tool-createDocument" ? "creation" : "update"} was
						denied
					</span>
				</div>
			);

		default: {
			// Exhaustive check - TypeScript will error if we miss a state
			state satisfies never;
			return null;
		}
	}
};
