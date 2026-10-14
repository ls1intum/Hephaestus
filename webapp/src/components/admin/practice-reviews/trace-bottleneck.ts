import {
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleSlashIcon,
	ClockIcon,
	EyeOffIcon,
	HourglassIcon,
	type LucideIcon,
	TimerOffIcon,
} from "lucide-react";

import type { ReviewBoundFeedback, ReviewDetail, ReviewLineage, ReviewRef } from "@/api/types.gen";
import type { BadgeVariant } from "@/components/common/status-def";
import { REVIEW_STOP_REASON_SENTENCES } from "@/components/practice-vocabulary/review-status-defs";
import { durationBetween } from "@/lib/dates";
import { hasText } from "@/lib/text";

import {
	countOf,
	feedbackOf,
	isSlowGap,
	type LineageFeedback,
	type LineageRecords,
	lineageRecords,
	reachedDeveloper,
	silentNotMet,
} from "./lineage-chain";
import type { TraceStage } from "./lineage-story";
import type { TraceNode } from "./review-trace";

/**
 * The stretches of time a review's timing names, on the overview and on a trace: how long the
 * review waited to be asked for after the event, how long it waited in the queue, how long it
 * prepared and executed, and how long until its feedback reached the developer. The overview splits
 * the run into Preparation and Execution; one review records only its whole Run.
 */
export type TraceSpanKind = "asked" | "queue" | "preparation" | "execution" | "run" | "delivery";

/** Each span's word, which the overview's review time and a review's trace use for the same stretch. */
export const TRACE_SPAN_LABELS: Record<TraceSpanKind, string> = {
	asked: "Until asked",
	queue: "Queue",
	preparation: "Preparation",
	execution: "Execution",
	run: "Run",
	delivery: "To developer",
};

/** The spans one review's record can measure. */
export type MeasuredSpanKind = Extract<TraceSpanKind, "asked" | "queue" | "run" | "delivery">;

export interface TraceSpan {
	kind: MeasuredSpanKind;
	/** "Queue", "Run": the word on the strip. */
	label: string;
	/** What the span runs from and to, for its tooltip. */
	hint: string;
	from: Date;
	to: Date;
	/** "2 h 10 min". */
	text: string;
	/** Half an hour or more: the wait is coloured. */
	slow: boolean;
}

/** Where on the trace the path was lost or slowed: the stage its line names. */
export type BottleneckStage = TraceStage;

/**
 * The one thing a reader of a trace looks for first: where the path was lost, held or slowed, and
 * why, in one line next to the verdict.
 *
 * - `failed`: the review stopped before it finished.
 * - `lost`: feedback did not reach the developer, or no longer stands.
 * - `slow`: everything arrived, but one wait took half an hour or more.
 * - `waiting`: the path is still on its way and waits on something named.
 * - `none`: nothing was lost and no wait stands out.
 */
export type BottleneckKind = "failed" | "lost" | "slow" | "waiting" | "none";

export interface TraceBottleneck {
	kind: BottleneckKind;
	stage: BottleneckStage;
	/** "Withheld", "Failed", "Slow queue": the first words, bold. */
	title: string;
	/** One sentence: what happened and why, with the time it cost where the record says. */
	text: string;
	tone: BadgeVariant;
	icon: LucideIcon;
	/** The span that took longest where it was slow, also coloured on the strip. */
	span?: MeasuredSpanKind;
	/** The reason in a few words, for where the verdict above already says the whole sentence. */
	short?: string;
}

function span(
	kind: MeasuredSpanKind,
	hint: string,
	from: Date | null | undefined,
	to: Date | null | undefined,
): TraceSpan | undefined {
	if (from == null || to == null || to.getTime() < from.getTime()) {
		return undefined;
	}
	return {
		kind,
		label: TRACE_SPAN_LABELS[kind],
		hint,
		from,
		to,
		text: durationBetween(from, to),
		slow: isSlowGap((to.getTime() - from.getTime()) / 1000),
	};
}

/**
 * The review a trace stands on: the one whose detail the page read, else the review of the graph's
 * root result, else the graph's first review.
 */
