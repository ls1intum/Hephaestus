import {
	CircleAlertIcon,
	CircleCheckIcon,
	CircleSlashIcon,
	ClockIcon,
	EyeOffIcon,
	HourglassIcon,
	type LucideIcon,
	PauseIcon,
	TimerOffIcon,
} from "lucide-react";

import type { AgentJob, ReviewLineage, ReviewLineageFeedback } from "@/api/types.gen";
import type { BadgeVariant } from "@/components/common/status-def";
import { REVIEW_CANCELLATION_DEFS } from "@/components/practice-vocabulary/review-cancellation-defs";
import { withholdingReasonSentence } from "@/components/practice-vocabulary/withholding-defs";
import { durationBetween } from "@/lib/dates";
import { hasText } from "@/lib/text";

import { holdReasonCopy } from "./job-utils";
import { countOf, isSlowGap, reachedDeveloper, shortReason, silentNotMet } from "./lineage-chain";
import { traceStart } from "./lineage-story";
import { withheldClass } from "./review-attention";
import type { TraceNode } from "./review-trace";

/**
 * The stretch of time between two steps of a trace that a reader asks about: how long the review
 * waited to be asked for after the moment, how long it waited in the queue, how long it ran, how
 * long until its feedback reached the developer, and until a developer first opened it.
 */
export type TraceSpanKind =
	| "asked"
	| "queue"
	| "preparation"
	| "execution"
	| "run"
	| "delivery"
	| "read";

/**
 * Each span's word on the strip, which the overview's review time and the review's own sheet use for
 * the same stretch. A run splits into Preparation and Execution where the review recorded when its
 * execution started; where it did not, it stays one Run.
 */
export const TRACE_SPAN_LABELS: Record<TraceSpanKind, string> = {
	asked: "Until asked",
	queue: "Queue",
	preparation: "Preparation",
	execution: "Execution",
	run: "Run",
	delivery: "To developer",
	read: "First read",
};

export interface TraceSpan {
	kind: TraceSpanKind;
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
export type BottleneckStage = "trigger" | "review" | "feedback" | "effect";

/**
 * The one thing a reader of a trace looks for first: where the path was lost, held or slowed, and
 * why, in one line next to the verdict.
 *
 * - `failed`: the review stopped and composed nothing more.
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
	span?: TraceSpanKind;
	/** The reason in a few words, for where the verdict above already says the whole sentence. */
	short?: string;
	/** Words that, where the verdict says them, mean it already tells the reason: then `short` stands. */
	echo?: string;
}

function span(
	kind: TraceSpanKind,
	label: string,
	hint: string,
	from: Date | undefined,
	to: Date | undefined,
): TraceSpan | undefined {
	if (from === undefined || to === undefined || to.getTime() < from.getTime()) {
		return undefined;
	}
	return {
		kind,
		label,
		hint,
		from,
		to,
		text: durationBetween(from, to),
		slow: isSlowGap((to.getTime() - from.getTime()) / 1000),
	};
}

/**
 * When the work was merged: from the chain, else from the review's own record, else from the
 * merge moment on the work. The chain reads it from the pull request, which can miss it where the
 * moment has it.
 */
export function mergedAtOf(
	lineage: ReviewLineage,
	job?: Pick<AgentJob, "workMergedAt">,
): Date | undefined {
	if (lineage.workMergedAt !== undefined) {
		return lineage.workMergedAt;
	}
	if (job?.workMergedAt !== undefined) {
		return job.workMergedAt;
	}
	let merged: Date | undefined;
	for (const moment of lineage.workMoments) {
		if (moment.kind === "MERGED") {
			merged = moment.occurredAt;
		}
	}
	return merged;
}

