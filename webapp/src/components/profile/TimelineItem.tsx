import type { ReactNode } from "react";

import { cn } from "cn";
import { Skeleton } from "@/components/ui/skeleton";

/** The row: label column, rail, content; below `sm` the label sits over the content. */
const ROW =
	"group grid min-w-0 grid-cols-[1rem_minmax(0,1fr)] gap-x-3 sm:grid-cols-[4.5rem_1rem_minmax(0,1fr)]";
const LABEL =
	"col-start-2 mb-1 flex w-fit gap-1 sm:col-start-1 sm:row-start-1 sm:mt-3 sm:flex-col sm:items-end";
const RAIL = "relative col-start-1 row-start-1 row-end-3 sm:col-start-2";
const DOT =
	"absolute top-3 left-1/2 z-10 size-2.5 -translate-x-1/2 rounded-full border-2 border-background";
const LINE = "absolute top-5 bottom-0 left-1/2 -translate-x-1/2";
const CONTENT = "col-start-2 mb-3 min-w-0 sm:col-start-3 sm:row-start-1";

export interface TimelineItemProps {
	/** When this happened, carried as the `<time>`'s machine-readable value. */
	at: Date;
	/** What the date column shows — an absolute date, a relative phrase, anything. */
	label: ReactNode;
	/**
	 * On the last row only: keep the rail running past the content as a dashed line, saying
	 * "there is more below the fold". Collapsed lists set it; expanded lists end plain.
	 */
	tailContinues?: boolean;
	children: ReactNode;
}

/**
 * One row on a vertical timeline: label column, dot on a connecting rail, then the content.
 * The rail hides its tail on the last item; place rows inside a plain `<ol>`.
 */
export function TimelineItem({ at, label, tailContinues = false, children }: TimelineItemProps) {
	return (
		<li className={ROW}>
			<time
				dateTime={at.toISOString()}
				className={cn(LABEL, "text-xs text-muted-foreground sm:text-right")}
			>
				{label}
			</time>
			<div className={RAIL}>
				<span className={cn(DOT, "bg-muted-foreground")} aria-hidden />
				<span
					className={cn(
						LINE,
						// A dashed rail needs a border — a background strip cannot dash.
						tailContinues
							? "border-l border-dashed border-muted-foreground/50"
							: "w-px bg-border group-last:hidden",
					)}
					aria-hidden
				/>
			</div>
			<div className={CONTENT}>{children}</div>
		</li>
	);
}

/**
 * A row's placeholder while the next page loads, where that page's rows will appear: the day and
 * time, the dot on the rail, and a card with its work and a result.
 */
export function TimelineItemSkeleton() {
	return (
		<li aria-hidden data-loading-row className={ROW}>
			<div className={LABEL}>
				<Skeleton className="h-4 w-12" />
				<Skeleton className="h-3 w-10" />
			</div>
			<div className={RAIL}>
				<span className={cn(DOT, "bg-muted-foreground/40")} />
				<span className={cn(LINE, "w-px bg-border group-last:hidden")} />
			</div>
			<div className={CONTENT}>
				<div className="divide-y overflow-hidden rounded-xl border bg-background">
					<div className="flex flex-col gap-2 px-4 py-3">
						<Skeleton className="h-4 w-2/3" />
						<Skeleton className="h-3 w-1/4" />
					</div>
					<div className="flex items-center gap-2.5 px-4 py-3">
						<Skeleton className="size-4 rounded-full" />
						<Skeleton className="h-4 w-1/2" />
					</div>
				</div>
			</div>
		</li>
	);
}
