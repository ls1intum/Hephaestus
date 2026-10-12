import { ChevronDownIcon, ChevronUpIcon } from "lucide-react";
import { useState } from "react";

import { cn } from "cn";
import type {
	PracticeGroupReviewRun,
	PracticeResultDetail,
	ReviewedWorkRef,
} from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import { reviewedWorkIcon } from "@/components/icons/reviewed-work-icon";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatShortDay, formatTime } from "@/lib/dates";
import { hasText } from "@/lib/text";

import type { ResultControls } from "./review-runs";
import { ReviewResultRow } from "./ReviewResultRow";
import { TimelineItem } from "./TimelineItem";

export interface ReviewRunCardProps {
	run: PracticeGroupReviewRun;
	/** The reader's response to a result, handed to every row. */
	results?: ResultControls;
	/** Off on a practice's own level, where every row is that practice and no row repeats it. */
	showPracticeName?: boolean;
	/**
	 * Which of this card's results arrive open: every one, only the first, or none. Whoever
	 * places the card decides — a feed of one practice opens its newest row and leaves the reader
	 * to open the rest.
	 */
	initiallyOpen?: "all" | "first" | "none";
	/** Set on the last card while earlier runs exist below the fold. */
	tailContinues?: boolean;
}

const NO_CONTROLS: ResultControls = {};

/** How many results a card shows before "Show more". */
const COLLAPSED_RESULT_COUNT = 3;

function resultPriority({ outcome, feedbackResponse }: PracticeResultDetail): number {
	const delivered = hasText(feedbackResponse?.feedbackId);
	if (outcome === "NOT_MET") {
		return delivered ? 0 : 1;
	}
	return delivered ? 2 : 3;
}

/**
 * One review run as a row of the timeline: the day and time in the date column, a dot on the
 * rail, and the card with the reviewed work at its head and the results as its rows. The head
 * names the work and nothing else: what the run is worth reading is what it concluded.
 *
 * A run that carries one result has no two things to separate — on a practice's own level a run
 * reviews that practice once, so every card there is that case. It drops the head and merges into
 * one block: the result's summary and verdict are the block's first line, and the work
 * the head would have named is the small line under it.
 */
export function ReviewRunCard({
	run,
	results = NO_CONTROLS,
	showPracticeName = true,
	initiallyOpen = "all",
	tailContinues = false,
}: ReviewRunCardProps) {
	const [showAllResults, setShowAllResults] = useState(false);
	// One result is one block: the row takes the work line and the card grows no head over it.
	const merged = run.practiceResults.length === 1;
	const hiddenCount = Math.max(0, run.practiceResults.length - COLLAPSED_RESULT_COUNT);
	const orderedResults = [...run.practiceResults].sort(
		(a, b) => resultPriority(a) - resultPriority(b),
	);
	const visibleResults = showAllResults
		? orderedResults
		: orderedResults.slice(0, COLLAPSED_RESULT_COUNT);

	return (
		<TimelineItem
			at={run.reviewedAt}
			label={
				<>
					<span className="text-sm font-semibold text-foreground">
						{formatShortDay(run.reviewedAt)}
					</span>
					{formatTime(run.reviewedAt)}
				</>
			}
			tailContinues={tailContinues}
		>
			<div className="min-w-0 overflow-hidden rounded-xl border bg-background">
				{!merged && <ReviewedWork work={run.reviewedWork} tone="head" />}
				<ul className="divide-y">
					{visibleResults.map((result, index) => {
						const feedbackId = result.feedbackResponse?.feedbackId;
						return (
							<ReviewResultRow
								key={result.id}
								result={result}
								defaultOpen={initiallyOpen === "all" || (initiallyOpen === "first" && index === 0)}
								showPracticeName={showPracticeName}
								work={merged ? <ReviewedWork work={run.reviewedWork} tone="line" /> : undefined}
								onRespond={results.onRespond}
								pendingResponse={
									hasText(feedbackId) ? results.pendingResponses?.get(feedbackId) : undefined
								}
							/>
						);
					})}
				</ul>
				{hiddenCount > 0 && (
					<div className="border-t px-4 py-2">
						<Button
							type="button"
							variant="quiet"
							size="sm"
							className="h-8"
							onClick={() => setShowAllResults((current) => !current)}
						>
							{showAllResults
								? "Show less"
								: `Show ${hiddenCount} more ${hiddenCount === 1 ? "result" : "results"}`}
							{showAllResults ? (
								<ChevronUpIcon data-icon="inline-end" />
							) : (
								<ChevronDownIcon data-icon="inline-end" />
							)}
						</Button>
					</div>
				)}
			</div>
		</TimelineItem>
	);
}

/**
 * The work a run reviewed, in the two shapes a card needs it.
 *
 * `head` is the head of a card whose results are several: the link the rows below it share,
 * with the repository under it. `line` is the same work under a merged card's summary, on one
 * small line — "#902 · HephaestusTest/practice-validation" — where the summary is the block's
 * anchor and the work it was seen on reads as the note beneath it rather than as a second head.
 *
 * One component, because the two differ in layout and type size only: whether the work is linked,
 * how it is tooltipped and when the repository is printed are the same decisions, and a fix to one
 * copy of them would have missed the other.
 */
function ReviewedWork({ work, tone }: { work: ReviewedWorkRef; tone: "head" | "line" }) {
	const Icon = reviewedWorkIcon(work.kind, work.provider);
	// The wire names the work: "#902", "#backend-guild", a document's title.
	const identity = work.label;
	const isHead = tone === "head";
	const typography = isHead ? "text-sm font-medium" : "text-xs";
	const name = hasText(work.url) ? (
		<Tooltip>
			<TooltipTrigger
				render={
					<InlineLink href={work.url} external className={cn("flex min-w-0", typography)}>
						<span className="truncate">{identity}</span>
					</InlineLink>
				}
			/>
			<TooltipContent className="max-w-80 text-pretty">{identity}</TooltipContent>
		</Tooltip>
	) : (
		<span className={cn("truncate", typography)}>{identity}</span>
	);

	if (isHead) {
		return (
			<div className="flex min-w-0 items-start gap-2.5 border-b px-4 py-3">
				<Icon className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" />
				<div className="flex min-w-0 flex-col gap-0.5">
					{name}
					{hasText(work.container) && (
						<span className="truncate text-xs text-muted-foreground">{work.container}</span>
					)}
				</div>
			</div>
		);
	}

	return (
		<span className="flex min-w-0 items-center gap-1.5 text-xs text-muted-foreground">
			<Icon className="size-3.5 shrink-0" />
			{name}
			{hasText(work.container) && (
				<>
					<span aria-hidden>·</span>
					<span className="truncate">{work.container}</span>
				</>
			)}
		</span>
	);
}
