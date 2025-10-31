import environment from "@/environment";

const PLACEHOLDER_PREFIX = "WEB_ENV_";

const sanitizeValue = (value?: string) => {
	if (!value) {
		return "";
	}
	const trimmed = value.trim();
	if (!trimmed || trimmed.startsWith(PLACEHOLDER_PREFIX)) {
		return "";
	}
	return trimmed;
};

export const posthogProjectApiKey = sanitizeValue(environment.posthog?.projectApiKey);
export const posthogApiHost = sanitizeValue(environment.posthog?.apiHost);
export const isPosthogEnabled = posthogProjectApiKey.length > 0;
