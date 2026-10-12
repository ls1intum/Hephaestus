import type { ReactNode } from "react";

import type { ReviewedWorkRef } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import { reviewedWorkIcon } from "@/components/icons/reviewed-work-icon";
import {
	RecordDayTable,
	type RecordTableColumn,
	RecordTableSkeleton,
} from "@/components/practice-vocabulary/RecordTable";
import { TableCell } from "@/components/ui/table";
import { hasText } from "@/lib/text";

import { reviewRunLead, reviewRunReach, type ReviewRunSaw } from "./review-run-saw";
import { ReviewOrdinalTag } from "./review-run-tags";

/** A column of a reviews table after Reviewed and Work, which every reviews table has. */
export type ReviewRunColumn = RecordTableColumn;

/** The heads every reviews table starts with: the review's time under `timeLabel`, then the work. */
function leadColumns(timeLabel: string): RecordTableColumn[] {
	return [{ label: timeLabel, className: "w-24 whitespace-nowrap" }, { label: "Work" }];
}

export interface ReviewRunDayTableProps<TRun> {
	"aria-label": string;
	/**
	 * The head over the review's time, in the word its date filter uses: "Requested" where the time
	 * is when the review was asked for, "Reviewed" where it is when it finished.
	 */
	timeLabel?: string;
	/** The heads after Reviewed and Work; each row draws a cell under every one. */
	columns: readonly ReviewRunColumn[];
	/** Newest first, as the wire orders them. */
	runs: readonly TRun[];
	/** The moment a row is listed at, which picks its day. */
	dateOf: (run: TRun) => Date;
	runKey: (run: TRun) => string;
	/** One `PracticeTableRow`: `RecordTimeCell`, `ReviewRunWorkCell`, then a cell per column. */
	renderRow: (run: TRun) => ReactNode;
	/** What the table says when there are no runs, inside a cell across every column. */
	empty: ReactNode;
}

/**
 * Reviews one body per day under its heading, so a row carries the time alone. The developer's
 * own reviews and the admin's list of every review are drawn in it, so one review reads alike on
 * both.
 */
export function ReviewRunDayTable<TRun>({
	timeLabel = "Reviewed",
	columns,
	runs,
	runKey,
	...table
}: ReviewRunDayTableProps<TRun>) {
	return (
		<RecordDayTable
			{...table}
			columns={[...leadColumns(timeLabel), ...columns]}
			rows={runs}
			rowKey={runKey}
		/>
	);
}

export interface ReviewRunDayTableSkeletonProps {
	"aria-label": string;
	timeLabel?: string;
	columns: readonly ReviewRunColumn[];
	rows: number;
}

/** The table's frame and head with placeholder rows, while the first page loads. */
export function ReviewRunDayTableSkeleton({
	timeLabel = "Reviewed",
	columns,
	...skeleton
}: ReviewRunDayTableSkeletonProps) {
	return <RecordTableSkeleton {...skeleton} columns={[...leadColumns(timeLabel), ...columns]} />;
}

export interface ReviewRunWorkCellProps {
	work: ReviewedWorkRef;
	/** The review's place among the reviews of its work, which tags a later one "Review 2". */
	position?: number;
	/** How many reviews the work has, which makes it "Review 2 of 3". */
	total?: number;
	/**
	 * `false` names the work as plain text, for a list whose row opens one thing: the review's
	 * level links the work to its provider.
	 */
	linked?: boolean;
	/** Chips beside the work's label, such as the states a list marks a review with. */
	children?: ReactNode;
}

/** The work by its provider's icon and label, linked to the provider, with its title under it. */
export function ReviewRunWorkCell({
	work,
	position,
	total,
	linked = true,
	children,
}: ReviewRunWorkCellProps) {
	const WorkIcon = reviewedWorkIcon(work.kind, work.provider);
	return (
		<TableCell className="align-top whitespace-normal">
			<span className="flex min-w-0 flex-col items-start gap-1">
				{/* `max-w-full`: in an `items-start` column the row is not stretched, and its fit-content
				    width counts the label whole, so `truncate` would never take effect. */}
				<span className="flex max-w-full min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1">
					{/* The icon and the label on one line that never wraps, so the label truncates beside
					    its icon; the chips wrap on their own. */}
					<span className="flex max-w-full min-w-0 items-center gap-x-1.5">
						<WorkIcon className="size-3.5 shrink-0 text-muted-foreground" />
						{linked ? (
							<InlineLink href={work.url} external className="truncate font-medium">
								{work.label}
							</InlineLink>
						) : (
							<span className="truncate font-medium">{work.label}</span>
						)}
					</span>
					{children}
				</span>
				{hasText(work.title) && (
					<span className="max-w-xs text-xs text-muted-foreground">{work.title}</span>
				)}
				<ReviewOrdinalTag position={position} total={total} />
			</span>
		</TableCell>
	);
}

export interface ReviewRunSawCellProps {
	saw: ReviewRunSaw;
	/** The feedback it left and the tags it wears, on one wrapping line under the reach. */
	children?: ReactNode;
}

/** What the review saw: the practices that slipped by name, then how it decided the rest. */
export function ReviewRunSawCell({ saw, children }: ReviewRunSawCellProps) {
	const reach = reviewRunReach(saw);
	return (
		<TableCell className="align-top whitespace-normal">
			<span className="flex min-w-0 flex-col items-start gap-1.5">
				<span className="text-sm font-semibold">{reviewRunLead(saw)}</span>
				{reach !== "" && <span className="text-xs text-muted-foreground">{reach}</span>}
				<span className="flex flex-wrap items-center gap-1.5">{children}</span>
			</span>
		</TableCell>
	);
}
