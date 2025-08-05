import type { Document } from "@/api/types.gen";
import { cn } from "@/lib/utils";
import { type MouseEvent, memo, useCallback } from "react";
import { InlineDocumentSkeleton } from "./DocumentSkeleton";
import { FileIcon, FullscreenIcon, LoaderIcon } from "./Icons";
import { TextEditor } from "./TextEditor";

interface DocumentPreviewProps {
	/** The document to preview (null for loading state) */
	document: Document | null;
	/** Whether the document is currently being loaded */
	isLoading?: boolean;
	/** Whether the document is currently streaming content */
	isStreaming?: boolean;
	/** Handler for when the document preview is clicked */
	onDocumentClick?: (document: Document, boundingBox: DOMRect) => void;
	/** Handler for saving document content changes */
	onSaveContent?: (updatedContent: string, debounce: boolean) => void;
}

function PureDocumentPreview({
	document,
	isLoading = false,
	isStreaming = false,
	onDocumentClick,
	onSaveContent,
}: DocumentPreviewProps) {
	const handleClick = useCallback(
		(event: MouseEvent<HTMLElement>) => {
			if (!document || !onDocumentClick) return;

			const boundingBox = event.currentTarget.getBoundingClientRect();
			onDocumentClick(document, boundingBox);
		},
		[document, onDocumentClick],
	);

	if (isLoading || !document) {
		return <LoadingSkeleton />;
	}

	return (
		<div className="relative w-full cursor-pointer">
			<HitboxLayer onClick={handleClick} />
			<DocumentHeader title={document.title} isStreaming={isStreaming} />
			<DocumentContent
				document={document}
				isStreaming={isStreaming}
				onSaveContent={onSaveContent}
			/>
		</div>
	);
}

export const DocumentPreview = memo(
	PureDocumentPreview,
	(prevProps, nextProps) => {
		return (
			prevProps.document?.id === nextProps.document?.id &&
			prevProps.document?.content === nextProps.document?.content &&
			prevProps.isLoading === nextProps.isLoading &&
			prevProps.isStreaming === nextProps.isStreaming
		);
	},
);

const LoadingSkeleton = () => (
	<div className="w-full">
		<div className="p-4 border rounded-t-2xl flex flex-row gap-2 items-center justify-between dark:bg-muted h-[57px] dark:border-zinc-700 border-b-0">
			<div className="flex flex-row items-center gap-3">
				<div className="text-muted-foreground">
					<LoaderIcon />
				</div>
				<div className="h-4 w-32 bg-muted-foreground/20 rounded animate-pulse" />
			</div>
			<div className="text-muted-foreground">
				<FullscreenIcon />
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

const PureHitboxLayer = ({ onClick }: HitboxLayerProps) => (
	<button
		type="button"
		className="size-full absolute top-0 left-0 rounded-xl z-10 bg-transparent border-none cursor-pointer"
		onClick={onClick}
		aria-label="Open document in full view"
	>
		<div className="w-full p-4 flex justify-end items-center">
			<div className="absolute right-[9px] top-[13px] p-2 hover:dark:bg-zinc-700 rounded-md hover:bg-zinc-100">
				<FullscreenIcon />
			</div>
		</div>
	</button>
);

const HitboxLayer = memo(PureHitboxLayer);

interface DocumentHeaderProps {
	title: string;
	isStreaming: boolean;
}

const PureDocumentHeader = ({ title, isStreaming }: DocumentHeaderProps) => (
	<div className="p-4 border rounded-t-2xl flex flex-row gap-2 items-start sm:items-center justify-between dark:bg-muted border-b-0 dark:border-zinc-700">
		<div className="flex flex-row items-start sm:items-center gap-3">
			<div className="text-muted-foreground">
				{isStreaming ? (
					<div className="animate-spin">
						<LoaderIcon />
					</div>
				) : (
					<FileIcon />
				)}
			</div>
			<div className="-translate-y-1 sm:translate-y-0 font-medium">{title}</div>
		</div>
		<div className="w-8" />
	</div>
);

const DocumentHeader = memo(PureDocumentHeader, (prevProps, nextProps) => {
	return (
		prevProps.title === nextProps.title &&
		prevProps.isStreaming === nextProps.isStreaming
	);
});

interface DocumentContentProps {
	document: Document;
	isStreaming: boolean;
	onSaveContent?: (updatedContent: string, debounce: boolean) => void;
}

const DocumentContent = ({
	document,
	isStreaming,
	onSaveContent,
}: DocumentContentProps) => {
	const containerClassName = cn(
		"h-[257px] overflow-y-scroll border rounded-b-2xl dark:bg-muted border-t-0 dark:border-zinc-700",
		{
			"p-4 sm:px-14 sm:py-8": document.kind === "TEXT",
		},
	);

	const handleSaveContent = useCallback(
		(updatedContent: string, debounce: boolean) => {
			onSaveContent?.(updatedContent, debounce);
		},
		[onSaveContent],
	);

	return (
		<div className={containerClassName}>
			{document.kind === "TEXT" && (
				<TextEditor
					content={document.content ?? ""}
					onSaveContent={handleSaveContent}
					status={isStreaming ? "streaming" : "idle"}
					isCurrentVersion={true}
					currentVersionIndex={0}
				/>
			)}
		</div>
	);
};
