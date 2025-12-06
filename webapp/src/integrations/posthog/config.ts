import environment from "@/environment";
import { sanitizeBoolean, sanitizeValue } from "@/lib/utils";

export const posthogProjectApiKey = sanitizeValue(
	environment.posthog?.projectApiKey,
);
export const posthogApiHost = sanitizeValue(environment.posthog?.apiHost);
export const posthogEnabled = sanitizeBoolean(environment.posthog?.enabled);
export const isPosthogEnabled =
	posthogEnabled && posthogProjectApiKey.length > 0;