/** The feedback the trace stands on: the piece itself, an observation's pieces, or the review's own. */
function feedbackOnPath(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
): ReviewLineageFeedback[] {
	if (here?.kind === "feedback") {
		return lineage.feedback.filter((entry) => entry.id === here.id);
	}
	if (here?.kind === "observation") {
		return lineage.feedback.filter((entry) => entry.observationIds.includes(here.id));
	}
	const reviewId = lineage.review?.id;
	return lineage.feedback.filter(
		(entry) => reviewId === undefined || entry.agentJobId === reviewId,
	);
}

/**
 * Where the time of the path went, stage by stage: asked for after the moment, the queue, the run,
 * until the feedback on the path reached the developer, until a developer first opened it. A span
 * the record cannot tell is left out, never guessed.
 */
export function traceSpans(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	job?: Pick<AgentJob, "firstFeedbackReadAt" | "executionStartedAt">,
): TraceSpan[] {
	const { review } = lineage;
	if (review === undefined) {
		return [];
	}
	const start = traceStart(lineage);
	const moment =
		start !== undefined && start.getTime() < review.createdAt.getTime() ? start : undefined;
	// The first piece on the path that reached the developer: from when it was composed to then.
	const first = feedbackOnPath(lineage, here)
		.filter((entry) => reachedDeveloper(entry) && entry.deliveredAt !== undefined)
		.sort((a, b) => (a.deliveredAt?.getTime() ?? 0) - (b.deliveredAt?.getTime() ?? 0))[0];
	const delivered = first?.deliveredAt;
	const finished = review.finishedAt;
	return [
		span(
			"asked",
			TRACE_SPAN_LABELS.asked,
			"From the moment that started it to when the review was asked for",
			moment,
			review.createdAt,
		),
		span(
			"queue",
			TRACE_SPAN_LABELS.queue,
			"From when the review was asked for to when a worker picked it up",
			review.createdAt,
			review.startedAt,
		),
		...runSpans(review.startedAt ?? review.createdAt, job?.executionStartedAt, finished),
		span(
			"delivery",
			TRACE_SPAN_LABELS.delivery,
			"From when the feedback was composed to when it reached the developer",
			first?.createdAt,
			delivered,
		),
		span(
			"read",
			TRACE_SPAN_LABELS.read,
			"From delivery to when a developer first opened feedback of this review",
			delivered,
			job?.firstFeedbackReadAt,
		),
	].filter((entry) => entry !== undefined);
}

/**
 * The run as Preparation and Execution when the review recorded when its execution started, between
 * its pick-up and its end; otherwise, or when that moment is out of order, as one Run.
 */
function runSpans(
	from: Date,
	executionStartedAt: Date | undefined,
	to: Date | undefined,
): (TraceSpan | undefined)[] {
	const split =
		executionStartedAt !== undefined &&
		to !== undefined &&
		executionStartedAt.getTime() >= from.getTime() &&
		executionStartedAt.getTime() <= to.getTime();
	if (!split) {
		return [
			span(
				"run",
				TRACE_SPAN_LABELS.run,
				"From when a worker picked the review up to when it finished",
				from,
				to,
			),
		];
	}
	return [
		span(
			"preparation",
			TRACE_SPAN_LABELS.preparation,
			"From when a worker picked the review up to when its execution started",
			from,
			executionStartedAt,
		),
		span(
			"execution",
			TRACE_SPAN_LABELS.execution,
			"From when its execution started to when the review finished",
			executionStartedAt,
			to,
		),
	];
}

export interface FeedbackLoss {
	title: string;
	text: string;
	short: string;
	echo?: string;
	/**
	 * How much the loss asks of an admin: red where sending failed, amber where timing cost it (the
	 * work moved on before the comment was ready), and neutral where it was the review, the developer
	 * or an admin deciding, as the overview splits withheld feedback.
	 */
	tone: LossTone;
}

type LossTone = "destructive" | "warning" | "secondary";

