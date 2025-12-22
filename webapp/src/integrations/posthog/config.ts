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

export const posthogProjectApiKey = sanitizeValue(environment.posthog?.projectApiKey);
export const posthogApiHost = sanitizeValue(environment.posthog?.apiHost);
export const posthogEnabled = sanitizeBoolean(environment.posthog?.enabled);
export const isPosthogEnabled = posthogEnabled && posthogProjectApiKey.length > 0;
