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
