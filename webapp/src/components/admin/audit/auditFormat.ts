import type { AdminListAuthEventsData } from "@/api/types.gen";
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

/** The event types the list endpoint accepts as a filter — the wire contract, not a hand-kept copy. */
export type AuthEventType = NonNullable<
	NonNullable<AdminListAuthEventsData["query"]>["eventType"]
>[number];

/**
 * Human labels for the auth event types. The filter facet and the table read the same map, so a row
 * can never disagree with the filter that produced it ("Sessions revoked" vs "Jwt revoked").
 */
export const EVENT_TYPE_LABELS: Record<AuthEventType, string> = {
	LOGIN: "Sign-in",
	LOGIN_FAILED: "Failed sign-in",
	LOGOUT: "Sign-out",
	TOKEN_REFRESH: "Token refresh",
	JWT_REVOKED: "Sessions revoked",
	IDENTITY_LINKED: "Identity linked",
	IDENTITY_UNLINKED: "Identity unlinked",
	IMPERSONATION_BEGIN: "Impersonation started",
	IMPERSONATION_END: "Impersonation ended",
	ACCOUNT_DELETED: "Account deleted",
	EXPORT_REQUESTED: "Data export requested",
	APP_ROLE_CHANGED: "Instance role changed",
	RESEARCH_CONSENT_REVOKED: "Research consent revoked",
	LLM_CONNECTION_CREATED: "Provider connected",
	LLM_CONNECTION_UPDATED: "Provider updated",
	LLM_CONNECTION_DELETED: "Provider removed",
	LLM_MODEL_CREATED: "Model added",
	LLM_MODEL_UPDATED: "Model updated",
	LLM_MODEL_DELETED: "Model removed",
	LLM_MODEL_PRICE_CHANGED: "Model price changed",
	LLM_MODEL_SHARING_CHANGED: "Model sharing changed",
	LLM_SETTINGS_CHANGED: "AI settings changed",
};

/** Falls back to a humanized enum name so an event type added server-side still reads sensibly. */
export function eventLabel(eventType: string): string {
	const known = (EVENT_TYPE_LABELS as Record<string, string | undefined>)[eventType];
	if (known) return known;
	const lower = eventType.replace(/_/g, " ").toLowerCase();
	return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/** `SUCCESS` → `Success`, so the Result column matches the Outcome facet. */
export function resultLabel(result: string): string {
	return result === "FAILURE" ? "Failure" : "Success";
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
