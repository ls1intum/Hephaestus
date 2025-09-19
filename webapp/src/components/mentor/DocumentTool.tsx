import { FileText, MessageCircle, PenLine } from "lucide-react";
import type { ArtifactKind } from "@/lib/types";
import { LoaderIcon } from "./LoaderIcon";

const getActionText = (
	type: "create" | "update" | "request-suggestions",
	tense: "present" | "past",
) => {
	switch (type) {
		case "create":
			return tense === "present" ? "Creating" : "Created";
		case "update":
			return tense === "present" ? "Updating" : "Updated";
		case "request-suggestions":
			return tense === "present"
				? "Adding suggestions"
				: "Added suggestions to";
		default:
			return null;
	}
};

interface DocumentToolProps {
	/** Type of document operation */
	type: "create" | "update" | "request-suggestions";
	/** Whether the operation is currently in progress (shows loading state) */
	isLoading?: boolean;
	/** Document data for completed operations */
	result?: { id: string; title: string; kind: ArtifactKind };
	/** Arguments for in-progress operations */
	args?:
		| { title: string; kind: ArtifactKind } // for create
		| { id: string; description: string } // for update
		| { documentId: string }; // for request-suggestions
	/** Handler for document clicks - receives document ID and rect */
	onDocumentClick?: (boundingBox: DOMRect) => void;
}

export function DocumentTool({
	type,
	isLoading = false,
	result,
	args,
	onDocumentClick,
}: DocumentToolProps) {
	const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
		const rect = event.currentTarget.getBoundingClientRect();

		if (result) {
			// Just pass the document ID and let parent handle the rest
			onDocumentClick?.(rect as DOMRect);
		}
		// For in-progress operations, we don't need to do anything
		// The loading state is already shown via isLoading prop
	};

	// Display text logic
	let displayText: string;
	if (result) {
		// Completed operation
		displayText = `${getActionText(type, "past")} "${result.title}"`;
	} else {
		// In-progress operation
		const actionText = getActionText(type, "present");
		if (type === "create" && args && "title" in args && args.title) {
			displayText = `${actionText} "${args.title}"`;
		} else if (type === "update" && args && "description" in args) {
			displayText = `${actionText} "${args.description}"`;
		} else if (type === "request-suggestions") {
			displayText = `${actionText} for document`;
		} else {
			displayText = actionText || "";
		}
	}

	// Icon logic
	let icon: React.ReactNode;
	if (type === "create") {
		icon = <FileText />;
	} else if (type === "update") {
		icon = <PenLine />;
	} else if (type === "request-suggestions") {
		icon = <MessageCircle />;
	}

	return (
		<button
			type="button"
			className="bg-background cursor-pointer border py-2 px-3 rounded-xl w-fit flex flex-row gap-3 items-center"
			onClick={handleClick}
		>
			<div className="text-muted-foreground">{icon}</div>
			<div className="text-left flex-1">{displayText}</div>
			{isLoading && (
				<div className="animate-spin mt-1">
					<LoaderIcon />
				</div>
			)}
		</button>
	);
}
