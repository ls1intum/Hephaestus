import { Hono } from "hono";
import { bodyLimit } from "hono/body-limit";
import { HTTPException } from "hono/http-exception";
import { requestId as requestIdMiddleware } from "hono/request-id";

import env from "@/env";
import logger from "@/logger";
import github from "@/routes/github";
import gitlab from "@/routes/gitlab";
import health from "@/routes/health";

/**
 * Application assembly
 *
 * Creates and configures the Hono application with all middleware and routes.
 */
const app = new Hono();

// Request ID middleware for tracing
app.use(requestIdMiddleware());

// Body limit middleware to prevent DoS attacks
// CVE-2025-59139: Fixed in Hono 4.9.7+
const maxPayloadBytes = env.MAX_PAYLOAD_SIZE_MB * 1024 * 1024;
app.use(
	bodyLimit({
		maxSize: maxPayloadBytes,
		onError: (c) => {
			return c.json({ error: "Payload too large" }, 413);
		},
	}),
);

// Simple request logging middleware
app.use(async (c, next) => {
	const start = Date.now();
	const requestId = c.get("requestId");
	const path = c.req.path;
	const method = c.req.method;

	await next();

	const duration = Date.now() - start;
	const status = c.res.status;

	logger.info(
		{ requestId, method, path, status, durationMs: duration },
		"Request completed",
	);
});

// Global error handler
app.onError((err, c) => {
	const requestId = c.get("requestId");

	if (err instanceof HTTPException) {
		logger.warn(
			{ requestId, status: err.status, message: err.message },
			"HTTP exception",
		);
		return c.json({ error: err.message }, err.status);
	}

	logger.error(
		{ requestId, error: err.message, stack: err.stack },
		"Unhandled error",
	);
	return c.json({ error: "Internal server error" }, 500);
});

// 404 handler
app.notFound((c) => {
	const requestId = c.get("requestId");
	logger.debug({ requestId, path: c.req.path }, "Not found");
	return c.json({ error: "Not found" }, 404);
});

// Route registration
app.route("/github", github);
app.route("/gitlab", gitlab);
app.route("/health", health);

// Root redirect
app.get("/", (c) => c.json({ service: "webhook-ingest", status: "running" }));

export default app;
