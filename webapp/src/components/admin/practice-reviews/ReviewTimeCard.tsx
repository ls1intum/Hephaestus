import { TimerIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "cn";
import type { ReviewTiming } from "@/api/types.gen";
import { STALE } from "@/components/activity/activity-tones";
import { InfoLines, InfoTip } from "@/components/common/InfoTip";
import { InlineLink } from "@/components/common/InlineLink";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Skeleton } from "@/components/ui/skeleton";
import { durationText } from "@/lib/dates";

import { type CardFigure, FigureCaption, FigureRow } from "./CardFigures";
import { OVERVIEW_TYPE } from "./overview-type-scale";
import type { ReviewListTarget } from "./review-outcomes";
import type { ReviewCountState } from "./review-states";
import {
	REVIEW_STAGES,
	type ReviewStage,
	type TypicalReview,
	typicalReview,
} from "./review-timing";
import { ReviewListLink } from "./ReviewListLink";

/** The timing once it is in: current, or the previous range's, standing in while this one loads. */
export type ReviewTimingState = PanelState<{ timing: ReviewTiming; stale: boolean }>;

/** The reviews of the range on screen that finished, counted as the overview counts them. */
export interface FinishedInRange {
	state: ReviewCountState;
	/** The Reviews table filtered to exactly the reviews counted. */
	list: ReviewListTarget;
}

export interface ReviewTimeCardProps {
	workspaceSlug: string;
	state: ReviewTimingState;
	/** The range by name, the part's caption: "Last 30 days". */
	label: string;
	/** What the time was measured over: "in the last 30 days". */
	during: string;
	/** The finished reviews of the range, the first of the part's figures; absent, not shown. */
	finished?: FinishedInRange;
}

/**
 * The right part of the Queue card, under the range's name: how long a typical review took, the
 * median of whole reviews, from the moment on the work that started each, or its request, until its
 * comment was posted on the work, and under it the same time as one bar split into the stages a
 * review's trace names, Until asked, Queue, Preparation, Execution and To developer, with the
 * unsplit Run of older reviews where the range has some, each as wide as its share of the reviews'
 * total time, with a legend of that share of the median, so the parts add up to it. Then one line of
 * figures: how many reviews of the range finished, the feedback lost because the work merged first, and the
 * reviews that finished after the merge. Where no review posted a comment on the work, as in a course whose feedback goes
 * to the practice pages, the part says so and measures to the end of the review instead. What each
 * figure means is in the (i) beside the caption.
 */
export function ReviewTimeCard({
	workspaceSlug,
	state,
	label,
	during,
	finished,
}: ReviewTimeCardProps) {
	let body: ReactNode;
	if (state.status === "error") {
		body = (
			<QueryErrorAlert
				error={state.error}
				title="We could not load the review time"
				onRetry={state.onRetry}
			/>
		);
	} else if (state.status === "loading") {
		body = (
			<div role="status" className="flex flex-col gap-3">
				<span className="sr-only">Loading the review time</span>
				<Skeleton className="h-6 w-16" />
				<Skeleton className="h-4 w-full" />
			</div>
		);
	} else {
		body = (
			<div
				aria-busy={state.stale || undefined}
				className={cn("flex flex-col gap-3", state.stale && STALE)}
			>
				<TimeFigures timing={state.timing} during={during} />
			</div>
		);
	}
	const timing = state.status === "ready" ? state.timing : undefined;
	return (
		<div role="region" aria-label="Review time" className="flex flex-col gap-3">
			<FigureCaption
				icon={TimerIcon}
				info={
					<InfoTip label="About review time">
						<InfoLines lines={timeInfo(timing)} />
					</InfoTip>
				}
			>
				{label}
			</FigureCaption>
			{body}
			<RangeFigures workspaceSlug={workspaceSlug} timing={timing} finished={finished} />
		</div>
	);
}

/**
 * The card's (i), one short line a term: what the figure is, each stage, then each figure of the
 * range. What a stage leaves out, such as an admin's approval, is in the docs, not here.
 */
function timeInfo(timing: ReviewTiming | undefined) {
	const byMerge = timing?.finishedAfterMergeStartedByMerge ?? 0;
	const typical = timing === undefined ? undefined : typicalReview(timing);
	// A stage only some ranges have, the unsplit run of older reviews, is explained where it shows.
	const shown = new Set(typical?.stages.map((stage) => stage.def.key));
	return [
		{
			term: "Median",
			text:
				typical?.until === "end"
					? "of whole reviews, from what started each to the end of the review, as no review posted a comment on the work."
					: "of whole reviews, from what started each to its comment on the work.",
		},
		{
			term: "Stages",
			text: "split that median by each stage's share of the total time of the reviews measured. They add up to the median, but are not each stage's own median.",
		},
		...REVIEW_STAGES.filter((stage) => stage.optional !== true || shown.has(stage.key)).map(
			(stage) => ({ term: stage.label, text: stage.text }),
		),
		{
			term: "Finished",
			text: "counts the reviews created in the range that completed, failed or timed out.",
		},
		{
			term: "Lost to merge",
			text: "counts pieces of feedback withheld because the work was merged first.",
		},
		{
			term: "Finished after merge",
			text: `counts reviews that finished after their work was merged.${byMerge > 0 ? ` ${byMerge} of them ${byMerge === 1 ? "was" : "were"} started by the merge.` : ""}`,
		},
	];
}

