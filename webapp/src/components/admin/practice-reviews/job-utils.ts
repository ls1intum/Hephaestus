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
	retried: boolean;
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

export function jobPhases(
	job: Pick<ReviewJob, "status" | "availableAt" | "startedAt" | "completedAt" | "attempt">,
): JobPhases {
	const retried = job.attempt > 1;
	if (job.status === "QUEUED") {
		return { wait: IN_PROGRESS, execution: NOT_STARTED, retried };
	}
	const started = asDate(job.startedAt);
	const wait = elapsed(asDate(job.availableAt), started);
	const execution =
		job.status === "RUNNING" ? IN_PROGRESS : elapsed(started, asDate(job.completedAt));
	return { wait, execution, retried };
}
