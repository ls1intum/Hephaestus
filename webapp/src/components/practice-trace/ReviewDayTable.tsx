import type { ReactNode } from "react";

import type { ItemRef } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import { reviewedWorkIcon } from "@/components/icons/reviewed-work-icon";
import {
	RecordDayTable,
	type RecordTableColumn,
	RecordTableSkeleton,
} from "@/components/practice-vocabulary/RecordTable";
import { TableCell } from "@/components/ui/table";
import { hasText } from "@/lib/text";

import { reviewLead, reviewReach, type ReviewSaw } from "./review-saw";
import { ReviewOrdinalTag } from "./review-tags";

/** A column of a reviews table after Reviewed and Work, which every reviews table has. */
export type ReviewColumn = RecordTableColumn;

/** The heads every reviews table starts with: the review's time under `timeLabel`, then the work. */
function leadColumns(timeLabel: string): RecordTableColumn[] {
	return [{ label: timeLabel, className: "w-24 whitespace-nowrap" }, { label: "Work" }];
}

export interface ReviewDayTableProps<TReview> {
	"aria-label": string;
	/**
	 * The head over the review's time, in the word its date filter uses: "Requested" where the time
	 * is when the review was asked for, "Reviewed" where it is when it finished.
	 */
	timeLabel?: string;
	/** The heads after Reviewed and Work; each row draws a cell under every one. */
	columns: readonly ReviewColumn[];
	/** Newest first, as the wire orders them. */
	reviews: readonly TReview[];
	/** The moment a row is listed at, which picks its day. */
	dateOf: (review: TReview) => Date;
	reviewKey: (review: TReview) => string;
	/** One `PracticeTableRow`: `RecordTimeCell`, `ReviewItemCell`, then a cell per column. */
	renderRow: (review: TReview) => ReactNode;
	/** What the table says when there are no reviews, inside a cell across every column. */
	empty: ReactNode;
}

/**
 * Reviews one body per day under its heading, so a row carries the time alone. The developer's
 * own reviews and the admin's list of every review are drawn in it, so one review reads alike on
 * both.
 */
export function ReviewDayTable<TReview>({
	timeLabel = "Reviewed",
	columns,
	reviews,
	reviewKey,
	...table
}: ReviewDayTableProps<TReview>) {
	return (
		<RecordDayTable
			{...table}
			columns={[...leadColumns(timeLabel), ...columns]}
			rows={reviews}
			rowKey={reviewKey}
		/>
	);
}

export interface ReviewDayTableSkeletonProps {
	"aria-label": string;
	timeLabel?: string;
	columns: readonly ReviewColumn[];
	rows: number;
}

/** The table's frame and head with placeholder rows, while the first page loads. */
export function ReviewDayTableSkeleton({
	timeLabel = "Reviewed",
	columns,
	...skeleton
}: ReviewDayTableSkeletonProps) {
	return <RecordTableSkeleton {...skeleton} columns={[...leadColumns(timeLabel), ...columns]} />;
}

export interface ReviewItemCellProps {
	item: ItemRef;
	/** The review's place among the reviews of its item, which tags a later one "Review 2". */
	position?: number;
	/** How many reviews the item has, which makes it "Review 2 of 3". */
	total?: number;
	/**
	 * `false` names the item as plain text, for a list whose row opens one thing: the review's
	 * level links the item to its provider.
	 */
	linked?: boolean;
	/** Chips beside the item's label, such as the states a list marks a review with. */
	children?: ReactNode;
}

/** The item by its provider's icon and label, linked to the provider, with its title under it. */
export function ReviewItemCell({
	item,
	position,
	total,
	linked = true,
	children,
}: ReviewItemCellProps) {
	const ItemIcon = reviewedWorkIcon(item.kind, item.provider ?? undefined);
	return (
		<TableCell className="align-top whitespace-normal">
			<span className="flex min-w-0 flex-col items-start gap-1">
				{/* `max-w-full`: in an `items-start` column the row is not stretched, and its fit-content
				    width counts the label whole, so `truncate` would never take effect. */}
				<span className="flex max-w-full min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1">
					{/* The icon and the label on one line that never wraps, so the label truncates beside
					    its icon; the chips wrap on their own. */}
					<span className="flex max-w-full min-w-0 items-center gap-x-1.5">
						<ItemIcon className="size-3.5 shrink-0 text-muted-foreground" />
						{linked ? (
							<InlineLink href={item.url ?? undefined} external className="truncate font-medium">
								{item.label}
							</InlineLink>
						) : (
							<span className="truncate font-medium">{item.label}</span>
						)}
					</span>
					{children}
				</span>
				{hasText(item.title) && (
					<span className="max-w-xs text-xs text-muted-foreground">{item.title}</span>
				)}
				<ReviewOrdinalTag position={position} total={total} />
			</span>
		</TableCell>
	);
}

export interface ReviewSawCellProps {
	saw: ReviewSaw;
	/** The feedback it left and the tags it wears, on one wrapping line under the reach. */
	children?: ReactNode;
}

/** What the review saw: the practices Not met by name, then how it decided the rest. */
export function ReviewSawCell({ saw, children }: ReviewSawCellProps) {
	const reach = reviewReach(saw);
	return (
		<TableCell className="align-top whitespace-normal">
			<span className="flex min-w-0 flex-col items-start gap-1.5">
				<span className="text-sm font-semibold">{reviewLead(saw)}</span>
				{reach !== "" && <span className="text-xs text-muted-foreground">{reach}</span>}
				<span className="flex flex-wrap items-center gap-1.5">{children}</span>
			</span>
		</TableCell>
	);
}
