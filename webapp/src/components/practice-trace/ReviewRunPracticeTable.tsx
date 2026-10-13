import { Link } from "@tanstack/react-router";
import { ArrowRightIcon, ChevronRightIcon, ScanSearchIcon } from "lucide-react";
import { Fragment, type ReactNode, useId, useState } from "react";

import type {
	PracticeGroup,
	PracticeResultDetail,
	PracticeTraceEntry,
	PracticeScriptRun,
	TracedSignal,
} from "@/api/types.gen";
import { reviewLevel } from "@/components/admin/practice-reviews/review-levels";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { InlineLink } from "@/components/common/InlineLink";
import { ResultCount } from "@/components/common/ResultCount";
import { SelectFilter } from "@/components/common/SelectFilter";
import { StatusBadge } from "@/components/common/StatusBadge";
import { detailSearch } from "@/components/layout/detail-drawer/detail-stack";
import { AutonomyBadge } from "@/components/practice-vocabulary/AutonomyBadge";
import {
	getGroupVisual,
	UNASSIGNED_GROUP_VISUAL,
} from "@/components/practice-vocabulary/group-visuals";
import { GroupName } from "@/components/practice-vocabulary/GroupName";
import { resultVerdict } from "@/components/practice-vocabulary/outcome-defs";
import {
	practiceScriptFix,
	practiceScriptRunSentence,
} from "@/components/practice-vocabulary/practice-script-run-status-defs";
import { PracticePill } from "@/components/practice-vocabulary/PracticePill";
import { MARKED_INCORRECT_DEF } from "@/components/practice-vocabulary/result-invalidation-defs";
import { StatusLabel, StatusTooltip } from "@/components/practice-vocabulary/StatusTooltip";
import {
	TRACE_OUTCOME_DEFS,
	type TraceOutcome,
} from "@/components/practice-vocabulary/trace-outcome-defs";
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

import { PracticeScriptFixLink } from "./PracticeScriptFixLink";
import { deliveryLabel, REVIEW_FILTER_MAX_LENGTH } from "./trace-format";
import { TraceOutcomeBadge } from "./TraceOutcomeBadge";

export interface ReviewRunPracticeFilters {
	/** The practice group's name, as the trace spells it. */
	group?: string;
	/** Part of a practice's name, matched case-insensitively. */
	practice?: string;
	/** One signal name, keeping the practices that watch it. */
	watches?: string;
}

/**
 * The words a row needs from one result. The developer's own read carries the whole result with
 * its problems; an admin's list row carries no correction reason and no problems, so those may be
 * absent.
 */
export type PracticeRowResult = Pick<
	PracticeResultDetail,
	"id" | "summary" | "outcome" | "invalidatedAt"
> &
	Partial<Pick<PracticeResultDetail, "invalidationReason" | "observations">>;

export interface ReviewRunPracticeTableProps {
	/** Where an admin's fix links lead, such as the AI models page for a missing model for practice scripts. */
	workspaceSlug: string;
	/** Each practice's answer on the work: one review's, or the latest across every review of it. */
	entries: PracticeTraceEntry[];
	/** The occurrences this work's activity carries, so "Rests on" can name one. */
	signals: TracedSignal[];
	/**
	 * The review's results about the reader, by practice slug; one practice may have several.
	 * Absent, each row reads the recorded explanation.
	 */
	resultsByPractice?: Record<string, PracticeRowResult[] | undefined>;
	/** For the icon and colour a group's name is drawn in; the entry carries the name itself. */
	groups: PracticeGroup[];
	filters: ReviewRunPracticeFilters;
	onFiltersChange: (filters: ReviewRunPracticeFilters) => void;
	/** Opens a practice's own level from its pill. */
	onOpenPractice?: (practiceSlug: string) => void;
	/** Shows one moment on the timeline, which the caller places. */
	onShowOccurrence: (signalId: string) => void;
	/** Admins also read the delivery sentence, the autonomy and what the answer rests on. */
	canAdminister?: boolean;
	/** Why there is no practice at all to list, which only the caller can say. */
	emptyMessage: string;
	/**
	 * The review the table lists, when it lists one: its rows then speak for that review, and a row
	 * answered by an earlier review says so. Absent, the rows speak for every review of the work.
	 */
	reviewId?: string;
	/** How a row answered by an earlier review links to it; the caller owns where reviews open. */
	earlierReviewLink?: (reviewId: string) => ReactNode;
	/**
	 * How a practice script line's *Details* opens its review, where the table sits in a stack that a
	 * review opens over. The label carries a trailing icon, so the link lays it out inline. Absent,
	 * it opens the review on Practice reviews.
	 */
	reviewLink?: (reviewId: string, label: ReactNode) => ReactNode;
	/**
	 * One result in full, with its problems and evidence. Given, a row with results opens in place to
	 * show them; the caller reads each one only when its row opens.
	 */
	renderResult?: (practiceResultId: string) => ReactNode;
	/**
	 * The practice the reader came to see: its row opens on arrival, with `renderResult`, and
	 * scrolls into view once. Closing it closes it like any other row.
	 */
	focusPracticeSlug?: string;
	/**
	 * Each row's state as words, with a badge kept for a state that went wrong, and no tooltip stop:
	 * for a level that keeps its focus stops for what the reader acts on.
	 */
	plainState?: boolean;
	/**
	 * `false` leaves out each row's "Rests on" link, for a caller that names the moment once above
	 * the table: in one review every row rests on the moment that started it.
	 */
	restsOnPerRow?: boolean;
}

