import { ChevronDownIcon, FileCodeIcon } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";
import type { EvidenceLocation } from "./evidence";
import { evidenceLineRangeLabel, splitPath } from "./evidence";

interface EvidenceFileBlockProps {
	location: EvidenceLocation;
	/** Collapsed blocks render the header only; the first block of a finding starts open. */
	defaultOpen?: boolean;
}

/**
 * One quoted file: its path as a header, its lines below.
 *
 * <p>A citation binds its quote to the exact lines it came from, so the header and the body always describe
 * the same place. A quote that was withheld says so instead of rendering an empty block — a reader can tell
 * a redacted quote from a citation that never carried one.
 *
 * <p>The path truncates in the MIDDLE, never at the end: the file name is the identity a reader scans for,
 * so `src/main/java/…/ReadyAndTraceableHandoff.java` stays readable where a trailing ellipsis would cut the
 * name off. Long code lines scroll inside the block; the line gutter stays pinned so a scrolled line keeps
 * its number.
 */
export function EvidenceFileBlock({ location, defaultOpen = true }: EvidenceFileBlockProps) {
	const [isOpen, setIsOpen] = useState(defaultOpen);
	const { directory, fileName } = splitPath(location.path);
	const rangeLabel = evidenceLineRangeLabel(location);
	const lines = location.snippet?.split("\n") ?? [];
	const firstLineNumber = location.startLine;
	const hasSnippet = lines.length > 0;
	const bodyId = `evidence-${location.path.replace(/[^\w-]/g, "-")}-${firstLineNumber}`;

	return (
		<figure className="min-w-0 overflow-hidden rounded-md border">
			<figcaption
				className={cn(
					"flex min-w-0 items-center gap-2 bg-code-header px-2.5 py-1.5",
					isOpen && hasSnippet && "border-b",
				)}
			>
				<FileCodeIcon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
				{/* Two spans, not one truncating span: the directory absorbs the overflow so the file name
				    can never be the part that disappears. */}
				<span className="flex min-w-0 flex-1 font-mono text-xs" title={location.path}>
					{directory && <span className="truncate text-muted-foreground">{directory}</span>}
					<span className="shrink-0 font-medium">{fileName}</span>
				</span>
				<span className="shrink-0 font-mono text-xs text-muted-foreground">{rangeLabel}</span>
				{location.redacted && (
					<span className="shrink-0 text-xs text-muted-foreground italic">quote withheld</span>
				)}
				{hasSnippet && (
					<button
						type="button"
						aria-expanded={isOpen}
						aria-controls={bodyId}
						aria-label={`${isOpen ? "Hide" : "Show"} the quoted lines of ${fileName}`}
						className="-me-1 shrink-0 rounded p-0.5 text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
						onClick={() => setIsOpen((open) => !open)}
					>
						<ChevronDownIcon
							className={cn("size-3.5 transition-transform", isOpen && "rotate-180")}
						/>
					</button>
				)}
			</figcaption>
			{hasSnippet && isOpen && (
				<pre
					id={bodyId}
					className="max-h-72 overflow-auto bg-code py-2 font-mono text-xs leading-relaxed"
				>
					<code>
						{lines.map((line, index) => {
							const lineNumber = firstLineNumber + index;
							return (
								<span
									// Line position IS the identity here; a repeated blank line is not a duplicate row.
									key={`${bodyId}-${lineNumber}`}
									className="grid grid-cols-[auto_1fr]"
								>
									<span className="sticky left-0 select-none bg-inherit pe-3 ps-2.5 text-end tabular-nums text-muted-foreground">
										{lineNumber}
									</span>
									<span className="pe-2.5">{line || " "}</span>
								</span>
							);
						})}
					</code>
				</pre>
			)}
		</figure>
	);
}
