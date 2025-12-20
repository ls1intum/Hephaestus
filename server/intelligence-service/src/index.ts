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

const logger = pino({ level: env.LOG_LEVEL });
const port = env.PORT;
logger.info(`Server is running on http://localhost:${port}`);

const server = serve({
	fetch: app.fetch,
	port,
});

// Graceful shutdown handlers
const shutdown = async (signal: string) => {
	logger.info({ signal }, "Received shutdown signal, gracefully shutting down...");

	// Close HTTP server first (stop accepting new connections)
	server.close(() => {
		logger.info("HTTP server closed");
	});

	// Flush pending telemetry traces
	await shutdownTelemetry();
	logger.info("Telemetry flushed");

	process.exit(0);
};

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
