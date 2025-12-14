import { FileText, Maximize } from "lucide-react";
import type { MouseEvent } from "react";
import type { Document } from "@/lib/types";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { InlineDocumentSkeleton } from "./DocumentSkeleton";
import { TextEditor } from "./TextEditor";

interface DocumentPreviewProps {
	/** The document to preview (null for loading state) */
	document: Document | null;
	/** Whether the document is currently being loaded */
	isLoading?: boolean;
	/** Whether the document is currently streaming content */
	isStreaming?: boolean;
	/** Handler for when the document preview is clicked */
	onDocumentClick?: (boundingBox: DOMRect) => void;
}

export function DocumentPreview({
	document,
	isLoading = false,
	isStreaming = false,
	onDocumentClick,
}: DocumentPreviewProps) {
	const handleClick = (event: MouseEvent<HTMLElement>) => {
		if (!document || !onDocumentClick) return;

		const boundingBox = event.currentTarget.getBoundingClientRect();
		onDocumentClick(boundingBox);
	};

	if (isLoading || !document) {
		return <LoadingSkeleton />;
	}

	return (
		<div className="relative w-full cursor-pointer">
			<HitboxLayer onClick={handleClick} />
			<DocumentHeader title={document.title} isStreaming={isStreaming} />
			<DocumentContent document={document} isStreaming={isStreaming} />
		</div>
	);
}

const LoadingSkeleton = () => (
	<div className="w-full">
		<div className="p-4 border rounded-t-2xl flex flex-row gap-2 items-center justify-between dark:bg-muted h-[57px] dark:border-zinc-700 border-b-0">
			<div className="flex flex-row items-center gap-3">
				<div className="text-muted-foreground">
					<Spinner size="sm" />
				</div>
				<Skeleton className="h-4 w-32" />
			</div>
			<div className="text-muted-foreground">
				<Maximize />
			</div>
		</div>
		<div className="overflow-y-scroll border rounded-b-2xl p-8 pt-4 bg-muted border-t-0 dark:border-zinc-700">
			<InlineDocumentSkeleton />
		</div>
	</div>
);

interface HitboxLayerProps {
	onClick: (event: MouseEvent<HTMLElement>) => void;
}

const HitboxLayer = ({ onClick }: HitboxLayerProps) => (
	<button
		type="button"
		className="size-full absolute top-0 left-0 rounded-xl z-10 bg-transparent border-none cursor-pointer"
		onClick={onClick}
		aria-label="Open document in full view"
	>
		<div className="w-full p-4 flex justify-end items-center">
			<div className="absolute right-[10px] top-[10px] p-2 hover:dark:bg-zinc-700 rounded-md hover:bg-zinc-100">
				<Maximize />
			</div>
		</div>
	</button>
);

interface DocumentHeaderProps {
	title: string;
	isStreaming: boolean;
}

const DocumentHeader = ({ title, isStreaming }: DocumentHeaderProps) => (
	<div className="p-4 border rounded-t-2xl flex flex-row gap-2 items-start sm:items-center justify-between dark:bg-muted border-b-0 dark:border-zinc-700">
		<div className="flex flex-row items-start sm:items-center gap-3">
			<div className="text-muted-foreground">
				{isStreaming ? <Spinner size="sm" /> : <FileText />}
			</div>
			<div className="-translate-y-1 sm:translate-y-0 font-medium">{title}</div>
		</div>
		<div className="w-8" />
	</div>
);

interface DocumentContentProps {
	document: Document;
	isStreaming: boolean;
}

const DocumentContent = ({ document, isStreaming }: DocumentContentProps) => {
	const containerClassName = cn(
		"h-[257px] overflow-y-scroll border rounded-b-2xl dark:bg-muted border-t-0 dark:border-zinc-700",
		{
			"p-4 sm:px-14 sm:py-8": document.kind === "text",
		},
	);

	return (
		<div className={containerClassName}>
			{document.kind === "text" && (
				<TextEditor
					content={document.content ?? ""}
					onSaveContent={() => {}}
					status={isStreaming ? "streaming" : "idle"}
					isCurrentVersion={true}
					currentVersionIndex={0}
				/>
			)}
		</div>
	);
};
