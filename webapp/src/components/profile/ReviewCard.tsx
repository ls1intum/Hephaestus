import { ChevronDownIcon, ChevronUpIcon } from "lucide-react";
import { useState } from "react";

import { cn } from "cn";
import type { ItemRef, ProfileReview, ResultList } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import { reviewedWorkIcon } from "@/components/icons/reviewed-work-icon";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatShortDay, formatTime } from "@/lib/dates";
import { hasText } from "@/lib/text";

import { OwnFeedbackList } from "./OwnFeedbackList";
import { ReviewResultRow } from "./ReviewResultRow";
import type { FeedbackResponseControls } from "./reviews";
import { TimelineItem } from "./TimelineItem";

export interface ReviewCardProps {
	/** One of the reader's own reviews: its item, its results about them and their feedback from it. */
	review: ProfileReview;
	/** The reader's response to each piece of feedback; absent leaves no response controls. */
	responses?: FeedbackResponseControls;
	/** Off on a practice's own level, where every row is that practice and no row repeats it. */
	showPracticeName?: boolean;
	/**
	 * Which of this card's results arrive open: every one, only the first, or none. Whoever
	 * places the card decides — a feed of one practice opens its newest row and leaves the reader
	 * to open the rest.
	 */
	initiallyOpen?: "all" | "first" | "none";
	/** Set on the last card while earlier reviews exist below the fold. */
	tailContinues?: boolean;
}

/** How many results a card shows before "Show more". */
const COLLAPSED_RESULT_COUNT = 3;

/** Not met first: it is what the reader can act on. The rest keep the server's order. */
function resultPriority({ outcome }: ResultList): number {
	return outcome === "NOT_MET" ? 0 : 1;
}

/**
 * One review as a row of the timeline: the day and time in the date column, a dot on the rail, and
 * the card with the reviewed item at its head, the results as its rows and the reader's feedback
 * from the review under them. The head names the item and nothing else: what the review is worth
 * reading is what it concluded.
 *
 * A review that carries one result has no two things to separate — on a practice's own level a
 * review reaches that practice once, so every card there is that case. It drops the head and
 * merges into one block: the result's summary and verdict are the block's first line, and the item
 * the head would have named is the small line under it.
 *
 * The feedback is the review's, not one result's: a piece can be based on several results, and a
 * result on none, so the card lists each piece once, with its own response.
 */
export function ReviewCard({
	review,
	responses,
	showPracticeName = true,
	initiallyOpen = "all",
	tailContinues = false,
}: ReviewCardProps) {
	const [showAllResults, setShowAllResults] = useState(false);
	const { results, feedback } = review;
	const at = review.review.createdAt;
	// One result is one block: the row takes the work line and the card grows no head over it.
	const merged = results.length === 1;
	const hiddenCount = Math.max(0, results.length - COLLAPSED_RESULT_COUNT);
	const orderedResults = [...results].sort((a, b) => resultPriority(a) - resultPriority(b));
	const visibleResults = showAllResults
		? orderedResults
		: orderedResults.slice(0, COLLAPSED_RESULT_COUNT);

	return (
		<TimelineItem
			at={at}
			label={
				<>
					<span className="text-sm font-semibold text-foreground">{formatShortDay(at)}</span>
					{formatTime(at)}
				</>
			}
			tailContinues={tailContinues}
		>
			<div className="min-w-0 overflow-hidden rounded-xl border bg-background">
				{!merged && <ReviewedItem item={review.review.item} tone="head" />}
				<ul className="divide-y">
					{visibleResults.map((result, index) => (
						<ReviewResultRow
							key={result.id}
							result={result}
							defaultOpen={initiallyOpen === "all" || (initiallyOpen === "first" && index === 0)}
							showPracticeName={showPracticeName}
							work={merged ? <ReviewedItem item={review.review.item} tone="line" /> : undefined}
						/>
					))}
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
				{feedback.length > 0 && (
					<div className="border-t bg-sidebar px-4 py-3">
						<OwnFeedbackList feedback={feedback} responses={responses} />
					</div>
				)}
			</div>
		</TimelineItem>
	);
}

/**
 * The item a review reviewed, in the two shapes a card needs it.
 *
 * `head` is the head of a card whose results are several: the link the rows below it share,
 * with the repository under it. `line` is the same item under a merged card's summary, on one
 * small line — "#902 · HephaestusTest/practice-validation" — where the summary is the block's
 * anchor and the item it was seen on reads as the note beneath it rather than as a second head.
 *
 * One component, because the two differ in layout and type size only: whether the item is linked,
 * how it is tooltipped and when the repository is printed are the same decisions, and a fix to one
 * copy of them would have missed the other.
 */
function ReviewedItem({ item, tone }: { item: ItemRef; tone: "head" | "line" }) {
	const Icon = reviewedWorkIcon(item.kind, item.provider ?? undefined);
	// The wire names the item: "#902", "#backend-guild", a document's title.
	const identity = item.label;
	const isHead = tone === "head";
	const typography = isHead ? "text-sm font-medium" : "text-xs";
	const name = hasText(item.url) ? (
		<Tooltip>
			<TooltipTrigger
				render={
					<InlineLink href={item.url} external className={cn("flex min-w-0", typography)}>
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
					{hasText(item.container) && (
						<span className="truncate text-xs text-muted-foreground">{item.container}</span>
					)}
				</div>
			</div>
		);
	}

	return (
		<span className="flex min-w-0 items-center gap-1.5 text-xs text-muted-foreground">
			<Icon className="size-3.5 shrink-0" />
			{name}
			{hasText(item.container) && (
				<>
					<span aria-hidden>·</span>
					<span className="truncate">{item.container}</span>
				</>
			)}
		</span>
	);
}
