import { formatDistanceStrict } from "date-fns";
import type { ReviewJob } from "@/api/types.gen";
import { asDate } from "@/lib/dates";
import { ITEM_KIND } from "@/lib/item-kinds";

export const REVIEWABLE_ON_DEMAND: ReadonlySet<string> = new Set([
	ITEM_KIND.pullRequest,
	ITEM_KIND.issue,
]);

export interface JobPhases {
	wait: string;
	execution: string;
}

const IN_PROGRESS = "In progress";
const NOT_STARTED = "Not started";
const NOT_RECORDED = "Not recorded";

/** The last recorded worker invocation ended; future scan work is not known here. */
export function invocationCompletedAt(
	jobs: readonly Pick<ReviewJob, "status" | "completedAt">[],
): Date | undefined {
	if (
		jobs.length === 0 ||
		jobs.some((job) => job.status === "QUEUED" || job.status === "RUNNING")
	) {
		return undefined;
	}
	const completed = jobs.map((job) => asDate(job.completedAt)).filter((at) => at !== undefined);
	return completed.length === jobs.length
		? new Date(Math.max(...completed.map((at) => at.getTime())))
		: undefined;
}

/**
 * When the review's first attempt started, while that is still known. Attempts count from 0, and a
 * later attempt reuses its job and replaces the job's start, so once any job is past its first
 * attempt the first start is gone. An earlier ledger, capture or trace time is a different event.
 */
export function firstStartedAt(
	jobs: readonly Pick<ReviewJob, "attempt" | "startedAt">[],
): Date | undefined {
	if (jobs.some((job) => job.attempt > 0)) {
		return undefined;
	}
	const started = jobs.map((job) => asDate(job.startedAt)).filter((at) => at !== undefined);
	return started.length === 0
		? undefined
		: new Date(Math.min(...started.map((at) => at.getTime())));
}

function elapsed(from: Date | undefined, to: Date | undefined): string {
	if (!from || !to || to.getTime() < from.getTime()) {
		return NOT_RECORDED;
	}
	const ms = to.getTime() - from.getTime();
	if (ms > 0 && ms < 1000) {
		return "Under 1 s";
	}
	return formatDistanceStrict(to, from, { roundingMethod: "floor" });
}

/**
 * The latest attempt, counted from 1, of a review carried by one job that went past its first.
 * The job's attempt is the slot it reached, not how many times it ran, and several jobs share no one
 * latest attempt.
 */
export function latestAttempt(jobs: readonly Pick<ReviewJob, "attempt">[]): number | undefined {
	const [only] = jobs;
	return jobs.length === 1 && only !== undefined && only.attempt > 0 ? only.attempt + 1 : undefined;
}

/** The job's current attempt only: a later attempt replaces the job's availability and start. */
export function jobPhases(
	job: Pick<ReviewJob, "status" | "availableAt" | "startedAt" | "completedAt">,
): JobPhases {
	if (job.status === "QUEUED") {
		return { wait: IN_PROGRESS, execution: NOT_STARTED };
	}
	const started = asDate(job.startedAt);
	const wait = elapsed(asDate(job.availableAt), started);
	const execution =
		job.status === "RUNNING" ? IN_PROGRESS : elapsed(started, asDate(job.completedAt));
	return { wait, execution };
}