export function traceReview(
	records: LineageRecords,
	detail: ReviewDetail | undefined,
): ReviewRef | undefined {
	if (detail !== undefined) {
		return detail;
	}
	const rootReviewId = records.root?.review?.id ?? records.root?.result?.reviewId;
	return records.reviews.find((review) => review.id === rootReviewId) ?? records.reviews[0];
}

/** The feedback written from the review's own results. */
function reviewFeedback(records: LineageRecords, review: ReviewRef | undefined): LineageFeedback[] {
	if (review === undefined) {
		return records.feedback;
	}
	const own = new Set(
		records.results.filter((result) => result.reviewId === review.id).map((result) => result.id),
	);
	return records.feedback.filter((entry) => entry.basis.some((id) => own.has(id)));
}

/** The feedback the trace stands on: the piece itself, a result's pieces, or the review's own. */
function feedbackOnPath(
	records: LineageRecords,
	review: ReviewRef | undefined,
	here: TraceNode | undefined,
): LineageFeedback[] {
	if (here?.kind === "feedback") {
		return records.feedback.filter((entry) => entry.id === here.id);
	}
	if (here?.kind === "result") {
		return feedbackOf(records.feedback, here.id);
	}
	return reviewFeedback(records, review);
}

/** When the first runtime job of the review was picked up by a worker. */
function pickedUpAt(detail: ReviewDetail | undefined): Date | undefined {
	const starts = (detail?.runtime?.jobs ?? [])
		.map((job) => job.startedAt)
		.filter((startedAt) => startedAt !== null)
		.sort((a, b) => a.getTime() - b.getTime());
	return starts[0];
}

/**
 * Where the time of the path went, stage by stage: asked for after the event, the queue, the run,
 * until the feedback on the path first reached the developer. A span the record cannot tell is left
 * out, never guessed: the queue and the run need the review's runtime jobs, which expire.
 */
export function traceSpans(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	detail?: ReviewDetail,
): TraceSpan[] {
	const records = lineageRecords(lineage);
	const review = traceReview(records, detail);
	if (review === undefined) {
		return [];
	}
	const pickedUp = pickedUpAt(detail);
	const [delivered] = feedbackOnPath(records, review, here)
		.map((entry) => entry.deliveredAt)
		.filter((deliveredAt) => deliveredAt !== null)
		.sort((a, b) => a.getTime() - b.getTime());
	return [
		span(
			"asked",
			"From the event that started it to when the review was asked for",
			detail?.startedBy?.occurredAt,
			review.createdAt,
		),
		span(
			"queue",
			"From when the review was asked for to when a worker picked it up",
			review.createdAt,
			pickedUp,
		),
		span(
			"run",
			"From when a worker picked the review up to when it finished",
			pickedUp,
			review.completedAt,
		),
		span(
			"delivery",
			"From when the review finished to when its feedback first reached the developer",
			review.completedAt,
			delivered,
		),
	].filter((entry) => entry !== undefined);
}

export interface FeedbackLoss {
	title: string;
	text: string;
	short: string;
	/**
	 * How much the loss asks of an admin: red where sending failed, amber where it was withheld, and
	 * neutral where an admin decided or newer feedback took its place.
	 */
	tone: LossTone;
}

type LossTone = "destructive" | "warning" | "secondary";

/**
 * Why a piece of feedback did not reach the developer or no longer stands; undefined while it
 * stands or waits. The graph records no stop reason, so a withheld piece says only that it was
 * withheld.
 */
