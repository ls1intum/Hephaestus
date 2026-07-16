import { formatDistanceToNow } from "date-fns";
import type { ConnectionSyncStatus, IntegrationCatalogEntry, SyncJob } from "@/api/types.gen";

/**
 * Poll cadence for sync status/resources/jobs queries. SSE hint invalidation is the primary live
 * channel, so polling is not a co-equal partner — it is either a slow backstop or the degraded
 * substitute for a stream that is down:
 *
 * - **Healthy stream, job running →** 30s backstop. Hint delivery is at-most-once, so a dropped
 *   terminal hint would otherwise strand a "Running" job on screen forever. 30s bounds that
 *   staleness without competing with the sub-second freshness the hints already provide.
 * - **Healthy stream, idle →** no interval at all. Idle changes arrive via mutation invalidation
 *   (user-initiated) or a hint (scheduled jobs); a dropped idle hint is healed by the next window
 *   focus, which `staleTime: 30_000` keeps cheap.
 * - **`livePushUnavailable` →** polling *is* the freshness channel, so pay the full 5s/60s.
 */
export function syncPollInterval(
	hasActiveJob: boolean,
	livePushUnavailable: boolean,
): number | false {
	if (livePushUnavailable) return hasActiveJob ? 5_000 : 60_000;
	return hasActiveJob ? 30_000 : false;
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

export type ConnectionState = NonNullable<IntegrationCatalogEntry["connectionState"]>;

/**
 * The connection lifecycle as a word rather than a wire token.
 *
 * Lowercasing the enum shipped "Slack is uninstalled." and "Outline suspended" to admins — machine
 * tokens dressed as English. This is the {@link HEALTH_LABEL} treatment for `connectionState`: a short
 * label for inline use. Copy that must also explain the *consequence* and the *next action* lives in
 * `ConnectionStateNotice`, which is the only thing that should be explaining a blocked state at length.
 */
export const CONNECTION_STATE_LABEL: Record<ConnectionState, string> = {
	PENDING: "finishing setup",
	ACTIVE: "connected",
	SUSPENDED: "suspended",
	UNINSTALLED: "removed",
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

/**
 * The job types an admin can trigger by hand. `INITIAL` is deliberately excluded: the server runs it
 * once when a connection is first established, and no UI offers it as a button.
 */
export type SyncTriggerType = Extract<SyncJob["type"], "RECONCILIATION" | "BACKFILL">;

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
