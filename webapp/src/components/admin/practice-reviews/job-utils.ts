import { formatDistanceStrict } from "date-fns";
import type { AgentJob } from "@/api/types.gen";
import { asDate } from "@/lib/dates";
import { hasText } from "@/lib/text";

export type JobWait = { kind: "hold"; reason: string } | { kind: "backoff" };

/**
 * `null` for a run that is simply claimable: `availableAt` is in the past for almost every run, so a
 * "due …" line on every queued row would be noise. A hold is keyed off `holdReason` alone, not the
 * clock — the server re-parks a still-capped run each time its `availableAt` lapses.
 */
export function jobWait(
	job: Pick<AgentJob, "status" | "holdReason" | "availableAt">,
	now: number,
): JobWait | null {
	if (job.status !== "QUEUED") {
		return null;
	}
	if (hasText(job.holdReason)) {
		return { kind: "hold", reason: job.holdReason };
	}
	const availableAt = asDate(job.availableAt);
	return availableAt && availableAt.getTime() > now ? { kind: "backoff" } : null;
}

export interface HoldReasonCopy {
	label: string;
	/** Sentence for the details panel. Never says "failed": a hold is a wait that ends by itself. */
	detail: string;
}

/**
 * `holdReason` is a plain string on the wire and the server may add reasons, so an unknown one reads
 * as a plain "On hold" rather than as its constant.
 */
const HOLD_REASON_COPY: Record<string, HoldReasonCopy | undefined> = {
	BUDGET: {
		label: "Over the AI budget",
		detail:
			"The monthly AI budget is used up, so this review is waiting, not failed. It continues on its own when the budget is raised or the next month starts. AI usage shows which budget is used up and who can raise it.",
	},
};

const UNKNOWN_HOLD_DETAIL =
	"This review is waiting, not failed. It continues on its own when the hold ends.";

export function holdReasonCopy(reason: string): HoldReasonCopy {
	const known = HOLD_REASON_COPY[reason];
	if (known) {
		return known;
	}
	return { label: "On hold", detail: UNKNOWN_HOLD_DETAIL };
}

export function isCancellable(status: AgentJob["status"]): boolean {
	return status === "QUEUED" || status === "RUNNING";
}

export function isResultProcessingRetryable(
	job: Pick<AgentJob, "status" | "deliveryStatus">,
): boolean {
	return job.status === "COMPLETED" && job.deliveryStatus === "FAILED";
}

export interface JobPhases {
	wait: string;
	preparation: string;
	execution: string;
	/** The wait then runs from the request, through every earlier attempt and retry wait. */
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
 * The current attempt's phases between the boundaries the job records: its claim (`startedAt`), the
 * moment before the sandbox is invoked (`executionStartedAt`) and its end. A retry clears the first two.
 * A live phase is in progress, never a figure; a missing, unreadable or out-of-order boundary is not
 * recorded, and so is every phase that depends on it.
 */
export function jobPhases(
	job: Pick<
		AgentJob,
		"status" | "createdAt" | "startedAt" | "executionStartedAt" | "completedAt" | "retryCount"
	>,
): JobPhases {
	const retried = job.retryCount > 0;
	if (job.status === "QUEUED") {
		return { wait: IN_PROGRESS, preparation: NOT_STARTED, execution: NOT_STARTED, retried };
	}
	const started = instant(job.startedAt);
	const executing = instant(job.executionStartedAt);
	const wait = elapsed(instant(job.createdAt), started);
	if (job.status === "RUNNING" && started && executing === undefined) {
		return { wait, preparation: IN_PROGRESS, execution: NOT_STARTED, retried };
	}
	const preparation = elapsed(started, executing);
	let execution = NOT_RECORDED;
	if (preparation !== NOT_RECORDED) {
		execution =
			job.status === "RUNNING" ? IN_PROGRESS : elapsed(executing, instant(job.completedAt));
	}
	return { wait, preparation, execution, retried };
}

/** The submit-time snapshot, not the runner-reported `llmModel`, which exists only once it has run. */
export function modelLabel(job: Pick<AgentJob, "model">): string {
	return job.model ?? "—";
}