export function feedbackLoss(feedback: ReviewBoundFeedback): FeedbackLoss | undefined {
	if (feedback.withdrawn) {
		return {
			title: "Withdrawn",
			// A ready piece can be withdrawn too: only a recorded delivery says it was delivered.
			text: reachedDeveloper(feedback)
				? "An admin withdrew it after it was delivered."
				: "An admin withdrew it before it reached the developer.",
			short: "withdrawn by an admin",
			tone: "secondary",
		};
	}
	switch (feedback.state) {
		case "REJECTED": {
			return {
				title: "Rejected",
				text: "An admin rejected it before it was sent.",
				short: "rejected",
				tone: "secondary",
			};
		}
		case "FAILED": {
			return {
				title: "Delivery failed",
				text: "Sending it failed, so it never reached the developer.",
				short: "delivery failed",
				tone: "destructive",
			};
		}
		case "REPLACED": {
			// Replaced after it reached the developer is the ordinary course of a re-review.
			if (feedback.deliveredAt !== null) {
				return undefined;
			}
			return {
				title: "Replaced",
				text: "Newer feedback replaced it.",
				short: "newer feedback took its place",
				tone: "secondary",
			};
		}
		case "WITHHELD": {
			return {
				title: "Withheld",
				text:
					feedback.deliveredAt === null
						? "It was withheld before it reached the developer. Its feedback record says why."
						: "It was delivered, then withheld. Its feedback record says why.",
				short: "withheld",
				tone: "warning",
			};
		}
		case "AWAITING_APPROVAL":
		case "READY":
		case "DELIVERED":
		case "UNCONFIRMED": {
			return undefined;
		}
	}
}

/** "withheld (2), rejected (1)": the reasons of several lost pieces, most first. */
function lossesByReason(losses: readonly { short: string }[]): string {
	const counts = new Map<string, number>();
	for (const loss of losses) {
		counts.set(loss.short, (counts.get(loss.short) ?? 0) + 1);
	}
	return [...counts.entries()]
		.sort((a, b) => b[1] - a[1])
		.map(([short, count]) => (losses.length === 1 ? short : `${short} (${count})`))
		.join(", ");
}

/**
 * Lost feedback in the tone of what it asks of an admin, the strongest of its pieces: a delivery
 * failure is red, a withheld piece amber, and a piece an admin held back is neutral.
 */
function lost(losses: readonly FeedbackLoss[]): { tone: LossTone; icon: LucideIcon } {
	let tone: LossTone = "secondary";
	for (const loss of losses) {
		if (loss.tone === "destructive" || (loss.tone === "warning" && tone === "secondary")) {
			({ tone } = loss);
		}
	}
	return { tone, icon: EyeOffIcon };
}

/** A not-met result the review told nobody about: worth a look, never a failure. */
const NOT_TOLD = { tone: "warning", icon: EyeOffIcon } as const;

/** The error the review's last failed runtime job recorded, and how many attempts it took. */
function runtimeFailure(detail: ReviewDetail | undefined): { error?: string; attempts: number } {
	const jobs = detail?.runtime?.jobs ?? [];
	const failed = [...jobs].reverse().find((job) => hasText(job.errorMessage));
	const attempts = Math.max(0, ...jobs.map((job) => job.attempt));
	return {
		error: hasText(failed?.errorMessage)
			? failed.errorMessage.trim().replace(/\.?$/u, ".")
			: undefined,
		attempts,
	};
}

/** A review that stopped or still runs is its own bottleneck; a complete one is not. */
function reviewBottleneck(
	review: ReviewRef,
	detail: ReviewDetail | undefined,
	now: Date,
): TraceBottleneck | undefined {
	const stopReason = detail?.stopReason ?? null;
	const because = stopReason === null ? undefined : REVIEW_STOP_REASON_SENTENCES[stopReason];
	const { error, attempts } = runtimeFailure(detail);
	const tries = attempts > 1 ? ` It was tried ${countOf(attempts, "time", "times")}.` : "";
	switch (review.status) {
		case "FAILED": {
			const timedOut = stopReason === "TIMED_OUT" || stopReason === "DEADLINE_EXCEEDED";
			return {
				kind: "failed",
				stage: "review",
				title: timedOut ? "Review timed out" : "Review failed",
				text: `${error ?? because ?? "It stopped on an error. The error is not recorded here."}${tries}`,
				tone: "destructive",
				icon: timedOut ? TimerOffIcon : CircleAlertIcon,
			};
		}
		case "PARTIAL": {
			return {
				kind: "failed",
				stage: "review",
				title: "Review did not finish",
				text: `${because ?? "It stopped before it finished."} What it recorded up to then still stands.`,
				tone: "warning",
				icon: CircleDashedIcon,
			};
		}
		case "CANCELLED": {
			return {
				kind: "failed",
				stage: "review",
				title: "Review cancelled",
				text: because ?? "Somebody stopped it before it finished.",
				tone: "outline",
				icon: CircleSlashIcon,
			};
		}
		case "QUEUED": {
			const slow = isSlowGap((now.getTime() - review.createdAt.getTime()) / 1000);
			return {
				kind: "waiting",
				stage: "review",
				title: "In the queue",
				text: `It has waited ${durationBetween(review.createdAt, now)} for a worker so far.`,
				tone: slow ? "warning" : "secondary",
				icon: ClockIcon,
				span: "queue",
			};
		}
		case "RUNNING": {
			return {
				kind: "waiting",
				stage: "review",
				title: "Running",
				text: `It has run ${durationBetween(pickedUpAt(detail) ?? review.createdAt, now)} so far.`,
				tone: "secondary",
				icon: HourglassIcon,
				span: "run",
			};
		}
		case "COMPLETE": {
			return undefined;
		}
	}
}

