import type { AccountRef, AuthEventView } from "@/api/types.gen";

/**
 * Severity of an audit event, derived from its outcome + type. Drives the row's visual emphasis so a
 * failed login or a privilege change stands out from routine traffic — the pattern Okta (INFO vs WARN),
 * Datadog and Auth0 (Error/Warn/Success) all use for audit/security logs.
 */
export type AuditSeverity = "error" | "warning" | "info";

const HIGH_RISK_EVENTS = new Set([
	"IMPERSONATION_BEGIN",
	"APP_ROLE_CHANGED",
	"ACCOUNT_DELETED",
	"JWT_REVOKED",
	"IDENTITY_UNLINKED",
]);

export function eventSeverity(eventType: string, result: string): AuditSeverity {
	if (result === "FAILURE") return "error";
	if (HIGH_RISK_EVENTS.has(eventType)) return "warning";
	return "info";
}

/** A short human label for an event type — `APP_ROLE_CHANGED` → `App role changed`. */
export function eventLabel(eventType: string): string {
	const lower = eventType.replace(/_/g, " ").toLowerCase();
	return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/**
 * Render an account reference as a human label, falling back to `#id` when the account no longer exists
 * (audit rows outlive accounts — deletion, GDPR redaction). `id` is the raw subject/actor id when no
 * resolved ref is available.
 */
export function accountLabel(ref: AccountRef | undefined, id: number | undefined): string | null {
	if (ref) return ref.displayName || ref.email || `#${ref.id}`;
	if (id != null) return `#${id}`;
	return null;
}

export interface FormattedTimestamp {
	/** Absolute time in the viewer's locale + timezone (forensic precision). */
	local: string;
	/** The canonical ISO-8601 UTC instant, for the hover tooltip and copy/paste. */
	isoUtc: string;
}

/**
 * Audit timestamps need precision, so the row shows the absolute local time; the exact ISO-8601 **UTC**
 * instant is kept in the tooltip so events stay comparable across timezones (the convention CloudTrail,
 * Tailscale and GitHub follow: store/serve UTC, render local).
 */
export function formatTimestamp(value: AuthEventView["occurredAt"]): FormattedTimestamp {
	// The generated client types this `Date`, but the response transformers aren't wired into the SDK
	// calls, so it arrives as an ISO string at runtime — coerce defensively (same pattern as elsewhere).
	const date = value instanceof Date ? value : new Date(value);
	return { local: date.toLocaleString(), isoUtc: date.toISOString() };
}

/**
 * Turn the JSONB `details` blob into a human sentence where we can — `{"from":"USER","to":"APP_ADMIN"}`
 * → `USER → APP_ADMIN`, other objects → `key: value` pairs — and fall back to the raw string when it is
 * not parseable JSON. Tailscale / Datadog / Vercel all render structured changes as old→new diffs rather
 * than raw JSON; this is the lightweight version of that.
 */
export function humanizeDetails(details: string | undefined): string | null {
	if (!details) return null;
	let parsed: unknown;
	try {
		parsed = JSON.parse(details);
	} catch {
		return details; // not JSON — show as-is
	}
	if (parsed === null || typeof parsed !== "object") return String(parsed);
	const obj = parsed as Record<string, unknown>;
	if ("from" in obj || "to" in obj) {
		return `${stringify(obj.from)} → ${stringify(obj.to)}`;
	}
	const entries = Object.entries(obj);
	if (entries.length === 0) return null;
	return entries.map(([k, v]) => `${k}: ${stringify(v)}`).join(", ");
}

function stringify(value: unknown): string {
	if (value === null || value === undefined) return "—";
	if (typeof value === "object") return JSON.stringify(value);
	return String(value);
}

/** Pretty-print the raw `details` JSON for the detail panel; returns the raw string if not JSON. */
export function prettyDetails(details: string | undefined): string | null {
	if (!details) return null;
	try {
		return JSON.stringify(JSON.parse(details), null, 2);
	} catch {
		return details;
	}
}