/** Practices the workspace files in no group are still listed, under a name of their own. */
const UNGROUPED = "Other practices";

const NO_RESULTS: PracticeRowResult[] = [];

/**
 * The practices this review decided, in the trace's order. The trace is asked for this review, which
 * still lists the practices no review speaks for — turned off, never occasioned — without its id. A
 * practice it left to an earlier review's answer names that review instead, so it belongs here through
 * the occurrence this review started.
 */
export function runPractices(
	entries: PracticeTraceEntry[],
	signals: TracedSignal[],
	reviewId: string | undefined,
): PracticeTraceEntry[] {
	if (reviewId === undefined) {
		return [];
	}
	const startedHere = new Set(
		signals.filter((signal) => signal.reviewId === reviewId).map((signal) => signal.id),
	);
	return entries.filter(
		(entry) =>
			entry.reviewId === reviewId ||
			(hasText(entry.reviewId) &&
				hasText(entry.occasionedById) &&
				startedHere.has(entry.occasionedById)),
	);
}

/**
 * The earlier review whose answer `reviewId` reused for the entry, or `undefined` when this review
 * assessed the practice itself, did not reach it, or the entry names no review.
 */
export function earlierReviewOf(
	entry: PracticeTraceEntry,
	reviewId: string | undefined,
): string | undefined {
	return reviewId !== undefined &&
		entry.outcome === "REVIEWED" &&
		hasText(entry.reviewId) &&
		entry.reviewId !== reviewId
		? entry.reviewId
		: undefined;
}

function groupNameOf(entry: PracticeTraceEntry): string {
	return hasText(entry.groupName) ? entry.groupName : UNGROUPED;
}

/**
 * Every practice's answer on one piece of work: its outcome, and its results' own words where
 * it said something about the reader, or the recorded reason where it did not.
 */
