import environment from "@/environment";

const PLACEHOLDER_PREFIX = "WEB_ENV_";

export const sanitizeValue = (value?: string | boolean) => {
	if (typeof value === "boolean") {
		return value ? "true" : "false";
	}
	if (!value) {
		return "";
	}
	const trimmed = value.trim();
	if (!trimmed || trimmed.startsWith(PLACEHOLDER_PREFIX)) {
		return "";
	}
	return trimmed;
};

export const sanitizeBoolean = (value?: string | boolean) => {
	const sanitized = sanitizeValue(value);
	if (!sanitized) {
		return false;
	}
	const normalized = sanitized.toLowerCase();
	return ["true", "1", "yes", "on"].includes(normalized);
};

export const posthogProjectApiKey = sanitizeValue(environment.posthog.projectApiKey);
export const posthogApiHost = sanitizeValue(environment.posthog.apiHost);
export const posthogEnabled = sanitizeBoolean(environment.posthog.enabled);
export const isPosthogEnabled = posthogEnabled && posthogProjectApiKey.length > 0;

/** Here rather than inline so a test can hold them to the decisions below. */
export const posthogOptions = {
	api_host: posthogApiHost || undefined,
	cross_subdomain_cookie: false,
	// Consent is ANDed: the provider only mounts once analytics consent is granted (ADR 0017), and
	// nothing is captured until `PostHogIdentity` opts in on the per-user research setting.
	opt_out_capturing_by_default: true,
	// Core Web Vitals, from the Chrome library PostHog already wraps, so capture inherits the gates
	// above. An object rather than a boolean because each key is answered separately: unset defers
	// to the PostHog project's remote config and `true` would turn `network_timing` on outright —
	// request URLs, for a Session Replay this app does not run.
	capture_performance: { web_vitals: true, network_timing: false },
} as const;
