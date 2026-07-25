import type { AgentJob } from "@/api/types.gen";

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

const USD = new Intl.NumberFormat("en-US", {
	style: "currency",
	currency: "USD",
	minimumFractionDigits: 2,
	maximumFractionDigits: 2,
});

const USD_WHOLE = new Intl.NumberFormat("en-US", {
	style: "currency",
	currency: "USD",
	minimumFractionDigits: 0,
	maximumFractionDigits: 0,
});

/**
 * An amount of money that was spent.
 *
 * <p>Three cases, because reading spend at a glance is the whole job of these tables:
 * nothing spent reads as `$0` — not `$0.000`, which looks like a broken decimal; an amount too
 * small to show in cents reads as `<$0.01`, which is honest about being nonzero where rounding to
 * `$0.00` would claim the opposite; everything else is plain cents.
 */
export function formatCostUsd(value: number | undefined): string {
	if (value == null) return "—";
	if (value === 0) return "$0";
	if (value > 0 && value < 0.005) return "<$0.01";
	return USD.format(value);
}

/**
 * A cap someone typed, rendered the way they typed it: `$50`, not `$50.00`. Cents appear only when
 * the cap actually has them, so a round number stays scannable next to the spend it bounds.
 */
export function formatCapUsd(value: number | undefined): string {
	if (value == null) return "—";
	// Cents are all-or-nothing: "$49.50", never "$49.5". Intl's maximumFractionDigits would happily
	// emit a single decimal, which reads as a typo in a column of money.
	return Number.isInteger(value) ? USD_WHOLE.format(value) : USD.format(value);
}

/**
 * The model a job ran on, read from its frozen submit-time snapshot. Populated from submission
 * onward, unlike the runner-reported `llmModel`, which only exists once the job has actually run.
 */
export function modelLabel(job: Pick<AgentJob, "model">): string {
	return job.model ?? "—";
}
