import { ChevronRightIcon } from "lucide-react";
import { Fragment, type ReactNode, useId, useState } from "react";

import type { GroupRef, ResultList } from "@/api/types.gen";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { ResultCount } from "@/components/common/ResultCount";
import { SelectFilter } from "@/components/common/SelectFilter";
import { StatusBadge } from "@/components/common/StatusBadge";
import {
	getGroupVisual,
	UNASSIGNED_GROUP_VISUAL,
} from "@/components/practice-vocabulary/group-visuals";
import { GroupName } from "@/components/practice-vocabulary/GroupName";
import {
	type Outcome,
	OUTCOME_DEFS,
	resultVerdict,
} from "@/components/practice-vocabulary/outcome-defs";
import { PracticePill } from "@/components/practice-vocabulary/PracticePill";
import { RESULT_MARK_DEFS } from "@/components/practice-vocabulary/result-mark-defs";
import { StatusLabel, StatusTooltip } from "@/components/practice-vocabulary/StatusTooltip";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { hasText } from "@/lib/text";

import { REVIEW_FILTER_MAX_LENGTH } from "./trace-format";

export interface ReviewPracticeFilters {
	group?: string;
	practice?: string;
}

export interface ReviewPracticeTableProps {
	results: ResultList[];
	reviewId: string;
	groupOf: (practiceSlug: string) => GroupRef | undefined;
	filters: ReviewPracticeFilters;
	onFiltersChange: (filters: ReviewPracticeFilters) => void;
	onOpenPractice?: (practiceSlug: string) => void;
	emptyMessage: string;
	/** How a result reused from an earlier review links to it; the caller owns where reviews open. */
	earlierReviewLink?: (reviewId: string) => ReactNode;
	/**
	 * One result in full, with its problems and evidence. Given, a row opens in place to show it;
	 * the caller reads each one only when its row opens.
	 */
	renderResult?: (resultId: string) => ReactNode;
	/**
	 * The practice the reader came to see: its rows open on arrival, with `renderResult`, and the
	 * first scrolls into view once. Closing it closes it like any other row.
	 */
	focusPracticeSlug?: string;
	/**
	 * Each row's outcome as words, with a badge kept for an outcome that went wrong, and no tooltip
	 * stop: for a level that keeps its focus stops for what the reader acts on.
	 */
	plainState?: boolean;
}

const UNGROUPED = "Other practices";

/**
 * The earlier review whose result `reviewId` reused, or `undefined` when this review recorded the
 * result itself. A reused result keeps the id of the review that recorded it.
 */
export function earlierReviewOf(
	result: Pick<ResultList, "reviewId">,
	reviewId: string,
): string | undefined {
	return result.reviewId === reviewId ? undefined : result.reviewId;
}

