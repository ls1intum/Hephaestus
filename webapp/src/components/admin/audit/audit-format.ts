import type { AdminListAuthEventsData } from "@/api/types.gen";
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

export type AuthEventType = NonNullable<
	NonNullable<AdminListAuthEventsData["query"]>["eventType"]
>[number];

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
	LOGIN_PROVIDER_CREATED: "Login provider added",
	LOGIN_PROVIDER_UPDATED: "Login provider updated",
	LOGIN_PROVIDER_DELETED: "Login provider removed",
	SILENT_MODE_CHANGED: "Silent mode changed",
};

export function eventLabel(eventType: string): string {
	const known = (EVENT_TYPE_LABELS as Record<string, string | undefined>)[eventType];
	if (known) return known;
	const lower = eventType.replace(/_/g, " ").toLowerCase();
	return lower.charAt(0).toUpperCase() + lower.slice(1);
}

export function resultLabel(result: string): string {
	return result === "FAILURE" ? "Failure" : "Success";
}

export function humanizeDetails(details: string | undefined): string | null {
	if (!details) return null;
	let parsed: unknown;
	try {
		parsed = JSON.parse(details);
	} catch {
		return details;
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

/** Tailwind class per severity; `warning` is the only tone that earns a colour. */
const SEVERITY_DOT: Record<AuditSeverity, string> = {
	error: "bg-destructive",
	warning: "bg-warning",
	info: "bg-muted-foreground/40",
};

/**
 * The dot's hue is the only marker of a high-risk event, so screen readers get it in words.
 * Import this rather than re-deriving the colours: a second copy drifts silently.
 */
export function severityDotClass(severity: AuditSeverity): string {
	return SEVERITY_DOT[severity];
}

export function severityScreenReaderPrefix(severity: AuditSeverity): string | null {
	return severity === "warning" ? "High-risk event: " : null;
}
