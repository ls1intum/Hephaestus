import environment from "@/environment";
import { sanitizeValue } from "@/integrations/posthog/config";

/**
 * Sentry runtime configuration, mirroring `posthog/config.ts`. Reuses `sanitizeValue` so an empty
 * value or a `WEB_ENV_*` placeholder (left by the prod entrypoint when no DSN is provided) is
 * treated as "not configured" — the consent UI then hides the error-reports option entirely.
 */
export const sentryDsn = sanitizeValue(environment.sentry?.dsn);
export const sentryEnvironment = sanitizeValue(environment.sentry?.environment);

/** Whether Sentry is configured (a real DSN is present). The consent gate is layered on top. */
export const isSentryConfigured = sentryDsn.length > 0;
