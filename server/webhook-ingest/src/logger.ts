import pino from "pino";
import env from "@/env";

/**
 * Shared application logger.
 *
 * All components should import this logger instead of creating new instances.
 */
export const logger = pino({
	level: env.LOG_LEVEL,
	// In production, use JSON logs (default pino output)
	// For local dev, set LOG_LEVEL=debug and use: npm run dev 2>&1 | npx pino-pretty
});

export default logger;