/** Why a piece of feedback did not reach the developer or no longer stands; undefined while it stands or waits. */
export function feedbackLoss(
	feedback: ReviewLineageFeedback,
	lineage: ReviewLineage,
	job?: Pick<AgentJob, "workMergedAt">,
): FeedbackLoss | undefined {
	if (feedback.withdrawnAt !== undefined) {
		return {
			title: "Withdrawn",
			// A prepared piece can be withdrawn too: only a recorded delivery says it was delivered.
			text: reachedDeveloper(feedback)
				? "An admin withdrew it after it was delivered."
				: "An admin withdrew it before it reached the developer.",
			short: "withdrawn by an admin",
			tone: "secondary",
		};
	}
	switch (feedback.deliveryState) {
		case "DISCARDED": {
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
		case "PARTIALLY_FAILED": {
			return {
				title: "Partly failed",
				text: "Only part of it was delivered; sending the rest failed.",
				short: "partly failed",
				tone: "destructive",
			};
		}
		case "SUPERSEDED": {
			// Replaced after it reached the developer is the ordinary course of a re-review.
			if (feedback.deliveredAt !== undefined) {
				return undefined;
			}
			return {
				title: "Replaced",
				text: "Newer feedback replaced it.",
				short: "newer feedback took its place",
				tone: "secondary",
			};
		}
		case "SUPPRESSED":
		case "PARTIALLY_DELIVERED": {
			const reason = feedback.suppressionReason;
			if (reason === undefined) {
				return feedback.deliveryState === "SUPPRESSED"
					? {
							title: "Withheld",
							text: "It was withheld; the reason is not recorded.",
							short: "withheld",
							tone: "warning",
						}
					: undefined;
			}
			const partly = feedback.deliveryState === "PARTIALLY_DELIVERED";
			return {
				title: partly ? "Partly withheld" : "Withheld",
				text: `${partly ? "Part of it was delivered, the rest was withheld. " : ""}${withheldText(feedback, lineage, job)}`,
				short:
					reason === "ARTIFACT_MERGED"
						? (mergeWhere(feedback, lineage, job) ?? shortReason(reason))
						: shortReason(reason),
				echo: reason === "ARTIFACT_MERGED" ? "merged" : undefined,
				tone: withheldClass(reason) === "TIMING" ? "warning" : "secondary",
			};
		}
		case "AWAITING_APPROVAL":
		case "PREPARED":
		case "DELIVERED":
		case "UNCONFIRMED": {
			return undefined;
		}
	}
}

/** Where in the review's run the work merged, as a clause: "merged while the review ran". */
const MERGE_PLACES = {
	startedReview: "the merge started this review",
	beforeRequest: "merged before the review was asked for",
	inQueue: "merged while the review waited in the queue",
	whileRunning: "merged while the review ran",
	afterFinish: "merged after the review finished",
} as const;

interface MergeTiming {
	/** When the work merged; absent when only the start of the review says the merge came first. */
	merged?: Date;
	/** Where in the review's run; absent when the review is not known. */
	where?: keyof typeof MERGE_PLACES;
}

/**
 * When the work merged against a piece withheld as already merged: the one answer the bottleneck
 * line and the verdict both tell. Undefined when the merge time is not known or came after the
 * piece was ready.
 */
export function mergeTiming(
	feedback: ReviewLineageFeedback,
	lineage: ReviewLineage,
	job?: Pick<AgentJob, "workMergedAt">,
): MergeTiming | undefined {
	const merged = mergedAtOf(lineage, job);
	if (
		lineage.reviewTrigger === "MERGED" ||
		lineage.workMoments.some((moment) => moment.startedReview && moment.kind === "MERGED")
	) {
		return { merged, where: "startedReview" };
	}
	if (merged === undefined || merged.getTime() > feedback.createdAt.getTime()) {
		return undefined;
	}
	const { review } = lineage;
	if (review === undefined) {
		return { merged };
	}
	if (merged.getTime() < review.createdAt.getTime()) {
		return { merged, where: "beforeRequest" };
	}
	if (review.startedAt === undefined || merged.getTime() < review.startedAt.getTime()) {
		return { merged, where: "inQueue" };
	}
	// The comments are written after the review finishes, so the work can merge in between.
	if (review.finishedAt !== undefined && merged.getTime() > review.finishedAt.getTime()) {
		return { merged, where: "afterFinish" };
	}
	return { merged, where: "whileRunning" };
}

/**
 * The reason a piece was withheld, said plainly. A piece withheld as already merged also says when
 * the work merged against the review, since that is where the time was lost: before the review was
 * asked for, in the queue, while it ran, or after it finished.
 */
export function withheldText(
	feedback: ReviewLineageFeedback,
	lineage: ReviewLineage,
	job?: Pick<AgentJob, "workMergedAt">,
): string {
	const reason = feedback.suppressionReason;
	if (reason === undefined) {
		return "It was withheld; the reason is not recorded.";
	}
	const timing = reason === "ARTIFACT_MERGED" ? mergeTiming(feedback, lineage, job) : undefined;
	if (timing?.where === "startedReview") {
		return "The merge started this review, so a comment on the work would always come too late.";
	}
	if (timing?.merged !== undefined) {
		const before = `The work was merged ${durationBetween(timing.merged, feedback.createdAt)} before the comment was ready`;
		return timing.where === undefined
			? `${before}.`
			: `${before}: it ${MERGE_PLACES[timing.where]}.`;
	}
	return withholdingReasonSentence(reason);
}

/**
 * Where in the review's run the work merged, before a piece withheld as already merged was ready:
 * "merged while the review ran". Undefined where the merge time or the review is not known.
 */
function mergeWhere(
	feedback: ReviewLineageFeedback,
	lineage: ReviewLineage,
	job?: Pick<AgentJob, "workMergedAt">,
): string | undefined {
	const where = mergeTiming(feedback, lineage, job)?.where;
	return where === undefined ? undefined : MERGE_PLACES[where];
}

/** "already merged (2), settings changed (1)": the reasons of several lost pieces, most first. */
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
 * failure is red, a loss to timing amber, and a piece the review, the developer or an admin held
 * back is neutral, since nothing went wrong.
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

/** A not-met observation the review told nobody about: worth a look, never a failure. */
const NOT_TOLD = { tone: "warning", icon: EyeOffIcon } as const;

type BottleneckJob = Pick<
	AgentJob,
	| "errorMessage"
	| "cancellationReason"
	| "retryCount"
	| "workMergedAt"
	| "firstFeedbackReadAt"
	| "executionStartedAt"
>;

/** A review that stopped, is held or still runs is its own bottleneck; a completed one is not. */
function reviewBottleneck(
	review: NonNullable<ReviewLineage["review"]>,
	job: BottleneckJob | undefined,
	now: Date,
): TraceBottleneck | undefined {
	const retries =
		job !== undefined && job.retryCount > 0
			? ` It was tried ${countOf(job.retryCount + 1, "time", "times")}.`
			: "";
	const error = hasText(job?.errorMessage)
		? job.errorMessage.trim().replace(/\.?$/u, ".")
		: undefined;
	switch (review.phase) {
		case "FAILED": {
			return {
				kind: "failed",
				stage: "review",
				title: "Review failed",
				text: `${error ?? "It stopped on an error; the error is not recorded here."}${retries}`,
				tone: "destructive",
				icon: CircleAlertIcon,
			};
		}
		case "TIMED_OUT": {
			return {
				kind: "failed",
				stage: "review",
				title: "Review timed out",
				text: `${error ?? "It ran past the time it is allowed and was stopped."}${retries}`,
				tone: "destructive",
				icon: TimerOffIcon,
			};
		}
		case "CANCELLED": {
			const reason = job?.cancellationReason;
			return {
				kind: "failed",
				stage: "review",
				title: "Review canceled",
				text:
					reason === undefined
						? (error ?? "Somebody stopped it before it finished.")
						: REVIEW_CANCELLATION_DEFS[reason].description,
				tone: "outline",
				icon: CircleSlashIcon,
			};
		}
		case "QUEUED": {
			return queuedBottleneck(review, now);
		}
		case "RUNNING": {
			return {
				kind: "waiting",
				stage: "review",
				title: "Running",
				text: `It has run ${durationBetween(review.startedAt ?? review.createdAt, now)} so far.`,
				tone: "secondary",
				icon: HourglassIcon,
				span: "run",
			};
		}
		case "COMPLETED": {
			return undefined;
		}
	}
}

function queuedBottleneck(
	review: NonNullable<ReviewLineage["review"]>,
	now: Date,
): TraceBottleneck {
	if (hasText(review.holdReason)) {
		const hold = holdReasonCopy(review.holdReason);
		return {
			kind: "waiting",
			stage: "review",
			title: "On hold",
			text: `${hold.label}, for ${durationBetween(review.createdAt, now)} so far. ${hold.detail}`,
			tone: "warning",
			icon: PauseIcon,
			span: "queue",
		};
	}
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

/** The not-met observations on the path that led to no feedback at all. */
function silentOnPath(lineage: ReviewLineage, here: TraceNode | undefined) {
	if (here?.kind === "feedback") {
		return [];
	}
	const silent = silentNotMet(lineage);
	return here?.kind === "observation" ? silent.filter((entry) => entry.id === here.id) : silent;
}

/** What of the path's feedback was lost: one piece, all of them, or some. */
function lossBottleneck(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	job: BottleneckJob | undefined,
): TraceBottleneck | undefined {
	const pieces = feedbackOnPath(lineage, here);
	const losses = pieces
		.map((feedback) => feedbackLoss(feedback, lineage, job))
		.filter((loss) => loss !== undefined);
	const reached = pieces.filter(reachedDeveloper).length;
	if (losses.length === 0) {
		const silent = silentOnPath(lineage, here);
		if (pieces.length > 0 || silent.length === 0) {
			return undefined;
		}
		return {
			kind: "lost",
			stage: "review",
			title: "Not told",
			text: `${silent.length === 1 ? "One observation was" : `${silent.length} observations were`} not met, but the review composed no feedback from ${silent.length === 1 ? "it" : "them"}, so the developer was not told.`,
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
			echo: first.echo,
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

/** What a path still on its way waits on: an admin's approval, or a conversation that has not come. */
function waitBottleneck(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	now: Date,
): TraceBottleneck | undefined {
	const pieces = feedbackOnPath(lineage, here);
	const waiting = pieces.find((entry) => entry.deliveryState === "AWAITING_APPROVAL");
	if (waiting !== undefined) {
		return {
			kind: "waiting",
			stage: "feedback",
			title: "Awaiting approval",
			text: `It has waited ${durationBetween(waiting.createdAt, now)} for an admin to approve it.`,
			tone: "warning",
			icon: HourglassIcon,
		};
	}
	const chat = pieces.find(
		(entry) => entry.deliveryState === "PREPARED" && entry.channel === "IN_CHAT",
	);
	const reached = pieces.some(reachedDeveloper);
	if (chat !== undefined && !reached) {
		return {
			kind: "waiting",
			stage: "feedback",
			title: "Waiting for a conversation",
			text: `Prepared ${durationBetween(chat.createdAt, now)} ago; it reaches the developer only when they next talk with Heph.`,
			tone: "secondary",
			icon: HourglassIcon,
		};
	}
	const prepared = pieces.find(
		(entry) => entry.deliveryState === "PREPARED" || entry.deliveryState === "PARTIALLY_DELIVERED",
	);
	if (prepared !== undefined && !reached) {
		return {
			kind: "waiting",
			stage: "feedback",
			title: "Not sent yet",
			text: `Prepared ${durationBetween(prepared.createdAt, now)} ago and not delivered yet.`,
			tone: "secondary",
			icon: HourglassIcon,
		};
	}
	return undefined;
}

const SPAN_STAGE: Record<TraceSpanKind, BottleneckStage> = {
	asked: "trigger",
	queue: "review",
	preparation: "review",
	execution: "review",
	run: "review",
	delivery: "feedback",
	read: "effect",
};

function spanLength(entry: TraceSpan): number {
	return entry.to.getTime() - entry.from.getTime();
}

/** The longest wait of half an hour or more, and whether the path's card is still unopened. */
function timeBottleneck(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	job: BottleneckJob | undefined,
): TraceBottleneck {
	const pieces = feedbackOnPath(lineage, here);
	const reached = pieces.filter(reachedDeveloper);
	const [slowest] = traceSpans(lineage, here, job)
		.filter((entry) => entry.slow && entry.kind !== "read")
		.sort((a, b) => spanLength(b) - spanLength(a));
	const unread =
		job !== undefined &&
		job.firstFeedbackReadAt === undefined &&
		reached.some((entry) => entry.channel === "IN_APP" && entry.deliveryState === "DELIVERED");
	if (slowest !== undefined) {
		const note = unread ? " Its card on the practice pages is not opened yet." : "";
		return {
			kind: "slow",
			stage: SPAN_STAGE[slowest.kind],
			title: SLOW_TITLE[slowest.kind],
			text: `${SLOW_TEXT[slowest.kind](slowest.text)}${note}`,
			tone: "warning",
			icon: ClockIcon,
			span: slowest.kind,
		};
	}
	if (unread) {
		return {
			kind: "waiting",
			stage: "effect",
			title: "Not opened yet",
			text: "It is on the developer’s practice pages, but no developer has opened feedback of this review yet.",
			tone: "secondary",
			icon: EyeOffIcon,
		};
	}
	return {
		kind: "none",
		stage: "effect",
		title: "No bottleneck",
		text: noneText(pieces.length, reached.length),
		// The ordinary case takes no colour, so the ones that do stand out.
		tone: "secondary",
		icon: CircleCheckIcon,
	};
}

/**
 * The bottleneck of the path the trace stands on, read from the chain and, where the page has it,
 * the review's own record: its error, its cancellation, its merge time and its first read. It names
 * the first thing that stopped the path: the review itself, then lost feedback; where nothing stopped
 * it, what it still waits on, else the longest wait.
 */
export function traceBottleneck(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	job?: BottleneckJob,
	now: Date = new Date(),
): TraceBottleneck {
	const { review } = lineage;
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
		reviewBottleneck(review, job, now) ??
		lossBottleneck(lineage, here, job) ??
		waitBottleneck(lineage, here, now) ??
		timeBottleneck(lineage, here, job)
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

const SLOW_TITLE: Record<TraceSpanKind, string> = {
	asked: "Slow start",
	queue: "Slow queue",
	preparation: "Slow preparation",
	execution: "Slow execution",
	run: "Slow run",
	delivery: "Slow delivery",
	read: "Read late",
};

const SLOW_TEXT: Record<TraceSpanKind, (duration: string) => string> = {
	asked: (duration) => `The review was asked for ${duration} after the moment that started it.`,
	queue: (duration) => `The review waited ${duration} in the queue for a worker.`,
	preparation: (duration) => `The review prepared its evidence for ${duration}.`,
	execution: (duration) => `The review's execution took ${duration}.`,
	run: (duration) => `The review ran for ${duration}.`,
	delivery: (duration) => `Its feedback reached the developer ${duration} after it was composed.`,
	read: (duration) => `It was first read ${duration} after it was delivered.`,
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
	const told =
		plain(verdict).includes(plain(bottleneck.text)) ||
		(bottleneck.echo !== undefined && plain(verdict).includes(bottleneck.echo));
	if (bottleneck.short !== undefined && told) {
		return bottleneck.short;
	}
	return bottleneck.text;
}
