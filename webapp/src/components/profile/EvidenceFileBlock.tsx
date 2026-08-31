import { ChevronDownIcon } from "lucide-react";
import { useId, useState } from "react";
import {
	DIFF_SIDE_LABELS,
	evidenceSourceDef,
} from "@/components/practice-vocabulary/evidence-source-defs";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { type EvidenceLocation, evidenceLineRangeLabel, splitPath } from "./evidence";

interface EvidenceFileBlockProps {
	location: EvidenceLocation;
	defaultOpen?: boolean;
}

/**
 * A quoted citation, rendered as what its source actually is.
 *
 * Only a `code` source is located by line: for an `object` source the same numbers are offsets into
 * a serialised context file — a line of `conversation_thread.json`, not a message of the thread — so
 * a gutter and a range there would dress a coordinate up as a place the reader could open. The
 * registry in `evidence-source-defs` owns that ruling and the icon that goes with each source.
 */
export function EvidenceFileBlock({ location, defaultOpen = true }: EvidenceFileBlockProps) {
	const [isOpen, setIsOpen] = useState(defaultOpen);
	const instanceId = useId();
	const source = evidenceSourceDef(location.sourceKind);
	const locatedByLine = source.locator === "code";
	const { directory, fileName } = splitPath(location.path);
	const lines = location.snippet?.split("\n") ?? [];
	const firstLineNumber = location.startLine;
	const hasSnippet = lines.length > 0;
	const bodyId = `evidence-${location.path.replace(/[^\w-]/g, "-")}-${firstLineNumber}-${instanceId}`;
	const SourceIcon = source.icon;

	return (
		<figure className="min-w-0 overflow-hidden rounded-md border">
			<figcaption
				className={cn(
					"flex min-w-0 items-center gap-2 bg-code-header px-2.5 py-1.5",
					isOpen && hasSnippet && "border-b",
				)}
			>
				<SourceIcon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
				<span className="flex min-w-0 flex-1 font-mono text-xs" title={location.path}>
					{locatedByLine && directory && (
						<span className="truncate text-muted-foreground">{directory}</span>
					)}
					<span className={cn("min-w-0 font-medium", locatedByLine ? "shrink-0" : "truncate")}>
						{locatedByLine ? fileName : location.path}
					</span>
				</span>
				{locatedByLine && (
					<span className="shrink-0 font-mono text-xs text-muted-foreground">
						{evidenceLineRangeLabel(location)}
					</span>
				)}
				{locatedByLine && location.side && (
					<Badge variant="outline" className="shrink-0">
						{DIFF_SIDE_LABELS[location.side]}
					</Badge>
				)}
				{location.redacted && (
					<span className="shrink-0 text-xs text-muted-foreground italic">quote withheld</span>
				)}
				{hasSnippet && (
					<button
						type="button"
						aria-expanded={isOpen}
						aria-controls={bodyId}
						aria-label={`${isOpen ? "Hide" : "Show"} the quote from ${locatedByLine ? fileName : source.label}`}
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
					// oxlint-disable-next-line jsx-a11y/no-noninteractive-tabindex -- Keyboard users must be able to scroll this region.
					tabIndex={0}
					className="max-h-72 overflow-auto bg-code py-2 font-mono text-xs leading-relaxed"
				>
					<code>
						{lines.map((line, index) => {
							const lineNumber = firstLineNumber + index;
							return (
								<span
									key={`${bodyId}-${lineNumber}`}
									className={cn("grid", locatedByLine ? "grid-cols-[auto_1fr]" : "grid-cols-1")}
								>
									{locatedByLine && (
										<span className="sticky left-0 select-none bg-inherit pe-3 ps-2.5 text-end tabular-nums text-muted-foreground">
											{lineNumber}
										</span>
									)}
									<span className={cn("pe-2.5", !locatedByLine && "ps-2.5")}>{line || " "}</span>
								</span>
							);
						})}
					</code>
				</pre>
			)}
		</figure>
	);
}