/** The not-met results on the path that led to no feedback at all. */
function silentOnPath(records: LineageRecords, review: ReviewRef, here: TraceNode | undefined) {
	if (here?.kind === "feedback") {
		return [];
	}
	const silent = silentNotMet(records);
	return here?.kind === "result"
		? silent.filter((entry) => entry.id === here.id)
		: silent.filter((entry) => entry.reviewId === review.id);
}

/** What of the path's feedback was lost: one piece, all of them, or some. */
function lossBottleneck(
	records: LineageRecords,
	review: ReviewRef,
	here: TraceNode | undefined,
): TraceBottleneck | undefined {
	const pieces = feedbackOnPath(records, review, here);
	const losses = pieces.map(feedbackLoss).filter((loss) => loss !== undefined);
	const reached = pieces.filter(reachedDeveloper).length;
	if (losses.length === 0) {
		const silent = silentOnPath(records, review, here);
		if (pieces.length > 0 || silent.length === 0) {
			return undefined;
		}
		return {
			kind: "lost",
			stage: "review",
			title: "Not told",
			text: `${silent.length === 1 ? "One result was" : `${silent.length} results were`} not met, but the review composed no feedback from ${silent.length === 1 ? "it" : "them"}, so the developer was not told.`,
			...NOT_TOLD,
		};
	}
	const [first] = losses;
	if (losses.length === 1 && first !== undefined && (here?.kind === "feedback" || reached === 0)) {
		return {
			kind: "lost",
			stage: "feedback",
			title: first.title,
			text: first.text,
			short: first.short,
			...lost(losses),
		};
	}
	if (reached === 0) {
		return {
			kind: "lost",
			stage: "feedback",
			title: "None arrived",
			text: `${countOf(losses.length, "piece", "pieces")} of feedback did not reach the developer: ${lossesByReason(losses)}.`,
			...lost(losses),
		};
	}
	return {
		kind: "lost",
		stage: "feedback",
		title: "Partly lost",
		text: `${reached} of ${pieces.length} pieces reached the developer; ${losses.length} did not: ${lossesByReason(losses)}.`,
		...lost(losses),
	};
}

/**
 * What a path still on its way waits on: an admin's approval, a conversation that has not come, a
 * send in progress, or a conversation that did not record showing it.
 */
function waitBottleneck(
	records: LineageRecords,
	review: ReviewRef,
	here: TraceNode | undefined,
): TraceBottleneck | undefined {
	const pieces = feedbackOnPath(records, review, here);
	if (pieces.some(reachedDeveloper)) {
		return undefined;
	}
	if (pieces.some((entry) => entry.state === "AWAITING_APPROVAL")) {
		return {
			kind: "waiting",
			stage: "feedback",
			title: "Awaiting approval",
			text: "It waits for a workspace admin to approve or reject it before anything is sent.",
			tone: "warning",
			icon: HourglassIcon,
		};
	}
	const ready = pieces.filter((entry) => entry.state === "READY");
	if (ready.some((entry) => entry.destination === "CONVERSATION")) {
		return {
			kind: "waiting",
			stage: "feedback",
			title: "Waiting for a conversation",
			text: "It reaches the developer only when they next talk with Heph.",
			tone: "secondary",
			icon: HourglassIcon,
		};
	}
	if (ready.length > 0) {
		return {
			kind: "waiting",
			stage: "feedback",
			title: "Not sent yet",
			text: "It is ready and not delivered yet.",
			tone: "secondary",
			icon: HourglassIcon,
		};
	}
	if (pieces.some((entry) => entry.state === "UNCONFIRMED")) {
		return {
			kind: "waiting",
			stage: "effect",
			title: "Delivery unconfirmed",
			text: "The conversation linked this feedback, but its appearance was not recorded.",
			tone: "secondary",
			icon: HourglassIcon,
		};
	}
	return undefined;
}

