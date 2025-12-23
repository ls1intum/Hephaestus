import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { verifyGitLabToken } from "@/crypto/verify";
import env from "@/env";
import logger from "@/logger";
import { natsClient } from "@/nats/client";
import { buildDedupeId } from "@/utils/dedupe";
import { buildGitLabSubject } from "@/utils/gitlab-subject";

const gitlab = new Hono();

/**
 * GitLab webhook endpoint
 *
 * Verifies the webhook token and publishes the payload to NATS JetStream.
 * GitLab uses a simple token comparison (not HMAC) for webhook authentication.
 */
gitlab.post("/", async (c) => {
	// Get raw body
	const rawBody = await c.req.arrayBuffer();
	const bodyBytes = new Uint8Array(rawBody);

	// Get token header
	const token = c.req.header("X-GitLab-Token");
	const eventType = c.req.header("X-GitLab-Event");

	// Verify webhook secret is configured
	const secret = env.WEBHOOK_SECRET;
	if (!secret) {
		throw new HTTPException(401, {
			message: "GitLab webhook secret not configured",
		});
	}

	// Verify token using constant-time comparison
	if (!verifyGitLabToken(token, secret)) {
		logger.warn({ eventType }, "Invalid GitLab webhook token");
		throw new HTTPException(401, { message: "Invalid token" });
	}

	// Parse payload
	let payload: Record<string, unknown>;
	try {
		payload = JSON.parse(new TextDecoder().decode(bodyBytes));
	} catch {
		throw new HTTPException(400, { message: "Invalid JSON payload" });
	}

	// Build subject from payload
	const subject = buildGitLabSubject(payload);
	const payloadEventType =
		(payload.object_kind as string) || (payload.event_name as string) || undefined;

	// Build headers for deduplication and tracing
	// GitLab provides these headers (per GitLab docs):
	// - X-Gitlab-Event-UUID (since GitLab 16.2)
	// - Idempotency-Key (since GitLab 17.4)
	// - X-Gitlab-Webhook-UUID (webhook instance identifier)
	const eventUuid = c.req.header("X-Gitlab-Event-UUID");
	const idempotencyKey = c.req.header("Idempotency-Key");
	const webhookUuid = c.req.header("X-Gitlab-Webhook-UUID");

	const headers = new Map<string, string>();
	// Use Idempotency-Key if available (GitLab 17.4+), fall back to X-Gitlab-Event-UUID
	const deduplicationId = idempotencyKey ?? eventUuid;
	const dedupeId = deduplicationId
		? `gitlab-${deduplicationId}`
		: buildDedupeId("gitlab", bodyBytes, eventType ?? payloadEventType);
	headers.set("Nats-Msg-Id", dedupeId);

	const eventHeader = eventType ?? payloadEventType;
	if (eventHeader) {
		headers.set("X-GitLab-Event", eventHeader);
	}
	if (webhookUuid) {
		headers.set("X-GitLab-Webhook-UUID", webhookUuid);
	}

	// Publish to NATS
	try {
		await natsClient.publishWithRetry(subject, bodyBytes, headers);
	} catch (error) {
		logger.error(
			{ eventType: eventHeader, subject, error },
			"Failed to publish GitLab webhook to NATS",
		);
		throw new HTTPException(503, {
			message: "Failed to publish webhook",
		});
	}

	logger.info({ eventType: eventHeader, subject }, "Published GitLab webhook to NATS");

	return c.json({ status: "ok" });
});

export default gitlab;
