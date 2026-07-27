import type { AgentJob } from "@/api/types.gen";
import { asDate } from "@/lib/dates";

export type JobStatus = AgentJob["status"];
export type DeliveryStatus = NonNullable<AgentJob["deliveryStatus"]>;

export const STATUS_LABELS: Record<JobStatus, string> = {
	QUEUED: "Queued",
	RUNNING: "Running",
	COMPLETED: "Completed",
	FAILED: "Failed",
	TIMED_OUT: "Timed out",
	CANCELLED: "Cancelled",
};

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
	PENDING: "Pending",
	DELIVERED: "Delivered",
	FAILED: "Failed",
};

export function statusBadgeVariant(
	status: JobStatus,
): "default" | "secondary" | "destructive" | "outline" {
	switch (status) {
		case "COMPLETED":
			return "default";
		case "RUNNING":
		case "QUEUED":
			return "secondary";
		case "FAILED":
		case "TIMED_OUT":
			return "destructive";
		case "CANCELLED":
			return "outline";
	}
}

export function deliveryBadgeVariant(
	status: DeliveryStatus,
): "default" | "secondary" | "destructive" {
	switch (status) {
		case "DELIVERED":
			return "default";
		case "PENDING":
			return "secondary";
		case "FAILED":
			return "destructive";
	}
}

/**
 * Why a queued run is sitting on the clock rather than waiting for a free worker — a `hold` an admin
 * can lift, or an ordinary retry `backoff` after a crash.
 */
export type JobWait = { kind: "hold"; reason: string } | { kind: "backoff" };

/**
 * `availableAt` is required and already in the past for almost every run, so printing it everywhere
 * would be noise on every row. These two cases are the only ones where it says something; `null`
 * means the run is claimable now or has already left the queue, and the timestamp stays hidden.
 *
 * A hold is keyed off `holdReason` alone, not the clock: the server re-parks a still-capped run each
 * time its `availableAt` lapses, so a hold whose instant has passed is held all the same.
 */
export function jobWait(
	job: Pick<AgentJob, "status" | "holdReason" | "availableAt">,
	now: number = Date.now(),
): JobWait | null {
	if (job.status !== "QUEUED") return null;
	if (job.holdReason) return { kind: "hold", reason: job.holdReason };
	const availableAt = asDate(job.availableAt);
	return availableAt && availableAt.getTime() > now ? { kind: "backoff" } : null;
}

export interface HoldReasonCopy {
	/** Phrase for the runs table, where the column is narrow. */
	label: string;
	/** Sentence for the details panel. Never says "failed": a hold is a wait that ends by itself. */
	detail: string;
}

/**
 * `holdReason` is a plain string on the wire and the server may add reasons, so an unknown one still
 * has to read as English. Same shape as `eventLabel` on the audit table: a map for what we know,
 * humanised underscores for what we don't.
 */
const HOLD_REASON_COPY: Record<string, HoldReasonCopy | undefined> = {
	BUDGET: {
		label: "Over the AI budget",
		detail:
			"The monthly AI cap is spent, so this run is parked rather than failed. It resumes on its own once the cap is raised or the month rolls over. AI usage names which purse is capped and who can lift it.",
	},
};

const UNKNOWN_HOLD_DETAIL =
	"This run is parked rather than failed. It resumes on its own once the hold lifts.";

export function holdReasonCopy(reason: string): HoldReasonCopy {
	const known = HOLD_REASON_COPY[reason];
	if (known) return known;
	const lower = reason.replace(/_/g, " ").toLowerCase();
	return {
		label: lower.charAt(0).toUpperCase() + lower.slice(1),
		detail: UNKNOWN_HOLD_DETAIL,
	};
}

export function isCancellable(status: JobStatus): boolean {
	return status === "QUEUED" || status === "RUNNING";
}

export function isDeliveryRetryable(job: Pick<AgentJob, "status" | "deliveryStatus">): boolean {
	return job.status === "COMPLETED" && job.deliveryStatus === "FAILED";
}

export function formatTokens(value: number | undefined): string {
	if (value == null) return "—";
	return value.toLocaleString();
}

/**
 * A money figure in a right-aligned column, padded so its decimal point lands where every other row's
 * does. `tabular-nums` equalises glyph *width* but not a missing `.00`, and the copy for `$0` and
 * `<$0.01` is fixed, so the fix is layout: `visibility: hidden` keeps the space that `display: none`
 * would collapse, and `aria-hidden` keeps it out of the accessible name. Not for headlines.
 */
export function MoneyCell({ children }: { children: string }) {
	return (
		<>
			{children}
			{!children.includes(".") && (
				<span className="invisible" aria-hidden>
					.00
				</span>
			)}
		</>
	);
}

/** The submit-time snapshot, not the runner-reported `llmModel`, which exists only once it has run. */
export function modelLabel(job: Pick<AgentJob, "model">): string {
	return job.model ?? "—";
}
