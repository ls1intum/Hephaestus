import { serve } from "@hono/node-server";

import app from "./app";
import env from "./env";
import logger from "./logger";
import { pool } from "./shared/db";

const port = env.PORT;

if (env.DATABASE_URL.includes("placeholder")) {
	logger.fatal(
		"DATABASE_URL contains placeholder value. Set a real database URL before starting the server.",
	);
	process.exit(1);
}

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

const server = serve({ fetch: app.fetch, port });

/** Force exit if shutdown hangs (otherwise an idle keep-alive connection stalls it). */
const SHUTDOWN_TIMEOUT_MS = 10_000;

const shutdown = async (signal: string) => {
	logger.info({ signal }, "Received shutdown signal, gracefully shutting down...");

	const forceExit = setTimeout(() => {
		logger.error("Shutdown timed out, forcing exit");
		process.exit(1);
	}, SHUTDOWN_TIMEOUT_MS);

	try {
		await new Promise<void>((resolve) => {
			server.close(() => {
				logger.info("HTTP server closed");
				resolve();
			});
		});

		await pool.end();
		logger.info("Database pool closed");

		clearTimeout(forceExit);
		process.exit(0);
	} catch (error) {
		logger.error({ error }, "Error during shutdown");
		clearTimeout(forceExit);
		process.exit(1);
	}
};

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
