import type { ListReviewsData, ReviewQueue, ReviewQueueItem } from "@/api/types.gen";

import { QUEUE_WAIT_THRESHOLDS } from "./review-attention-thresholds";

/**
 * How often the queue is re-read while something in it waits or runs. Slower than a single
 * review's poll: the card is a glance at the whole workspace, not a watch over one review.
 */
export const QUEUE_POLL_MS = 10_000;

/**
 * A wait from this long on is the queue's problem and is marked: the overview's long-wait mark
 * (icon and words, no colour) for the oldest wait (`review-attention-thresholds.ts`), the wait the
 * trace calls slow. The server's `waitingLong` still counts waits over 10 minutes.
 */
export const LONG_WAIT_SECONDS = QUEUE_WAIT_THRESHOLDS.warningSeconds;

/** How many of the queue's head the card lists before it links to the rest. */
export const QUEUE_ROWS = 3;

/** Every review the queue holds now: running, waiting and on hold. */
export function queueTotal(queue: ReviewQueue): number {
	return queue.running + queue.waiting + queue.heldForBudget;
}

/**
 * Something waits for a worker or runs, so the queue is worth re-reading. A budget hold alone is
 * not: it waits for the monthly cap, which can take days, and nothing in the queue moves meanwhile.
 */
export function queueActive(queue: ReviewQueue): boolean {
	return queue.running + queue.waiting > 0;
}

/** Seconds from `at` until the queue was read, never negative when the clocks disagree. */
export function secondsUntilRead(queue: ReviewQueue, at: Date): number {
	return Math.max(0, (queue.asOf.getTime() - at.getTime()) / 1000);
}

/** How long the oldest review has waited for a worker, or undefined when none waits. */
export function oldestWaitSeconds(queue: ReviewQueue): number | undefined {
	return queue.oldestWaitingSince === null
		? undefined
		: secondsUntilRead(queue, queue.oldestWaitingSince);
}

/** The oldest wait has reached the overview's long-wait mark (30 minutes): an icon and words, no colour. */
export function waitsLong(queue: ReviewQueue): boolean {
	return (oldestWaitSeconds(queue) ?? 0) >= LONG_WAIT_SECONDS;
}

export type QueueItemState = "running" | "waiting" | "held";

/**
 * The Reviews list's `queueState`: the reviews whose job stands so in the queue now, split as the
 * queue counts its figures, so a figure opens exactly the reviews it counts.
 */
export const QUEUE_STATES = ["WAITING", "HELD", "RUNNING"] as const satisfies readonly NonNullable<
	NonNullable<ListReviewsData["query"]>["queueState"]
>[];

export type QueueState = (typeof QUEUE_STATES)[number];

/** Each queue state as the queue card says it, for the filter's pill. */
export const QUEUE_STATE_LABELS: Record<QueueState, string> = {
	WAITING: "Waiting",
	HELD: "On hold for budget",
	RUNNING: "Running",
};

export function queueItemState(item: ReviewQueueItem): QueueItemState {
	if (item.status === "RUNNING") {
		return "running";
	}
	return item.heldForBudget ? "held" : "waiting";
}
