import pino from "pino";
import env from "./env";

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
	// Database credentials
	"*.connectionString",
	"*.DATABASE_URL",
	// LLM API keys
	"*.OPENAI_API_KEY",
	"*.ANTHROPIC_API_KEY",
	"*.AZURE_API_KEY",
	// Request/response sensitive data
	"*.access_token",
	"*.refresh_token",
	"*.private_key",
	// Headers
	"req.headers.authorization",
	"req.headers.cookie",
	// Error objects may contain sensitive data
	"error.config",
	"error.request",
	"error.response",
	"*.error.config",
	"*.error.request",
	"*.error.response",
];

/**
 * Base logger configuration with automatic sensitive field redaction.
 */
const baseLogger = pino({
	level: env.LOG_LEVEL,
	redact: {
		paths: REDACT_PATHS,
		censor: "[REDACTED]",
	},
});

/**
 * Create a named child logger with redaction inherited from base.
 */
export function createLogger(name: string): pino.Logger {
	return baseLogger.child({ name });
}

/**
 * Default application logger.
 */
export const logger = baseLogger;

export default logger;
