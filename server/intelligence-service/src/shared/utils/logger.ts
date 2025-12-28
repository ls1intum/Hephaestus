import type { Context } from "hono";
import type { PinoLogger } from "hono-pino";
import type { AppBindings } from "@/shared/http/types";

/**
 * Minimal logger interface for handlers.
 * Matches PinoLogger's signature for the methods we actually use.
 */
export interface HandlerLogger {
	error: (obj: unknown, msg?: string) => void;
	warn: (obj: unknown, msg?: string) => void;
	info: (obj: unknown, msg?: string) => void;
	debug: (obj: unknown, msg?: string) => void;
}

/**
 * No-op logger for testing or when logger middleware isn't available.
 */
const noopLogger: HandlerLogger = {
	error: () => {
		/* noop - used when logger middleware isn't available */
	},
	warn: () => {
		/* noop */
	},
	info: () => {
		/* noop */
	},
	debug: () => {
		/* noop */
	},
};

/**
 * Extract logger from Hono context with type safety.
 * Falls back to no-op logger if middleware isn't configured.
 */
export function getLogger(c: Context<AppBindings>): HandlerLogger {
	try {
		const logger = c.get("logger") as PinoLogger | undefined;
		if (logger) {
			return logger;
		}
	} catch {
		// Context.get may throw if not set
	}
	return noopLogger;
}
