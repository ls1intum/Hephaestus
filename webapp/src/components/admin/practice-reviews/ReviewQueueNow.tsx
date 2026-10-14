import { ClockIcon, ListOrderedIcon, LoaderIcon, PauseIcon, TriangleAlertIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "cn";
import type { ReviewQueue, ReviewQueueItem } from "@/api/types.gen";
import { InfoLines, InfoTip } from "@/components/common/InfoTip";
import { InlineLink } from "@/components/common/InlineLink";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { type StatusDef, statusToneClass } from "@/components/common/status-def";
import { reviewedWorkIcon } from "@/components/icons/reviewed-work-icon";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import type { ReviewStatus } from "@/components/practice-vocabulary/review-status-defs";
import { Skeleton } from "@/components/ui/skeleton";
import { durationText } from "@/lib/dates";
import { firstNonBlank } from "@/lib/text";

import { type CardFigure, FigureCaption, FigureRow } from "./CardFigures";
import { OVERVIEW_TYPE } from "./overview-type-scale";
import { reviewLevel } from "./review-levels";
import {
	oldestWaitSeconds,
	perHourText,
	QUEUE_ROWS,
	type QueueItemState,
	queueItemState,
	queueTotal,
	secondsUntilRead,
	waitsLong,
} from "./review-queue";
import { ReviewListLink } from "./ReviewListLink";

/** The queue as it stands now, read apart from the range: it is the same whatever the range. */
export type ReviewQueueState = PanelState<{ queue: ReviewQueue }>;

export interface ReviewQueueNowProps {
	workspaceSlug: string;
	state: ReviewQueueState;
}

/**
 * The status of a row: the review registry's words for running and queued, and a hold of its own,
 * since a review held for budget waits for the cap and not for a worker.
 */
const QUEUE_ITEM_DEFS: Record<QueueItemState, StatusDef & { short: string }> = {
	running: {
		label: "Running",
		short: "Running",
		icon: LoaderIcon,
		badgeVariant: "secondary",
		description: "Being run now.",
	},
	waiting: {
		label: "Waiting",
		short: "Waiting",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description: "Waiting for a worker.",
	},
	held: {
		label: "On hold for budget",
		short: "On hold",
		icon: PauseIcon,
		badgeVariant: "destructive",
		description: "The monthly AI budget is used up, so this review waits for it.",
	},
};

export function ReviewQueueNow({ workspaceSlug, state }: ReviewQueueNowProps) {
	let body: ReactNode;
	if (state.status === "error") {
		body = (
			<QueryErrorAlert
				error={state.error}
				title="We could not load the queue"
				onRetry={state.onRetry}
			/>
		);
	} else if (state.status === "loading") {
		body = (
			<div role="status" className="flex flex-col gap-2">
				<span className="sr-only">Loading the queue</span>
				<Skeleton className="h-9 w-full" />
				<Skeleton className="h-4 w-40" />
			</div>
		);
	} else {
		body = <QueueFigures workspaceSlug={workspaceSlug} queue={state.queue} />;
	}
	return (
		<div role="region" aria-label="The queue" className="flex flex-col gap-3">
			<FigureCaption
				icon={ListOrderedIcon}
				info={
					<InfoTip label="About the queue">
						<InfoLines lines={queueInfo(state.status === "ready" ? state.queue : undefined)} />
					</InfoTip>
				}
			>
				Now
			</FigureCaption>
			{body}
		</div>
	);
}

/** The (i) beside "Now": what each figure counts, and how fast the queue empties now. */
function queueInfo(queue: ReviewQueue | undefined) {
	return [
		{
			term: "Waiting",
			text: "counts reviews that wait for a worker now, not those on hold or waiting before a retry.",
		},
		{
			term: "Running",
			text:
				queue === undefined || queue.activeWorkers === 0
					? "counts reviews a worker runs now. The count opens them."
					: `counts reviews a worker runs now, on ${queue.activeWorkers} ${queue.activeWorkers === 1 ? "worker" : "workers"}. The count opens them.`,
		},
		{
			term: "On hold for budget",
			text: "counts reviews that wait now because the monthly AI budget is used up. A hold is red.",
		},
		{
			term: "Oldest wait",
			text: "is how long the first waiting review has waited; from 30 minutes it is marked. It opens that review. It is left out while nothing waits.",
		},
		{
			term: "Since / Waited",
			text: "is how long a review has run or waited. For a review on hold, it counts from its request.",
		},
		{
			term: "Pace",
			text:
				queue === undefined || queue.finishedLastDay === 0
					? "Right now, no review finished in the last 24 hours."
					: `Right now, ${perHourText(queue.finishedPerHourLastDay)} finish an hour, over the last 24 hours.`,
		},
	];
}

function QueueFigures({ workspaceSlug, queue }: { workspaceSlug: string; queue: ReviewQueue }) {
	const total = queueTotal(queue);
	const rows = queue.head.slice(0, QUEUE_ROWS);
	const oldest = oldestWaitSeconds(queue);
	const figures: CardFigure[] = [
		{
			key: "waiting",
			term: "waiting",
			value: <span>{queue.waiting}</span>,
			none: queue.waiting === 0,
		},
		{
			key: "running",
			term: "running",
			value: (
				<QueueCount
					workspaceSlug={workspaceSlug}
					count={queue.running}
					status="RUNNING"
					noun="running"
				>
					{queue.running}
				</QueueCount>
			),
			none: queue.running === 0,
		},
		{
			key: "held",
			term: "on hold for budget",
			value: (
				<Problem show={queue.heldForBudget > 0} broken>
					{queue.heldForBudget}
				</Problem>
			),
			none: queue.heldForBudget === 0,
		},
	];
	// While nothing waits there is no oldest wait to tell: "0 waiting" says it already.
	if (oldest !== undefined) {
		figures.push({
			key: "oldest",
			term: "oldest wait",
			value: (
				<OldestWait queue={queue}>
					<Problem show={waitsLong(queue)}>{durationText(oldest)}</Problem>
				</OldestWait>
			),
		});
	}
	return (
		<>
			<FigureRow label="Queue now" figures={figures} />
			{total === 0 && <p className={OVERVIEW_TYPE.empty}>Nothing waits or runs.</p>}
			{rows.length > 0 && <QueueTable queue={queue} rows={rows} />}
			{total > rows.length && (
				<p className="text-sm">
					<InlineLink
						tone="count"
						render={
							<ReviewListLink
								workspaceSlug={workspaceSlug}
								// The queue's own statuses, so the list holds exactly the reviews the total counts.
								destination={{ list: "runs", search: { status: ["RUNNING", "QUEUED"] } }}
							/>
						}
					>
						See all {total}
					</InlineLink>
				</p>
			)}
		</>
	);
}

/**
 * A count of the queue as the link to the Reviews table filtered to its status, with no days: the
 * queue is now. None of it is plain text.
 */
function QueueCount({
	workspaceSlug,
	count,
	status,
	noun,
	children,
}: {
	workspaceSlug: string;
	count: number;
	status: ReviewStatus;
	noun: string;
	children: ReactNode;
}) {
	if (count === 0) {
		return <span>{children}</span>;
	}
	return (
		<InlineLink
			tone="count"
			aria-label={`${count} ${count === 1 ? "review" : "reviews"} ${noun}`}
			render={
				<ReviewListLink
					workspaceSlug={workspaceSlug}
					destination={{ list: "runs", search: { status: [status] } }}
				/>
			}
		>
			{children}
		</InlineLink>
	);
}

/**
 * The oldest wait as the link to the review that waits: the first waiting review of the queue's head
 * whose wait began then. Not among the head, it is plain text.
 */
function OldestWait({ queue, children }: { queue: ReviewQueue; children: ReactNode }) {
	const since = queue.oldestWaitingSince?.getTime();
	const item = queue.head.find(
		(candidate) => queueItemState(candidate) === "waiting" && candidate.since.getTime() === since,
	);
	if (item === undefined) {
		return <span>{children}</span>;
	}
	return (
		<InlineLink
			tone="count"
			aria-label="Open the review that has waited longest"
			render={<DetailStackLink entry={reviewLevel(item.reviewId)} single />}
		>
			{children}
		</InlineLink>
	);
}

/**
 * A figure that needs a look: the mark and weight, and the same said in words to a screen reader,
 * which hears neither. By the overview's colour rule (`review-attention-thresholds.ts`), only a
 * broken figure, reviews held for budget, is red; a long wait is advice, in the text's colour.
 */
function Problem({
	show,
	broken = false,
	children,
}: {
	show: boolean;
	/** Nothing runs until an admin acts: the mark is red. */
	broken?: boolean;
	children: ReactNode;
}) {
	if (!show) {
		return <span>{children}</span>;
	}
	return (
		<span className={cn("font-semibold", broken && statusToneClass("destructive"))}>
			<span className="sr-only">Needs a look: </span>
			<TriangleAlertIcon aria-hidden className="mr-1 inline size-3.5 align-[-0.125em]" />
			{children}
		</span>
	);
}

/** The heads of the queue's table, small and muted, so the rows carry the eye. */
const QUEUE_HEAD = cn("pb-1 font-medium", OVERVIEW_TYPE.caption);
const QUEUE_CELL = "py-0.5 align-middle";

/**
 * The first reviews in line as a small table: the work, which opens the review, the developer, the
 * state and how long it has run or waited. Each column lines up down the rows; the developer takes
 * what room is left and truncates first, so the time is never cut off. A held review's time runs
 * from its request, since its hold points at the next check of the cap.
 */
function QueueTable({ queue, rows }: { queue: ReviewQueue; rows: readonly ReviewQueueItem[] }) {
	return (
		<table aria-label="First in the queue" className="w-full text-sm">
			<thead>
				<tr className="border-b text-left">
					<th scope="col" className={cn(QUEUE_HEAD, "pr-4")}>
						Work
					</th>
					<th scope="col" className={cn(QUEUE_HEAD, "w-full pr-4")}>
						Developer
					</th>
					<th scope="col" className={cn(QUEUE_HEAD, "pr-4")}>
						State
					</th>
					<th scope="col" className={cn(QUEUE_HEAD, "text-right whitespace-nowrap")}>
						Since / Waited
					</th>
				</tr>
			</thead>
			<tbody>
				{rows.map((item) => (
					<QueueRow key={item.jobId} queue={queue} item={item} />
				))}
			</tbody>
		</table>
	);
}

function QueueRow({ queue, item }: { queue: ReviewQueue; item: ReviewQueueItem }) {
	const def = QUEUE_ITEM_DEFS[queueItemState(item)];
	const Icon = reviewedWorkIcon(item.item.kind, item.item.provider ?? undefined);
	const StateIcon = def.icon;
	return (
		<tr>
			<td className={cn(QUEUE_CELL, "pr-4 whitespace-nowrap")}>
				<InlineLink
					className="inline-flex items-center gap-1.5"
					title={item.item.title ?? undefined}
					render={<DetailStackLink entry={reviewLevel(item.reviewId)} single />}
				>
					<Icon className="size-3.5 shrink-0" />
					<span>{item.item.label}</span>
				</InlineLink>
			</td>
			{/* `max-w-0` lets the cell shrink below its words, so the name truncates, not the row. */}
			<td className={cn(QUEUE_CELL, "max-w-0 pr-4", OVERVIEW_TYPE.label)}>
				<span className="block truncate">
					{item.developer
						? (firstNonBlank(item.developer.displayName, item.developer.providerLogin) ?? "")
						: ""}
				</span>
			</td>
			<td className={cn(QUEUE_CELL, "pr-4 whitespace-nowrap", OVERVIEW_TYPE.label)}>
				<span className="inline-flex items-center gap-1.5" title={def.label}>
					<StateIcon
						aria-hidden
						className={cn("size-3.5 shrink-0", statusToneClass(def.badgeVariant))}
					/>
					{def.short}
				</span>
			</td>
			<td
				className={cn(QUEUE_CELL, "text-right whitespace-nowrap tabular-nums", OVERVIEW_TYPE.label)}
			>
				{durationText(secondsUntilRead(queue, item.since))}
			</td>
		</tr>
	);
}
