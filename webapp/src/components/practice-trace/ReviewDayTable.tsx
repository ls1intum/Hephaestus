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

export type ReviewColumn = RecordTableColumn;

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
	columns: readonly ReviewColumn[];
	reviews: readonly TReview[];
	dateOf: (review: TReview) => Date;
	reviewKey: (review: TReview) => string;
	renderRow: (review: TReview) => ReactNode;
	empty: ReactNode;
}

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

export function ReviewDayTableSkeleton({
	timeLabel = "Reviewed",
	columns,
	...skeleton
}: ReviewDayTableSkeletonProps) {
	return <RecordTableSkeleton {...skeleton} columns={[...leadColumns(timeLabel), ...columns]} />;
}

export interface ReviewItemCellProps {
	item: ItemRef;
	position?: number;
	total?: number;
	/**
	 * `false` names the item as plain text, for a list whose row opens one thing: the review's
	 * level links the item to its provider.
	 */
	linked?: boolean;
	children?: ReactNode;
}

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
	children?: ReactNode;
}

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
