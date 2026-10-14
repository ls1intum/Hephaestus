import { formatDistanceStrict } from "date-fns";
import type { ReviewJob, ReviewSummary } from "@/api/types.gen";
import { asDate } from "@/lib/dates";
import { ITEM_KIND } from "@/lib/item-kinds";

export interface JobWait {
	kind: "backoff";
}

export function jobWait(
	job: Pick<ReviewJob, "status" | "availableAt">,
	now: number,
): JobWait | null {
	if (job.status !== "QUEUED") {
		return null;
	}
	const availableAt = asDate(job.availableAt);
	return availableAt && availableAt.getTime() > now ? { kind: "backoff" } : null;
}

export interface HoldReasonCopy {
	label: string;
	/** Sentence for the details panel. Never says "failed": a hold is a wait that ends by itself. */
	detail: string;
}

export const BUDGET_HOLD: HoldReasonCopy = {
	label: "Over the AI budget",
	detail:
		"The monthly AI budget is used up, so this review is waiting, not failed. It continues on its own when the budget is raised or the next month starts. AI usage shows which budget is used up and who can raise it.",
};

export function isCancellable(status: ReviewSummary["status"]): boolean {
	return status === "QUEUED" || status === "RUNNING";
}

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
