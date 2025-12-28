import { Hono } from "hono";
import { natsClient } from "@/nats/client";

const health = new Hono();

/**
 * Health check endpoint
 *
 * Used by Docker and Kubernetes for container orchestration and
 * service health monitoring.
 *
 * Returns OK only if NATS connection is healthy.
 */
health.get("/", (c) => {
	const natsHealthy = natsClient.isConnected;

	if (!natsHealthy) {
		return c.json({ status: "UNHEALTHY", nats: "disconnected" }, 503);
	}

	return c.json({ status: "OK", nats: "connected" });
});

export default health;
