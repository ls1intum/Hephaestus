import pino from "pino";
import env from "@/env";

/**
 * Sensitive field paths to redact from logs.
 * Uses Pino's redact syntax: https://getpino.io/#/docs/redaction
 */
const REDACT_PATHS = [
	// Authentication and secrets
	"*.token",
	"*.secret",
	"*.password",
	"*.apiKey",
	"*.api_key",
	"*.authorization",
	"*.Authorization",
	"*.x-gitlab-token",
	"*.X-GitLab-Token",
	// Webhook payloads may contain sensitive data
	"*.access_token",
	"*.refresh_token",
	"*.private_key",
	"*.encrypted_value",
	// Headers that might leak secrets
	"req.headers.authorization",
	"req.headers.x-gitlab-token",
	"req.headers.cookie",
	// URL credentials (e.g., nats://user:pass@host)
	"*.url",
	"url",
	// Error objects may contain sensitive payload data
	"error.config",
	"error.request",
	"error.response",
	"*.error.config",
	"*.error.request",
	"*.error.response",
];

/**
 * Shared application logger.
 *
 * All components should import this logger instead of creating new instances.
 * Sensitive fields are automatically redacted.
 */
export const logger = pino({
	level: env.LOG_LEVEL,
	// Redact sensitive fields from all log output
	redact: {
		paths: REDACT_PATHS,
		censor: "[REDACTED]",
	},
	// In production, use JSON logs (default pino output)
	// For local dev, set LOG_LEVEL=debug and use: npm run dev 2>&1 | npx pino-pretty
});

export default logger;
