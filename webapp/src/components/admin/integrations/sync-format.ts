import { formatDistanceToNow } from "date-fns";
import type { ConnectionSyncStatus, SyncJob } from "@/api/types.gen";

/**
 * Adaptive poll cadence for sync status/resources/jobs queries: refetch every 5s while a job is
 * running (live progress) and back off to 60s when idle. SSE hint invalidation is the primary live
 * channel; this polling is the fallback when SSE is unavailable.
 */
export function syncPollInterval(hasActiveJob: boolean): number {
	return hasActiveJob ? 5_000 : 60_000;
}

export function asDate(value: Date | string | undefined | null): Date | undefined {
	if (value == null) return undefined;
	const date = value instanceof Date ? value : new Date(value);
	return Number.isNaN(date.getTime()) ? undefined : date;
}

/**
 * Human "5 minutes ago" phrasing for a timestamp. A missing/invalid value renders the {@link fallback}
 * dash — never "now" — so an absent timestamp can't masquerade as a fresh one.
 */
export function relativeTime(value: Date | string | undefined | null, fallback = "–"): string {
	const date = asDate(value);
	return date ? formatDistanceToNow(date, { addSuffix: true }) : fallback;
}

export type ConnectionHealth = ConnectionSyncStatus["health"];

export const HEALTH_LABEL: Record<ConnectionHealth, string> = {
	PENDING: "Pending",
	HEALTHY: "Healthy",
	DEGRADED: "Degraded",
	FAILED: "Failed",
	SUSPENDED: "Suspended",
};

export const JOB_STATUS_LABEL: Record<SyncJob["status"], string> = {
	PENDING: "Pending",
	RUNNING: "Running",
	SUCCEEDED: "Succeeded",
	SUCCEEDED_WITH_WARNINGS: "Succeeded with warnings",
	FAILED: "Failed",
	CANCELLED: "Cancelled",
};

/**
 * Both `SyncResourceState.state` and `BackfillSummary.state` are free-form, integration-defined
 * strings on the wire, so title-case whatever arrives rather than render a raw SCREAMING_SNAKE token:
 * `IN_PROGRESS` → "In Progress". There is deliberately no lookup table — each integration owns its own
 * vocabulary, so a table here could only drift out of date and fall back to this same rule anyway.
 */
export function stateLabel(state: string): string {
	return state
		.toLowerCase()
		.split(/[\s_]+/)
		.filter(Boolean)
		.map((word) => word.charAt(0).toUpperCase() + word.slice(1))
		.join(" ");
}

export const JOB_TYPE_LABEL: Record<SyncJob["type"], string> = {
	INITIAL: "Initial sync",
	RECONCILIATION: "Reconciliation",
	BACKFILL: "Backfill",
};

export const JOB_TRIGGER_LABEL: Record<SyncJob["trigger"], string> = {
	SCHEDULED: "Scheduled",
	MANUAL: "Manual",
	LIFECYCLE: "Lifecycle",
	SYSTEM: "System",
};

export function jobCurrentStep(job: Pick<SyncJob, "progress">): string | undefined {
	const step = job.progress?.currentStep;
	return typeof step === "string" && step.length > 0 ? step : undefined;
}
