/**
 * Severity of an audit event, derived from its outcome + type. Drives the row's visual emphasis so a
 * failed login or a privilege change stands out from routine traffic.
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

export { refLabel as accountLabel } from "../audit-shared/refLabel";

export { formatTimestamp } from "../audit-shared/timeFormat";

/**
 * Turn the JSONB `details` blob into a human sentence where we can — `{"from":"USER","to":"APP_ADMIN"}`
 * → `USER → APP_ADMIN`, other objects → `key: value` pairs — and fall back to the raw string when it is
 * not parseable JSON.
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

export { prettyJson as prettyDetails } from "../audit-shared/prettyJson";