export function ReviewPracticeTable({
	results,
	reviewId,
	groupOf,
	filters,
	onFiltersChange,
	onOpenPractice,
	emptyMessage,
	earlierReviewLink,
	renderResult,
	focusPracticeSlug,
	plainState = false,
}: ReviewPracticeTableProps) {
	const [opened, setOpened] = useState<ReadonlySet<string>>(new Set());
	const [focusClosed, setFocusClosed] = useState(false);
	const [focusLanded, setFocusLanded] = useState(false);
	const openOnArrival = (result: ResultList) =>
		result.practiceSlug === focusPracticeSlug && !focusClosed;
	const landOnFocus = (node: HTMLElement | null) => {
		if (node) {
			node.scrollIntoView({ block: "start" });
			setFocusLanded(true);
		}
	};
	const toggle = (result: ResultList) => {
		if (openOnArrival(result) && !opened.has(result.id)) {
			setFocusClosed(true);
			return;
		}
		setOpened((previous) => {
			const next = new Set(previous);
			if (!next.delete(result.id)) {
				next.add(result.id);
			}
			return next;
		});
	};
	const detailIdBase = useId();
	const groupNameOf = (result: ResultList) => groupOf(result.practiceSlug)?.name ?? UNGROUPED;
	const groupNames = [...new Set(results.map(groupNameOf))].sort((left, right) =>
		left.localeCompare(right),
	);

	const needle = filters.practice?.trim().toLowerCase() ?? "";
	const rows = results.filter(
		(result) =>
			(!hasText(filters.group) || groupNameOf(result) === filters.group) &&
			(needle === "" || result.practiceName.toLowerCase().includes(needle)),
	);
	const hasFilter = hasText(filters.group) || needle !== "";
	const practiceFilterId = useId();
	const focusRow = rows.find((result) => result.practiceSlug === focusPracticeSlug);

	return (
		<div className="flex min-w-0 flex-col gap-3">
			<FilterToolbar hasFilter={hasFilter} onReset={() => onFiltersChange({})}>
				<SelectFilter
					label="Group"
					allLabel="All groups"
					options={groupNames.map((group) => ({ value: group, label: group }))}
					value={filters.group}
					onChange={(group) => onFiltersChange({ ...filters, group })}
				/>
				<div className="flex min-w-0 items-center gap-2">
					<Label htmlFor={practiceFilterId} className="shrink-0 text-muted-foreground">
						Practice
					</Label>
					<Input
						id={practiceFilterId}
						value={filters.practice ?? ""}
						maxLength={REVIEW_FILTER_MAX_LENGTH}
						placeholder="Filter by name"
						className="w-56 max-w-full"
						onChange={(event) =>
							onFiltersChange({
								...filters,
								practice: event.target.value === "" ? undefined : event.target.value,
							})
						}
					/>
				</div>
			</FilterToolbar>
			{hasFilter && rows.length !== results.length && (
				<ResultCount total={rows.length} noun={["result", "results"]} hasFilter />
			)}
			<PracticesFrame>
				{rows.length === 0 && (
					<TableRow variant="static">
						<TableCell colSpan={3} className="p-4 whitespace-normal">
							<p className="text-sm text-muted-foreground">
								{results.length > 0 ? "No results match your filters." : emptyMessage}
							</p>
						</TableCell>
					</TableRow>
				)}
				{rows.map((result) => {
					const group = groupOf(result.practiceSlug);
					const visual =
						group === undefined ? UNASSIGNED_GROUP_VISUAL : getGroupVisual(group.icon, group.color);
					const expandable = renderResult !== undefined;
					const open = expandable && (opened.has(result.id) || openOnArrival(result));
					const detailId = `${detailIdBase}-${result.id}`;
					const lands = expandable && !focusLanded && result === focusRow;
					const earlierReview = earlierReviewOf(result, reviewId);
					return (
						<Fragment key={result.id}>
							<TableRow ref={lands ? landOnFocus : undefined}>
								<TableCell className="align-top whitespace-normal">
									<ResultOutcome outcome={result.outcome} plain={plainState} />
								</TableCell>
								<TableCell className="align-top whitespace-normal">
									<span className="flex min-w-0 flex-col items-start gap-1">
										<PracticePill
											name={result.practiceName}
											onOpen={
												onOpenPractice === undefined
													? undefined
													: () => onOpenPractice(result.practiceSlug)
											}
										/>
										<GroupName
											name={groupNameOf(result)}
											icon={visual.Icon}
											pill={group === undefined ? undefined : visual.pill}
											className="text-xs"
										/>
									</span>
								</TableCell>
								<TableCell className="align-top whitespace-normal">
									<span className="flex max-w-lg min-w-0 flex-col items-start gap-1 text-sm text-pretty">
										{hasText(result.summary) && (
											<span className="font-medium">{result.summary.trim()}</span>
										)}
										<span className="text-muted-foreground">
											{resultVerdict(result.outcome, result.problems.length)}
										</span>
										{result.markedIncorrectAt !== null && (
											<StatusBadge def={RESULT_MARK_DEFS.INCORRECT} />
										)}
										{earlierReview !== undefined && earlierReviewLink?.(earlierReview)}
									</span>
									{expandable && (
										<Button
											type="button"
											variant="ghost"
											size="sm"
											className="group mt-1 -ml-2"
											aria-expanded={open}
											aria-controls={open ? detailId : undefined}
											onClick={() => toggle(result)}
										>
											<ChevronRightIcon
												aria-hidden
												className="transition-transform group-aria-expanded:rotate-90 motion-reduce:transition-none"
											/>
											{open ? "Hide evidence" : "Show evidence"}
											<span className="sr-only"> for {result.practiceName}</span>
										</Button>
									)}
								</TableCell>
							</TableRow>
							{open && (
								<TableRow variant="static" id={detailId}>
									<TableCell colSpan={3} className="bg-muted/30 p-4 whitespace-normal">
										{renderResult(result.id)}
									</TableCell>
								</TableRow>
							)}
						</Fragment>
					);
				})}
			</PracticesFrame>
		</div>
	);
}

function ResultOutcome({ outcome, plain }: { outcome: Outcome; plain: boolean }) {
	const def = OUTCOME_DEFS[outcome];
	if (plain) {
		return def.badgeVariant === "destructive" || def.badgeVariant === "warning" ? (
			<StatusBadge def={def} />
		) : (
			<StatusLabel def={def} />
		);
	}
	return (
		<StatusTooltip
			def={def}
			render={<button type="button" aria-label={def.label} />}
			className="cursor-help"
		>
			<StatusBadge def={def} />
		</StatusTooltip>
	);
}

const TABLE_LABEL = "Every practice in this review";

function PracticesFrame({ busy = false, children }: { busy?: boolean; children: ReactNode }) {
	return (
		<div className="shrink-0 overflow-hidden rounded-xl border bg-background">
			<Table aria-label={TABLE_LABEL} aria-busy={busy || undefined} className="min-w-152">
				<TableHeader>
					<TableRow>
						<TableHead className="w-40">Outcome</TableHead>
						<TableHead className="w-64">Practice</TableHead>
						<TableHead>Result</TableHead>
					</TableRow>
				</TableHeader>
				<TableBody>{children}</TableBody>
			</Table>
		</div>
	);
}

export function ReviewPracticeTableSkeleton({ rows }: { rows: number }) {
	return (
		<PracticesFrame busy>
			{Array.from({ length: rows }, (_, index) => (
				<TableRow key={index} variant="static" aria-hidden>
					<TableCell className="align-top">
						<Skeleton className="h-5 w-20" />
					</TableCell>
					<TableCell className="align-top">
						<Skeleton className="h-5 w-40" />
					</TableCell>
					<TableCell className="align-top">
						<Skeleton className="h-4 w-64" />
					</TableCell>
				</TableRow>
			))}
		</PracticesFrame>
	);
}
