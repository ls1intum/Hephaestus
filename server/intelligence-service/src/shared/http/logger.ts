import { pinoLogger as logger } from "hono-pino";
import pino from "pino";

import env from "@/env";

/**
 * Pino logger middleware for Hono
 *
 * Best practices implemented:
 * 1. Log level based on environment (LOG_LEVEL env var)
 * 2. Pretty printing only in development using hono-pino's debug-log transport
 * 3. Integration with Hono's requestId middleware via referRequestIdKey
 * 4. Sensitive data filtering via redact configuration
 * 5. Minimal base bindings in production to reduce log size
 */
export function pinoLogger() {
	const isProduction = env.NODE_ENV === "production";

	return logger({
		pino: pino({
			level: env.LOG_LEVEL || "info",
			// Avoid logging hostname and pid in production for cleaner logs
			base: isProduction ? { env: env.NODE_ENV } : undefined,
			// Redact sensitive fields that might appear in logs
			redact: {
				paths: [
					"req.headers.authorization",
					"req.headers.cookie",
					"req.headers['x-api-key']",
					"*.password",
					"*.token",
					"*.secret",
					"*.apiKey",
					"*.api_key",
				],
				censor: "[REDACTED]",
			},
			// Use pino's built-in transport for pretty printing in development
			...(isProduction
				? {}
				: {
						transport: {
							target: "hono-pino/debug-log",
						},
					}),
		}),
		http: {
			// Use Hono's requestId middleware for request tracing
			referRequestIdKey: "requestId",
			// Set appropriate log level based on response status
			onResLevel: (c) => {
				const status = c.res.status;
				if (status >= 500) {
					return "error";
				}
				if (status >= 400) {
					return "warn";
				}
				return "info";
			},
			// Include only essential request info to avoid bloat
			onReqBindings: (c) => ({
				req: {
					method: c.req.method,
					url: c.req.path,
					// Exclude full headers object, include only safe ones
					userAgent: c.req.header("user-agent"),
				},
			}),
		},
	});
}
