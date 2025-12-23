/**
 * Application Entry Point
 *
 * Initializes NATS connection and starts the HTTP server.
 */
import process from "node:process";
import { serve } from "@hono/node-server";

import app from "@/app";
import env from "@/env";
import logger from "@/logger";
import { natsClient } from "@/nats/client";

const port = env.PORT;

// Shutdown timeout to prevent hanging indefinitely
const SHUTDOWN_TIMEOUT_MS = 10_000;

async function main() {
	// Connect to NATS first (streams will be initialized)
	await natsClient.connect();

	logger.info(`Server is running on http://localhost:${port}`);

	const server = serve({
		fetch: app.fetch,
		port,
	});

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

			// Close NATS connection
			await natsClient.close();
			logger.info("NATS connection closed");

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
}

main().catch((error) => {
	logger.fatal({ error }, "Failed to start server");
	process.exit(1);
});
