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

export function formatCostUsd(value: number | undefined): string {
	if (value == null) return "—";
	return `$${value.toFixed(value < 1 ? 3 : 2)}`;
}

export function configLabel(job: Pick<AgentJob, "configName" | "configId">): string {
	return job.configName ?? (job.configId != null ? `#${job.configId}` : "—");
}