/**
 * Each stage's shade of the bar: one quiet tone in four strengths, neighbours apart, so the bar is
 * calm in both themes and the gap between two parts still shows where one ends.
 */
const STAGE_SHADES: Record<ReviewStage, string> = {
	asked: "bg-muted-foreground/30",
	queue: "bg-muted-foreground/60",
	preparation: "bg-muted-foreground/40",
	execution: "bg-muted-foreground",
	run: "bg-muted-foreground/70",
	delivery: "bg-muted-foreground/45",
};

/**
 * The typical review as one bar, each stage as wide as its part of the median, then a legend of each
 * stage's label and time. A stage with no time takes no room in the bar, but keeps its place in the
 * legend, so the stages are always named; the unsplit Run only where the range has some.
 */
function StageBar({ typical }: { typical: TypicalReview }) {
	if (typical.stages.length === 0) {
		return null;
	}
	return (
		<div className="flex flex-col gap-2">
			<div aria-hidden className="flex h-2 w-full gap-0.5 overflow-hidden rounded-full">
				{typical.stages
					.filter((stage) => stage.share > 0)
					.map((stage) => (
						<span
							key={stage.def.key}
							data-stage={stage.def.key}
							className={cn("h-full min-w-1 grow-(--share) basis-0", STAGE_SHADES[stage.def.key])}
							style={{ "--share": stage.share }}
						/>
					))}
			</div>
			<ol
				aria-label="Where the time went"
				className={cn("flex flex-wrap items-center gap-x-4 gap-y-1", OVERVIEW_TYPE.label)}
			>
				{typical.stages.map((stage) => (
					<li key={stage.def.key} className="inline-flex items-center gap-1.5 whitespace-nowrap">
						<span
							aria-hidden
							className={cn("size-2 shrink-0 rounded-full", STAGE_SHADES[stage.def.key])}
						/>
						<span>{stage.def.label}</span>
						<span className={stage.seconds > 0 ? OVERVIEW_TYPE.value : OVERVIEW_TYPE.none}>
							{durationText(stage.seconds)}
						</span>
					</li>
				))}
			</ol>
		</div>
	);
}

function TimeFigures({ timing, during }: { timing: ReviewTiming; during: string }) {
	const typical = typicalReview(timing);
	if (typical === undefined) {
		return <p className={OVERVIEW_TYPE.empty}>No review finished {during}.</p>;
	}
	return (
		<div className="flex flex-col gap-3">
			{typical.until === "end" && (
				<p className={OVERVIEW_TYPE.empty}>No review posted a comment on the work {during}.</p>
			)}
			<p className="flex flex-wrap items-baseline gap-x-1.5">
				<span className={OVERVIEW_TYPE.hero}>{durationText(typical.timing.medianSeconds)}</span>
				<span className={OVERVIEW_TYPE.heroNoun}>
					{typical.until === "comment"
						? "median, start to comment"
						: "median, start to end of review"}
				</span>
			</p>
			<StageBar typical={typical} />
		</div>
	);
}

function RangeFigures({
	workspaceSlug,
	timing,
	finished,
}: {
	workspaceSlug: string;
	timing: ReviewTiming | undefined;
	finished: FinishedInRange | undefined;
}) {
	const figures: CardFigure[] = [];
	if (finished) {
		figures.push(finishedFigure(workspaceSlug, finished));
	}
	const lost = timing?.feedbackWithheldAsMerged ?? 0;
	if (lost > 0) {
		figures.push({ key: "lost", term: "lost to merge", value: lost });
	}
	// Every review that finished after the merge, the ones the merge started too; the (i) says how
	// many the merge started. The reviews list cannot filter by it, so the count opens nothing.
	const afterMerge = timing?.finishedAfterMerge ?? 0;
	if (afterMerge > 0) {
		figures.push({ key: "late", term: "finished after merge", value: afterMerge });
	}
	const stale = finished?.state.status === "ready" && finished.state.stale;
	return (
		<div className={cn(stale && STALE)}>
			<FigureRow label="Review time figures" figures={figures} />
		</div>
	);
}

/** How many reviews of the range finished, opening the reviews counted. */
function finishedFigure(workspaceSlug: string, { state, list }: FinishedInRange): CardFigure {
	let value: ReactNode;
	if (state.status === "error") {
		value = null;
	} else if (state.status === "loading") {
		value = <Skeleton className="h-5 w-8" />;
	} else if (state.total === 0) {
		value = 0;
	} else {
		value = (
			<InlineLink
				tone="count"
				aria-label={`${state.total} ${state.total === 1 ? "review" : "reviews"} finished`}
				render={<ReviewListLink workspaceSlug={workspaceSlug} destination={list} />}
			>
				{state.total}
			</InlineLink>
		);
	}
	return {
		key: "finished",
		term: state.status === "error" ? "finished: not loaded" : "finished",
		value,
		none: state.status !== "ready" || state.total === 0,
	};
}
