import { Hono } from "hono";
import { bodyLimit } from "hono/body-limit";
import { createMiddleware } from "hono/factory";
import { HTTPException } from "hono/http-exception";
import { requestId as requestIdMiddleware } from "hono/request-id";
import { timeout as timeoutMiddleware } from "hono/timeout";

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

// Request timeout to prevent slow loris attacks
// GitHub/GitLab webhook timeout is ~10s, so 15s gives margin for processing
const REQUEST_TIMEOUT_MS = 15_000;
app.use(
	timeoutMiddleware(REQUEST_TIMEOUT_MS, () => {
		throw new HTTPException(408, { message: "Request timeout" });
	}),
);

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

// Content-Type validation for webhook endpoints (security best practice)
// Only applied to POST requests on webhook routes
const validateJsonContentType = createMiddleware(async (c, next) => {
	if (c.req.method === "POST") {
		const contentType = c.req.header("Content-Type");
		if (!contentType?.includes("application/json")) {
			return c.json({ error: "Content-Type must be application/json" }, 415);
		}
	}
	return await next();
});
app.use("/github", validateJsonContentType);
app.use("/gitlab", validateJsonContentType);

// Simple request logging middleware
app.use(async (c, next) => {
	const start = Date.now();
	const requestId = c.get("requestId");
	const path = c.req.path;
	const method = c.req.method;

	await next();

	const duration = Date.now() - start;
	const status = c.res.status;

	logger.info({ requestId, method, path, status, durationMs: duration }, "Request completed");
});

// Global error handler
app.onError((err, c) => {
	const requestId = c.get("requestId");

	if (err instanceof HTTPException) {
		logger.warn({ requestId, status: err.status, message: err.message }, "HTTP exception");
		return c.json({ error: err.message }, err.status);
	}

	logger.error({ requestId, error: err.message, stack: err.stack }, "Unhandled error");
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
