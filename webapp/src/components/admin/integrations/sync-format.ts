import type { ConnectionSyncStatus, SyncJob } from "@/api/types.gen";

/**
 * The API's `Date` fields carry `Date` types in generated TS, but this repo does not wire hey-api's
 * date transformers — at runtime they arrive as ISO strings. Normalize both shapes and drop anything
 * unparseable. Shared by every sync-observability surface (see `OutlineConnectCard`'s original of
 * the same name, which this supersedes for the new Integrations section).
 */
export function asDate(value: Date | string | undefined | null): Date | undefined {
	if (value == null) return undefined;
	const date = value instanceof Date ? value : new Date(value);
	return Number.isNaN(date.getTime()) ? undefined : date;
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
 * Humanizes the provider-defined {@link SyncResourceState.state}. That field is an open string
 * (providers are being unified toward `"SYNCED"`/`"PENDING"`), so this maps the known values and
 * title-cases anything unmapped rather than leaking raw ALL_CAPS enum casing or crashing on a new
 * provider string.
 */
const RESOURCE_STATE_LABEL: Record<string, string> = {
	PENDING: "Pending",
	SYNCED: "Synced",
	SYNCING: "Syncing",
	BACKFILLING: "Backfilling",
	ACTIVE: "Active",
	ERROR: "Error",
	FAILED: "Failed",
	PAUSED: "Paused",
	SUSPENDED: "Suspended",
	DISABLED: "Disabled",
};

export function resourceStateLabel(state: string): string {
	const known = RESOURCE_STATE_LABEL[state];
	if (known) return known;
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

/** Extracts a human `currentStep` hint from a job's free-form `progress` JSON, if present. */
export function jobCurrentStep(job: Pick<SyncJob, "progress">): string | undefined {
	const step = job.progress?.currentStep;
	return typeof step === "string" && step.length > 0 ? step : undefined;
}
