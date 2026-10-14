import type { ReactNode } from "react";

import { cn } from "cn";

/** The two kinds of table a stage of the trace holds: the review's results, its feedback. */
export type TraceTableKind = "results" | "feedback";

/**
 * The columns of each table, with and without its time column. Written out in full, so the
 * stylesheet keeps every one of them. Below the container's `lg` width a row is no grid of columns
 * but a stack: its number on the left, its main cell on the first line, the rest wrapping under it.
 */
const COLUMNS: Record<TraceTableKind, { times: string; plain: string }> = {
	results: {
		times: "@lg:grid-cols-[1.375rem_minmax(0,1fr)_6.5rem_6.5rem_4.5rem_8.5rem]",
		plain: "@lg:grid-cols-[1.375rem_minmax(0,1fr)_6.5rem_6.5rem_4.5rem]",
	},
	feedback: {
		times: "@lg:grid-cols-[1.375rem_10.5rem_minmax(0,1fr)_6rem_9.5rem]",
		plain: "@lg:grid-cols-[1.375rem_10.5rem_minmax(0,1fr)_6rem]",
	},
};

const HEADERS: Record<TraceTableKind, readonly string[]> = {
	results: ["#", "Practice", "Outcome", "Severity", "Feedback", "Time"],
	feedback: ["#", "Destination", "State", "From", "Time"],
};

/** The grid of one row, and of the header above the rows, so the columns line up. */
export function traceRowGrid(kind: TraceTableKind, times: boolean): string {
	return cn(
		"grid grid-cols-[1.375rem_minmax(0,1fr)] items-start gap-x-2 gap-y-0.5",
		times ? COLUMNS[kind].times : COLUMNS[kind].plain,
	);
}

/**
 * The cells after the number: one wrapping line under the main cell while the table is a stack, a
 * column each once it is wide enough.
 */
export const TRACE_ROW_CELLS = "flex min-w-0 flex-wrap items-center gap-x-2 gap-y-0.5 @lg:contents";

/** The main cell of a row: a line of its own in the stack, its own column in the table. */
export const TRACE_ROW_MAIN = "-order-1 basis-full min-w-0 @lg:order-none @lg:basis-auto";

/** A cell with a label of its own in the stack, which the column's header says in the table. */
export function CellLabel({ children }: { children: ReactNode }) {
	return <span className="text-muted-foreground @lg:sr-only">{children} </span>;
}

/**
 * Several records of one stage as an aligned table inside the rail: a header row naming the columns
 * once, then one row per record, each the record's own control. The rail, its dot and the stage
 * stay; the table only lines the records up. On a narrow sheet the rows stack.
 */
export function TraceTable({
	kind,
	times,
	label,
	children,
}: {
	kind: TraceTableKind;
	times: boolean;
	label: string;
	children: ReactNode;
}) {
	const headers = times ? HEADERS[kind] : HEADERS[kind].slice(0, -1);
	return (
		<div data-trace-table={kind} className="@container mt-1.5 min-w-0">
			<div className="overflow-hidden rounded-md border bg-card">
				<div
					aria-hidden
					className={cn(
						traceRowGrid(kind, times),
						"hidden border-b bg-muted/50 px-1.5 py-1 text-2xs font-medium text-muted-foreground @lg:grid",
					)}
				>
					{headers.map((header, index) => (
						<span
							key={header}
							className={cn(index === headers.length - 1 && times && "text-right")}
						>
							{header}
						</span>
					))}
				</div>
				<ul aria-label={label} className="divide-y">
					{children}
				</ul>
			</div>
		</div>
	);
}
