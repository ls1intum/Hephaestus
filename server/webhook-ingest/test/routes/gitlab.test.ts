import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import app from "@/app";

// Mock the NATS client
vi.mock("@/nats/client", () => ({
	natsClient: {
		isConnected: true,
		publishWithRetry: vi.fn().mockResolvedValue(undefined),
	},
}));

// Mock the env module
vi.mock("@/env", () => ({
	default: {
		PORT: 4200,
		WEBHOOK_SECRET: "test-secret-that-is-at-least-32-chars-long",
		MAX_PAYLOAD_SIZE_MB: 25,
		NODE_ENV: "test",
		LOG_LEVEL: "silent",
		NATS_URL: "nats://localhost:4222",
		STREAM_MAX_AGE_DAYS: 180,
		STREAM_MAX_MSGS: 2_000_000,
		NATS_PUBLISH_TIMEOUT_MS: 9000,
		NATS_PUBLISH_MAX_RETRIES: 5,
		NATS_PUBLISH_RETRY_BASE_DELAY_MS: 200,
	},
}));

import { natsClient } from "@/nats/client";

type JsonResponse = Record<string, unknown>;

const TEST_SECRET = "test-secret-that-is-at-least-32-chars-long";

describe("GitLab Webhook Route", () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	afterEach(() => {
		vi.restoreAllMocks();
	});

	describe("POST /gitlab", () => {
		it("should accept valid webhook with correct token", async () => {
			const payload = JSON.stringify({
				object_kind: "push",
				project: {
					path_with_namespace: "group/myproject",
				},
			});

			const res = await app.request("/gitlab", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-GitLab-Token": TEST_SECRET,
					"X-GitLab-Event": "Push Hook",
				},
				body: payload,
			});

			expect(res.status).toBe(200);
			const json = (await res.json()) as JsonResponse;
			expect(json.status).toBe("ok");
			expect(natsClient.publishWithRetry).toHaveBeenCalledTimes(1);
		});

		it("should reject request with invalid token", async () => {
			const payload = JSON.stringify({
				object_kind: "push",
				project: { path_with_namespace: "org/repo" },
			});

			const res = await app.request("/gitlab", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-GitLab-Token": "wrong-token",
					"X-GitLab-Event": "Push Hook",
				},
				body: payload,
			});

			expect(res.status).toBe(401);
			const json = (await res.json()) as JsonResponse;
			expect(json.error).toBe("Invalid token");
		});

		it("should reject request without token header", async () => {
			const payload = JSON.stringify({
				object_kind: "push",
				project: { path_with_namespace: "org/repo" },
			});

			const res = await app.request("/gitlab", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-GitLab-Event": "Push Hook",
				},
				body: payload,
			});

			expect(res.status).toBe(401);
		});

		it("should reject invalid JSON payload", async () => {
			const invalidPayload = "{ invalid json }";

			const res = await app.request("/gitlab", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-GitLab-Token": TEST_SECRET,
					"X-GitLab-Event": "Push Hook",
				},
				body: invalidPayload,
			});

			expect(res.status).toBe(400);
			const json = (await res.json()) as JsonResponse;
			expect(json.error).toBe("Invalid JSON payload");
		});

		it("should return 503 when NATS publish fails", async () => {
			vi.mocked(natsClient.publishWithRetry).mockRejectedValueOnce(
				new Error("NATS connection failed"),
			);

			const payload = JSON.stringify({
				object_kind: "merge_request",
				project: { path_with_namespace: "org/repo" },
			});

			const res = await app.request("/gitlab", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-GitLab-Token": TEST_SECRET,
					"X-GitLab-Event": "Merge Request Hook",
				},
				body: payload,
			});

			expect(res.status).toBe(503);
			const json = (await res.json()) as JsonResponse;
			expect(json.error).toBe("Failed to publish webhook");
		});

		it("should use Idempotency-Key for deduplication when available", async () => {
			const payload = JSON.stringify({
				object_kind: "push",
				project: { path_with_namespace: "org/repo" },
			});

			const res = await app.request("/gitlab", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-GitLab-Token": TEST_SECRET,
					"X-GitLab-Event": "Push Hook",
					"Idempotency-Key": "unique-idempotency-key-123",
				},
				body: payload,
			});

			expect(res.status).toBe(200);
			expect(natsClient.publishWithRetry).toHaveBeenCalledWith(
				"gitlab.org.repo.push",
				expect.any(Uint8Array),
				expect.any(Map),
			);

			// Verify the headers map contains the deduplication ID
			const callArgs = vi.mocked(natsClient.publishWithRetry).mock.calls[0];
			const headers = callArgs?.[2] as Map<string, string>;
			expect(headers?.get("Nats-Msg-Id")).toBe("gitlab-unique-idempotency-key-123");
		});

		it("should fall back to X-Gitlab-Event-UUID for deduplication", async () => {
			const payload = JSON.stringify({
				object_kind: "issue",
				project: { path_with_namespace: "org/repo" },
			});

			const res = await app.request("/gitlab", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-GitLab-Token": TEST_SECRET,
					"X-GitLab-Event": "Issue Hook",
					"X-Gitlab-Event-UUID": "event-uuid-456",
				},
				body: payload,
			});

			expect(res.status).toBe(200);
			const callArgs = vi.mocked(natsClient.publishWithRetry).mock.calls[0];
			const headers = callArgs?.[2] as Map<string, string>;
			expect(headers?.get("Nats-Msg-Id")).toBe("gitlab-event-uuid-456");
		});

		it("should handle group-level events", async () => {
			const payload = JSON.stringify({
				object_kind: "member",
				group: { full_path: "parent/child" },
			});

			const res = await app.request("/gitlab", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-GitLab-Token": TEST_SECRET,
					"X-GitLab-Event": "Member Hook",
				},
				body: payload,
			});

			expect(res.status).toBe(200);
			expect(natsClient.publishWithRetry).toHaveBeenCalledWith(
				"gitlab.parent~child.?.member",
				expect.any(Uint8Array),
				expect.any(Map),
			);
		});
	});
});