export function ReviewRunPracticeTable({
	workspaceSlug,
	entries,
	signals,
	resultsByPractice,
	groups,
	filters,
	onFiltersChange,
	onOpenPractice,
	onShowOccurrence,
	canAdminister = false,
	emptyMessage,
	reviewId,
	earlierReviewLink,
	reviewLink,
	renderResult,
	focusPracticeSlug,
	plainState = false,
	restsOnPerRow = true,
}: ReviewRunPracticeTableProps) {
	const [opened, setOpened] = useState<ReadonlySet<string>>(new Set());
	const [focusClosed, setFocusClosed] = useState(false);
	const [focusLanded, setFocusLanded] = useState(false);
	const openOnArrival = (practiceSlug: string) =>
		practiceSlug === focusPracticeSlug && !focusClosed;
	const landOnFocus = (node: HTMLElement | null) => {
		if (node) {
			node.scrollIntoView({ block: "start" });
			setFocusLanded(true);
		}
	};
	const toggle = (practiceSlug: string) => {
		if (openOnArrival(practiceSlug) && !opened.has(practiceSlug)) {
			setFocusClosed(true);
			return;
		}
		setOpened((previous) => {
			const next = new Set(previous);
			if (!next.delete(practiceSlug)) {
				next.add(practiceSlug);
			}
			return next;
		});
	};
	const detailIdBase = useId();
	// From the entries, not `groups`: that holds only the groups the reader has a standing in.
	const groupNames = [...new Set(entries.map(groupNameOf))].sort((left, right) =>
		left.localeCompare(right),
	);
	const groupBySlug = new Map(groups.map((group) => [group.slug, group]));
	// Each entry carries the words for what it watches, so a moment never raised on this work is named too.
	const moments = [
		...new Map(
			entries.flatMap((entry) =>
				entry.watches.map((watched) => [watched.signal, watched.displayName] as const),
			),
		),
	].sort(([, left], [, right]) => left.localeCompare(right));
	// A moment named in the address that no practice here watches — a hand-edited or stale link — is
	// no filter at all, rather than one the select would have to show by its wire name.
	const watches = moments.some(([signal]) => signal === filters.watches)
		? filters.watches
		: undefined;

	const needle = filters.practice?.trim().toLowerCase() ?? "";
	const rows = entries.filter((entry) => {
		if (hasText(filters.group) && groupNameOf(entry) !== filters.group) {
			return false;
		}
		if (hasText(watches) && !entry.watches.some((watched) => watched.signal === watches)) {
			return false;
		}
		return needle === "" || entry.practiceName.toLowerCase().includes(needle);
	});
	const hasFilter = hasText(filters.group) || hasText(watches) || needle !== "";
	const practiceFilterId = useId();

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
				<SelectFilter
					label="Reviews when"
					allLabel="Any moment"
					options={moments.map(([value, label]) => ({ value, label }))}
					value={watches}
					onChange={(next) => onFiltersChange({ ...filters, watches: next })}
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
			{hasFilter && rows.length !== entries.length && (
				<ResultCount total={rows.length} noun={["practice", "practices"]} hasFilter />
			)}
			<PracticesFrame label={tableLabel(reviewId !== undefined)}>
				{rows.length === 0 && (
					<TableRow variant="static">
						<TableCell colSpan={3} className="p-4 whitespace-normal">
							<p className="text-sm text-muted-foreground">
								{entries.length > 0 ? "No practices match your filters." : emptyMessage}
							</p>
						</TableCell>
					</TableRow>
				)}
				{rows.map((entry) => {
					const group = hasText(entry.groupSlug) ? groupBySlug.get(entry.groupSlug) : undefined;
					const visual =
						group === undefined ? UNASSIGNED_GROUP_VISUAL : getGroupVisual(group.icon, group.color);
					const results = resultsByPractice?.[entry.practiceSlug] ?? NO_RESULTS;
					const expandable = renderResult !== undefined && results.length > 0;
					const open =
						expandable && (opened.has(entry.practiceSlug) || openOnArrival(entry.practiceSlug));
					const detailId = `${detailIdBase}-${entry.practiceSlug}`;
					const lands = expandable && !focusLanded && entry.practiceSlug === focusPracticeSlug;
					return (
						<Fragment key={entry.practiceSlug}>
							<TableRow ref={lands ? landOnFocus : undefined}>
								<TableCell className="align-top whitespace-normal">
									<PracticeState outcome={entry.outcome} plain={plainState} />
								</TableCell>
								<TableCell className="align-top whitespace-normal">
									<span className="flex min-w-0 flex-col items-start gap-1">
										<PracticePill
											name={entry.practiceName}
											onOpen={
												onOpenPractice === undefined
													? undefined
													: () => onOpenPractice(entry.practiceSlug)
											}
										/>
										<GroupName
											name={groupNameOf(entry)}
											icon={visual.Icon}
											pill={group === undefined ? undefined : visual.pill}
											className="text-xs"
										/>
									</span>
								</TableCell>
								<TableCell className="align-top whitespace-normal">
									<WhatItSaw
										workspaceSlug={workspaceSlug}
										entry={entry}
										results={results}
										occurrenceName={
											restsOnPerRow && hasText(entry.occasionedById)
												? (signals.find((signal) => signal.id === entry.occasionedById)
														?.displayName ?? entry.occasionedBy?.displayName)
												: undefined
										}
										onShowOccurrence={onShowOccurrence}
										canAdminister={canAdminister}
										earlierReview={earlierReviewOf(entry, reviewId)}
										earlierReviewLink={earlierReviewLink}
										reviewLink={reviewLink}
									/>
									{expandable && (
										<Button
											type="button"
											variant="ghost"
											size="sm"
											className="group mt-1 -ml-2"
											aria-expanded={open}
											aria-controls={open ? detailId : undefined}
											onClick={() => toggle(entry.practiceSlug)}
										>
											<ChevronRightIcon
												aria-hidden
												className="transition-transform group-aria-expanded:rotate-90 motion-reduce:transition-none"
											/>
											{open ? "Hide evidence" : "Show evidence"}
											<span className="sr-only"> for {entry.practiceName}</span>
										</Button>
									)}
								</TableCell>
							</TableRow>
							{open && (
								<TableRow variant="static" id={detailId}>
									<TableCell colSpan={3} className="bg-muted/30 p-4 whitespace-normal">
										<div className="flex min-w-0 flex-col gap-6">
											{results.map((result) => (
												<Fragment key={result.id}>{renderResult(result.id)}</Fragment>
											))}
										</div>
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

/**
 * A row's state. Plain, a state that went wrong is a badge and every other state is words beside
 * its icon; otherwise the badge is a tooltip's trigger, so a keyboard reaches its sentence.
 */
function PracticeState({ outcome, plain }: { outcome: TraceOutcome; plain: boolean }) {
	const def = TRACE_OUTCOME_DEFS[outcome];
	if (plain) {
		return def.badgeVariant === "destructive" || def.badgeVariant === "warning" ? (
			<TraceOutcomeBadge outcome={outcome} />
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
			<TraceOutcomeBadge outcome={outcome} />
		</StatusTooltip>
	);
}

/** The table speaks for one review when it lists one, and for the work otherwise. */
function tableLabel(inReview: boolean): string {
	return inReview ? "Every practice in this review" : "Every practice on this work";
}

function PracticesFrame({
	label,
	busy = false,
	children,
}: {
	label: string;
	busy?: boolean;
	children: ReactNode;
}) {
	return (
		<div className="shrink-0 overflow-hidden rounded-xl border bg-background">
			<Table aria-label={label} aria-busy={busy || undefined} className="min-w-152">
				<TableHeader>
					<TableRow>
						<TableHead className="w-40">State</TableHead>
						<TableHead className="w-64">Practice</TableHead>
						<TableHead>What it saw</TableHead>
					</TableRow>
				</TableHeader>
				<TableBody>{children}</TableBody>
			</Table>
		</div>
	);
}

export function ReviewRunPracticeTableSkeleton({
	rows,
	inReview = false,
}: {
	rows: number;
	/** The table will list one review, so the loading table carries the same name. */
	inReview?: boolean;
}) {
	return (
		<PracticesFrame label={tableLabel(inReview)} busy>
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

interface WhatItSawProps {
	workspaceSlug: string;
	entry: PracticeTraceEntry;
	results: PracticeRowResult[];
	occurrenceName: string | undefined;
	onShowOccurrence: (signalId: string) => void;
	canAdminister: boolean;
	earlierReview: string | undefined;
	earlierReviewLink: ((reviewId: string) => ReactNode) | undefined;
	reviewLink: ((reviewId: string, label: ReactNode) => ReactNode) | undefined;
}

/**
 * One line per result about the reader, or the recorded reason where there is none. The
 * catalog's words on the practice never stand in: they say what the practice is about, not what
 * this review made of this work. The operating facts under it are the admin's.
 */
function WhatItSaw({
	workspaceSlug,
	entry,
	results,
	occurrenceName,
	onShowOccurrence,
	canAdminister,
	earlierReview,
	earlierReviewLink,
	reviewLink,
}: WhatItSawProps) {
	// Narrowed here: the closure below would not keep a narrowing made in the JSX guard.
	const occasionedById = hasText(entry.occasionedById) ? entry.occasionedById : undefined;
	const summaries = results.filter(
		(result) => hasText(result.summary) || result.invalidatedAt !== undefined,
	);
	return (
		<span className="flex min-w-0 flex-col items-start gap-2.5">
			{summaries.length === 0 ? (
				<span className="text-sm text-muted-foreground">{entry.explanation}</span>
			) : (
				summaries.map((result) => (
					<span
						key={result.id}
						className="flex max-w-lg flex-col items-start gap-1 text-sm text-pretty"
					>
						{hasText(result.summary) && (
							<span className="font-medium">{result.summary.trim()}</span>
						)}
						<span className="text-muted-foreground">
							{resultVerdict(result.outcome, result.observations?.length)}
						</span>
						{result.invalidatedAt !== undefined && (
							<>
								<StatusBadge def={MARKED_INCORRECT_DEF} />
								{hasText(result.invalidationReason) && (
									<span className="text-muted-foreground">Reason: {result.invalidationReason}</span>
								)}
							</>
						)}
					</span>
				))
			)}
			{earlierReview !== undefined && earlierReviewLink?.(earlierReview)}
			{canAdminister && (
				<span className="flex min-w-0 flex-col gap-1 text-xs text-muted-foreground">
					<span className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
						<span>
							{/* The results behind a reused answer are the earlier review's; this one made none. */}
							{earlierReview !== undefined &&
							entry.practiceResultCount === 0 &&
							entry.deliveredCount === 0
								? "No new results in this review, so nothing was sent"
								: deliveryLabel(entry)}
						</span>
						<AutonomyBadge autonomy={entry.autonomy} />
					</span>
					{entry.practiceScript !== undefined && (
						<PracticeScriptTraceLine
							workspaceSlug={workspaceSlug}
							entry={entry}
							run={entry.practiceScript}
							reviewLink={reviewLink}
						/>
					)}
					{occasionedById !== undefined &&
						occurrenceName !== undefined && (
							// Not an anchor: the occurrence is drawn on the other tab, which has to open first.
							<InlineLink
								onClick={() => onShowOccurrence(occasionedById)}
								className="inline-flex items-center gap-1"
							>
								Rests on {occurrenceName}
								<ArrowRightIcon className="size-3 shrink-0" aria-hidden />
							</InlineLink>
						)}
				</span>
			)}
		</span>
	);
}

/**
 * How the practice's program ran in the review, in one muted line: how it ended, the one change
 * that would help it, and *Details*, which opens the review's *Practice scripts* section with the reasons behind any unrated call. The links name the practice for a screen
 * reader, which lists every row's links together.
 */
export function PracticeScriptTraceLine({
	workspaceSlug,
	entry,
	run,
	reviewLink,
	details: showDetails = true,
}: {
	workspaceSlug: string;
	entry: PracticeTraceEntry;
	run: PracticeScriptRun;
	reviewLink: ((reviewId: string, label: ReactNode) => ReactNode) | undefined;
	/** `false` where the line sits on the review's own sheet, beside its section Practice scripts. */
	details?: boolean;
}) {
	const fix = practiceScriptFix(run);
	const { reviewId, practiceName } = entry;
	const details = (
		<>
			Details <span className="sr-only">of the practice script for {practiceName}</span>
			<ArrowRightIcon className="size-3 shrink-0" aria-hidden />
		</>
	);
	return (
		<span className="flex min-w-0 items-start gap-1.5 text-pretty">
			<ScanSearchIcon className="mt-px size-3.5 shrink-0" aria-hidden />
			<span className="flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-1">
				<span>{practiceScriptRunSentence(run)}</span>
				{fix !== undefined && (
					<PracticeScriptFixLink
						workspaceSlug={workspaceSlug}
						practiceSlug={entry.practiceSlug}
						practiceName={practiceName}
						fix={fix}
					/>
				)}
				{showDetails &&
					hasText(reviewId) &&
					(reviewLink?.(reviewId, details) ?? (
						<InlineLink
							render={
								<Link
									to="/w/$workspaceSlug/admin/practices/reviews"
									params={{ workspaceSlug }}
									search={detailSearch(reviewLevel(reviewId))}
								/>
							}
							className="inline-flex items-center gap-1 font-medium"
						>
							{details}
						</InlineLink>
					))}
			</span>
		</span>
	);
}
