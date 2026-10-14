import { formatDistanceStrict } from "date-fns";
import type { ReviewJob, ReviewSummary } from "@/api/types.gen";
import { asDate } from "@/lib/dates";
import { ITEM_KIND } from "@/lib/item-kinds";
import { hasText } from "@/lib/text";

export type JobWait = { kind: "backoff" };

/**
 * `null` for a job that is simply claimable: `availableAt` is in the past for almost every job, so a
 * "due …" line on every queued row would be noise.
 */
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

/** A queued job held because the monthly AI budget is used up (`heldForBudget`). */
export const BUDGET_HOLD: HoldReasonCopy = {
	label: "Over the AI budget",
	detail:
		"The monthly AI budget is used up, so this review is waiting, not failed. It continues on its own when the budget is raised or the next month starts. AI usage shows which budget is used up and who can raise it.",
};

/** Cancelling stops every job of the review that still waits or runs. */
export function isCancellable(status: ReviewSummary["status"]): boolean {
	return status === "QUEUED" || status === "RUNNING";
}

/**
 * The kinds the request endpoint accepts. A conversation and a document are reviewed on the event
 * their source produces, and asking for one by hand is refused. Nothing on the wire says which kinds
 * accept a request; being wrong in this direction costs a missing button rather than a broken one.
 */
export const REVIEWABLE_ON_DEMAND: ReadonlySet<string> = new Set([
	ITEM_KIND.pullRequest,
	ITEM_KIND.issue,
]);

export interface JobPhases {
	wait: string;
	execution: string;
	/** The wait then runs from this attempt's availability, after every earlier attempt. */
	retried: boolean;
}

const IN_PROGRESS = "In progress";
const NOT_STARTED = "Not started";
const NOT_RECORDED = "Not recorded";

/** `undefined` when the boundary was never recorded, `null` when it was but cannot be read. */
function instant(value: Parameters<typeof asDate>[0]): Date | null | undefined {
	return value == null ? undefined : (asDate(value) ?? null);
}

function elapsed(from: Date | null | undefined, to: Date | null | undefined): string {
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
 * The current attempt's phases between the boundaries the job records: when it became available
 * (`availableAt`), its claim (`startedAt`) and its end. A live phase is in progress, never a figure; a
 * missing, unreadable or out-of-order boundary is not recorded, and so is every phase that depends on
 * it.
 */
export function jobPhases(
	job: Pick<ReviewJob, "status" | "availableAt" | "startedAt" | "completedAt" | "attempt">,
): JobPhases {
	const retried = job.attempt > 1;
	if (job.status === "QUEUED") {
		return { wait: IN_PROGRESS, execution: NOT_STARTED, retried };
	}
	const started = instant(job.startedAt);
	const wait = elapsed(instant(job.availableAt), started);
	const execution =
		job.status === "RUNNING" ? IN_PROGRESS : elapsed(started, instant(job.completedAt));
	return { wait, execution, retried };
}
