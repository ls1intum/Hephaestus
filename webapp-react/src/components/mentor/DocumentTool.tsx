import { memo } from "react";

import { useArtifact } from "@/hooks/use-artifact";
import type { ArtifactKind } from "./Artifact";
import { FileIcon, LoaderIcon, MessageIcon, PencilEditIcon } from "./Icons";

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
}

function PureDocumentTool({
	type,
	isLoading = false,
	result,
	args,
}: DocumentToolProps) {
	const { setArtifact } = useArtifact();

	const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
		const rect = event.currentTarget.getBoundingClientRect();
		const boundingBox = {
			top: rect.top,
			left: rect.left,
			width: rect.width,
			height: rect.height,
		};

		if (result) {
			// Completed operation - set artifact with result data
			setArtifact({
				documentId: result.id,
				kind: result.kind,
				content: "",
				title: result.title,
				isVisible: true,
				status: "idle",
				boundingBox,
			});
		} else {
			// In-progress operation - just show the artifact panel
			setArtifact((currentArtifact) => ({
				...currentArtifact,
				isVisible: true,
				boundingBox,
			}));
		}
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
		icon = <FileIcon />;
	} else if (type === "update") {
		icon = <PencilEditIcon />;
	} else if (type === "request-suggestions") {
		icon = <MessageIcon />;
	}

	return (
		<button
			type="button"
			className="bg-background cursor-pointer border py-2 px-3 rounded-xl w-fit flex flex-row gap-3 items-start"
			onClick={handleClick}
		>
			<div className="text-muted-foreground mt-1">{icon}</div>
			<div className="text-left flex-1">{displayText}</div>
			{isLoading && (
				<div className="animate-spin mt-1">
					<LoaderIcon />
				</div>
			)}
		</button>
	);
}

export const DocumentTool = memo(PureDocumentTool, () => true);
