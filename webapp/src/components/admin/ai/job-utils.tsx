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
 * A money figure in a right-aligned column, padded so its decimal point lands where every other
 * row's does.
 *
 * `tabular-nums` equalises glyph *width*; it does nothing about a missing `.00`, so `$0`, `<$0.01`
 * and `$4.50` right-align with their decimal points three different distances from the edge. The
 * copy for `$0` and `<$0.01` is fixed (`docs/contributor/llm-cost-vocabulary.md`, rule 5), so the
 * fix is layout: reserve the width of the cents that string doesn't have. `visibility: hidden`
 * keeps the space (unlike `display: none`) and `aria-hidden` keeps it out of the accessible name.
 *
 * Headlines are not columns — don't wrap them.
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

/**
 * The model a job ran on, read from its frozen submit-time snapshot. Populated from submission
 * onward, unlike the runner-reported `llmModel`, which only exists once the job has actually run.
 */
export function modelLabel(job: Pick<AgentJob, "model">): string {
	return job.model ?? "—";
}
