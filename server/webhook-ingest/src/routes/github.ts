import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { verifyGitHubSignature } from "@/crypto/verify";
import env from "@/env";
import logger from "@/logger";
import { natsClient } from "@/nats/client";
import { buildDedupeId } from "@/utils/dedupe";

const github = new Hono();

/**
 * Sanitize a string for use in NATS subject tokens.
 * NATS uses dots as token separators, so we replace them with ~.
 */
function sanitize(value: string): string {
	if (!value) {
		return "?";
	}
	return value.replace(/\./g, "~");
}

/** Extract org/repo from GitHub webhook payload. */
function extractSubjectComponents(payload: Record<string, unknown>): {
	org: string;
	repo: string;
	action: string | undefined;
} {
	let org = "?";
	let repo = "?";

	const repository = payload.repository as Record<string, unknown> | undefined;
	const organization = payload.organization as Record<string, unknown> | undefined;

	if (repository) {
		const owner = repository.owner as Record<string, unknown> | undefined;
		org = (owner?.login as string) ?? "?";
		repo = (repository.name as string) ?? "?";
	} else if (organization) {
		org = (organization.login as string) ?? "?";
	}

	const action = typeof payload.action === "string" ? (payload.action as string) : undefined;

	return { org, repo, action };
}

/** Build NATS message headers for GitHub webhooks. */
function buildHeaders(
	eventType: string,
	deliveryId: string | undefined,
	action: string | undefined,
	bodyBytes: Uint8Array,
): Map<string, string> {
	const headers = new Map<string, string>();
	if (deliveryId) {
		headers.set("X-GitHub-Delivery", deliveryId);
	}
	headers.set("X-GitHub-Event", eventType);
	if (action) {
		headers.set("X-GitHub-Action", action);
	}

	const dedupeId = deliveryId
		? `github-${deliveryId}`
		: buildDedupeId("github", bodyBytes, eventType);
	headers.set("Nats-Msg-Id", dedupeId);

	return headers;
}

/** Verify GitHub webhook signature, throwing HTTPException on failure. */
function verifyRequest(
	signature: string | null | undefined,
	bodyBytes: Uint8Array,
	deliveryId: string | undefined,
	eventType: string | undefined,
): void {
	// Note: env.WEBHOOK_SECRET is guaranteed to be a non-empty string by Zod validation
	if (!verifyGitHubSignature(signature, env.WEBHOOK_SECRET, bodyBytes)) {
		logger.warn({ deliveryId, eventType }, "Invalid GitHub webhook signature");
		throw new HTTPException(401, { message: "Invalid signature" });
	}
}

/** Parse JSON payload, throwing HTTPException on failure. */
function parsePayload(bodyBytes: Uint8Array): Record<string, unknown> {
	try {
		return JSON.parse(new TextDecoder().decode(bodyBytes));
	} catch {
		throw new HTTPException(400, { message: "Invalid JSON payload" });
	}
}

/**
 * GitHub webhook endpoint
 *
 * Verifies the webhook signature and publishes the payload to NATS JetStream.
 * The signature MUST be verified against the raw request body before any JSON parsing.
 */
github.post("/", async (c) => {
	const rawBody = await c.req.arrayBuffer();
	const bodyBytes = new Uint8Array(rawBody);

	const signatureSha256 = c.req.header("X-Hub-Signature-256");
	const signatureSha1 = c.req.header("X-Hub-Signature");
	const eventType = c.req.header("X-GitHub-Event");
	const deliveryId = c.req.header("X-GitHub-Delivery");

	// Verify signature (prefer SHA-256, fall back to SHA-1)
	const signature = signatureSha256 ?? signatureSha1;
	verifyRequest(signature, bodyBytes, deliveryId, eventType);

	if (!eventType) {
		throw new HTTPException(400, { message: "Missing X-GitHub-Event header" });
	}

	// Handle ping events
	if (eventType === "ping") {
		logger.info({ deliveryId }, "Received ping event");
		return c.json({ status: "pong" });
	}

	const payload = parsePayload(bodyBytes);
	const { org, repo, action } = extractSubjectComponents(payload);
	const subject = `github.${sanitize(org)}.${sanitize(repo)}.${sanitize(eventType)}`;
	const headers = buildHeaders(eventType, deliveryId, action, bodyBytes);

	try {
		await natsClient.publishWithRetry(subject, bodyBytes, headers);
	} catch (error) {
		logger.error(
			{ deliveryId, eventType, subject, error },
			"Failed to publish GitHub webhook to NATS",
		);
		throw new HTTPException(503, { message: "Failed to publish webhook" });
	}

	logger.info(
		{ deliveryId, eventType, action, org, repo, subject },
		"Published GitHub webhook to NATS",
	);

	return c.json({ status: "ok" });
});

export default github;
