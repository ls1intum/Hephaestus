/**
 * Application Entry Point
 *
 * Instrumentation import must be first to initialize OpenTelemetry
 * before any other modules load and make LLM calls.
 */
import "./instrumentation";

import { serve } from "@hono/node-server";
import pino from "pino";

import app from "./app";
import env from "./env";
import { shutdownTelemetry } from "./instrumentation";
import { pool } from "./shared/db";

const logger = pino({ level: env.LOG_LEVEL });
const port = env.PORT;

// Fail-fast: Validate database configuration at startup
if (env.DATABASE_URL.includes("placeholder")) {
	logger.fatal(
		"DATABASE_URL contains placeholder value. Set a real database URL before starting the server.",
	);
	process.exit(1);
}

// Validate database connectivity before accepting requests
async function validateDatabaseConnection(): Promise<void> {
	try {
		await pool.query("SELECT 1");
		logger.info("Database connection validated");
	} catch (error) {
		logger.fatal({ error }, "Failed to connect to database at startup");
		process.exit(1);
	}
}

await validateDatabaseConnection();

logger.info(`Server is running on http://localhost:${port}`);

const server = serve({
	fetch: app.fetch,
	port,
});

// Shutdown timeout to prevent hanging indefinitely
const SHUTDOWN_TIMEOUT_MS = 10_000;

// Graceful shutdown handlers
const shutdown = async (signal: string) => {
	logger.info({ signal }, "Received shutdown signal, gracefully shutting down...");

	// Create a timeout to force exit if shutdown hangs
	const forceExit = setTimeout(() => {
		logger.error("Shutdown timed out, forcing exit");
		process.exit(1);
	}, SHUTDOWN_TIMEOUT_MS);

	try {
		// Close HTTP server first (stop accepting new connections)
		await new Promise<void>((resolve) => {
			server.close(() => {
				logger.info("HTTP server closed");
				resolve();
			});
		});

		// Close database pool
		await pool.end();
		logger.info("Database pool closed");

		// Flush pending telemetry traces
		await shutdownTelemetry();
		logger.info("Telemetry flushed");

		clearTimeout(forceExit);
		process.exit(0);
	} catch (error) {
		logger.error({ error }, "Error during shutdown");
		clearTimeout(forceExit);
		process.exit(1);
	}
};

// Handle uncaught exceptions and unhandled rejections
process.on("uncaughtException", (error) => {
	logger.fatal({ error }, "Uncaught exception, shutting down");
	shutdown("uncaughtException").catch(() => process.exit(1));
});

process.on("unhandledRejection", (reason) => {
	logger.fatal({ reason }, "Unhandled rejection, shutting down");
	shutdown("unhandledRejection").catch(() => process.exit(1));
});

process.on("SIGTERM", () => {
	shutdown("SIGTERM").catch(() => process.exit(1));
});
process.on("SIGINT", () => {
	shutdown("SIGINT").catch(() => process.exit(1));
});