const SPAN_STAGE: Record<MeasuredSpanKind, BottleneckStage> = {
	asked: "start",
	queue: "review",
	run: "review",
	delivery: "feedback",
};

function spanLength(entry: TraceSpan): number {
	return entry.to.getTime() - entry.from.getTime();
}

/** The longest wait of half an hour or more. */
function timeBottleneck(
	lineage: ReviewLineage,
	records: LineageRecords,
	review: ReviewRef,
	here: TraceNode | undefined,
	detail: ReviewDetail | undefined,
): TraceBottleneck {
	const pieces = feedbackOnPath(records, review, here);
	const [slowest] = traceSpans(lineage, here, detail)
		.filter((entry) => entry.slow)
		.sort((a, b) => spanLength(b) - spanLength(a));
	if (slowest !== undefined) {
		return {
			kind: "slow",
			stage: SPAN_STAGE[slowest.kind],
			title: SLOW_TITLE[slowest.kind],
			text: SLOW_TEXT[slowest.kind](slowest.text),
			tone: "warning",
			icon: ClockIcon,
			span: slowest.kind,
		};
	}
	return {
		kind: "none",
		stage: "effect",
		title: "No bottleneck",
		text: noneText(pieces.length, pieces.filter(reachedDeveloper).length),
		// The ordinary case takes no colour, so the ones that do stand out.
		tone: "secondary",
		icon: CircleCheckIcon,
	};
}

/**
 * The bottleneck of the path the trace stands on, read from the graph and, where the page has it,
 * the review's detail: its stop reason, its runtime jobs and the event that started it. It names
 * the first thing that stopped the path: the review itself, then lost feedback; where nothing
 * stopped it, what it still waits on, else the longest wait.
 */
export function traceBottleneck(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	detail?: ReviewDetail,
	now: Date = new Date(),
): TraceBottleneck {
	const records = lineageRecords(lineage);
	const review = traceReview(records, detail);
	if (review === undefined) {
		return {
			kind: "none",
			stage: "review",
			title: "Not known",
			text: "The record of this review is gone, so the trace cannot tell where time went.",
			tone: "outline",
			icon: CircleSlashIcon,
		};
	}
	return (
		reviewBottleneck(review, detail, now) ??
		lossBottleneck(records, review, here) ??
		waitBottleneck(records, review, here) ??
		timeBottleneck(lineage, records, review, here, detail)
	);
}

function noneText(pieces: number, reached: number): string {
	if (pieces === 0) {
		return "Nothing on this path had to be sent.";
	}
	return reached > 0
		? "Nothing was lost and no wait took half an hour or more."
		: "Nothing was lost on this path.";
}

const SLOW_TITLE: Record<MeasuredSpanKind, string> = {
	asked: "Slow start",
	queue: "Slow queue",
	run: "Slow run",
	delivery: "Slow delivery",
};

const SLOW_TEXT: Record<MeasuredSpanKind, (duration: string) => string> = {
	asked: (duration) => `The review was asked for ${duration} after the event that started it.`,
	queue: (duration) => `The review waited ${duration} in the queue for a worker.`,
	run: (duration) => `The review ran for ${duration}.`,
	delivery: (duration) =>
		`Its feedback reached the developer ${duration} after the review finished.`,
};

function plain(text: string): string {
	return text
		.toLowerCase()
		.replaceAll(/[.:;,]/gu, "")
		.trim();
}

/**
 * The line as it stands under a verdict: where the verdict already says the whole reason, only its
 * few words, so the two lines never say the same thing twice.
 */
export function bottleneckText(bottleneck: TraceBottleneck, verdict: string): string {
	if (bottleneck.short !== undefined && plain(verdict).includes(plain(bottleneck.text))) {
		return bottleneck.short;
	}
	return bottleneck.text;
}
