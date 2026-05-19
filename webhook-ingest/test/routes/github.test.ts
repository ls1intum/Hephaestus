import { createHmac } from "node:crypto";
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

function createGitHubSignature(payload: string, secret: string): string {
	const mac = createHmac("sha256", secret);
	mac.update(payload);
	return `sha256=${mac.digest("hex")}`;
}

describe("GitHub Webhook Route", () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	afterEach(() => {
		vi.restoreAllMocks();
	});

	describe("POST /github", () => {
		it("should accept valid webhook with correct signature", async () => {
			const payload = JSON.stringify({
				action: "opened",
				repository: {
					name: "test-repo",
					owner: { login: "test-org" },
				},
			});
			const signature = createGitHubSignature(payload, TEST_SECRET);

			const res = await app.request("/github", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-Hub-Signature-256": signature,
					"X-GitHub-Event": "pull_request",
					"X-GitHub-Delivery": "test-delivery-id-123",
				},
				body: payload,
			});

			expect(res.status).toBe(200);
			const json = (await res.json()) as JsonResponse;
			expect(json.status).toBe("ok");
			expect(natsClient.publishWithRetry).toHaveBeenCalledTimes(1);
		});

		it("should respond with pong for ping events", async () => {
			const payload = JSON.stringify({ zen: "test zen" });
			const signature = createGitHubSignature(payload, TEST_SECRET);

			const res = await app.request("/github", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-Hub-Signature-256": signature,
					"X-GitHub-Event": "ping",
					"X-GitHub-Delivery": "ping-delivery-id",
				},
				body: payload,
			});

			expect(res.status).toBe(200);
			const json = (await res.json()) as JsonResponse;
			expect(json.status).toBe("pong");
			// Ping should NOT publish to NATS
			expect(natsClient.publishWithRetry).not.toHaveBeenCalled();
		});

		it("should reject request with invalid signature", async () => {
			const payload = JSON.stringify({ test: "data" });

			const res = await app.request("/github", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-Hub-Signature-256": "sha256=invalid-signature",
					"X-GitHub-Event": "push",
				},
				body: payload,
			});

			expect(res.status).toBe(401);
			const json = (await res.json()) as JsonResponse;
			expect(json.error).toBe("Invalid signature");
		});

		it("should reject request without signature header", async () => {
			const payload = JSON.stringify({ test: "data" });

			const res = await app.request("/github", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-GitHub-Event": "push",
				},
				body: payload,
			});

			expect(res.status).toBe(401);
		});

		it("should reject request without X-GitHub-Event header", async () => {
			const payload = JSON.stringify({ test: "data" });
			const signature = createGitHubSignature(payload, TEST_SECRET);

			const res = await app.request("/github", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-Hub-Signature-256": signature,
				},
				body: payload,
			});

			expect(res.status).toBe(400);
			const json = (await res.json()) as JsonResponse;
			expect(json.error).toBe("Missing X-GitHub-Event header");
		});

		it("should reject invalid JSON payload", async () => {
			const invalidPayload = "{ invalid json }";
			const signature = createGitHubSignature(invalidPayload, TEST_SECRET);

			const res = await app.request("/github", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-Hub-Signature-256": signature,
					"X-GitHub-Event": "push",
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
				repository: {
					name: "test-repo",
					owner: { login: "test-org" },
				},
			});
			const signature = createGitHubSignature(payload, TEST_SECRET);

			const res = await app.request("/github", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-Hub-Signature-256": signature,
					"X-GitHub-Event": "push",
					"X-GitHub-Delivery": "delivery-123",
				},
				body: payload,
			});

			expect(res.status).toBe(503);
			const json = (await res.json()) as JsonResponse;
			expect(json.error).toBe("Failed to publish webhook");
		});

		it("should accept SHA-1 signature as fallback", async () => {
			const payload = JSON.stringify({
				repository: {
					name: "test-repo",
					owner: { login: "test-org" },
				},
			});
			const mac = createHmac("sha1", TEST_SECRET);
			mac.update(payload);
			const sha1Signature = `sha1=${mac.digest("hex")}`;

			const res = await app.request("/github", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-Hub-Signature": sha1Signature,
					"X-GitHub-Event": "push",
					"X-GitHub-Delivery": "sha1-delivery",
				},
				body: payload,
			});

			expect(res.status).toBe(200);
		});

		it("should handle organization-level events without repository", async () => {
			const payload = JSON.stringify({
				action: "member_added",
				organization: { login: "test-org" },
			});
			const signature = createGitHubSignature(payload, TEST_SECRET);

			const res = await app.request("/github", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"X-Hub-Signature-256": signature,
					"X-GitHub-Event": "organization",
					"X-GitHub-Delivery": "org-event-delivery",
				},
				body: payload,
			});

			expect(res.status).toBe(200);
			expect(natsClient.publishWithRetry).toHaveBeenCalledWith(
				"github.test-org.?.organization",
				expect.any(Uint8Array),
				expect.any(Map),
			);
		});

		describe("NATS subject sanitization", () => {
			it("should preserve wildcards in org/repo names", async () => {
				const payload = JSON.stringify({
					action: "opened",
					repository: {
						name: "test*repo",
						owner: { login: "test*org" },
					},
				});
				const signature = createGitHubSignature(payload, TEST_SECRET);

				const res = await app.request("/github", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"X-Hub-Signature-256": signature,
						"X-GitHub-Event": "push",
						"X-GitHub-Delivery": "wildcard-test",
					},
					body: payload,
				});

				expect(res.status).toBe(200);
				const callArgs = vi.mocked(natsClient.publishWithRetry).mock.calls[0];
				const subject = callArgs?.[0] as string;
				expect(subject).toBe("github.test*org.test*repo.push");
			});

			it("should preserve > in org/repo names", async () => {
				const payload = JSON.stringify({
					repository: {
						name: "test>repo",
						owner: { login: "test>org" },
					},
				});
				const signature = createGitHubSignature(payload, TEST_SECRET);

				const res = await app.request("/github", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"X-Hub-Signature-256": signature,
						"X-GitHub-Event": "push",
						"X-GitHub-Delivery": "gt-wildcard-test",
					},
					body: payload,
				});

				expect(res.status).toBe(200);
				const callArgs = vi.mocked(natsClient.publishWithRetry).mock.calls[0];
				const subject = callArgs?.[0] as string;
				expect(subject).toBe("github.test>org.test>repo.push");
			});

			it("should sanitize dots in org/repo names", async () => {
				const payload = JSON.stringify({
					repository: {
						name: "my.repo.name",
						owner: { login: "my.org.name" },
					},
				});
				const signature = createGitHubSignature(payload, TEST_SECRET);

				const res = await app.request("/github", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"X-Hub-Signature-256": signature,
						"X-GitHub-Event": "push",
						"X-GitHub-Delivery": "dots-test",
					},
					body: payload,
				});

				expect(res.status).toBe(200);
				const callArgs = vi.mocked(natsClient.publishWithRetry).mock.calls[0];
				const subject = callArgs?.[0] as string;
				expect(subject).toBe("github.my~org~name.my~repo~name.push");
			});

			it("should preserve whitespace in org/repo names", async () => {
				const payload = JSON.stringify({
					repository: {
						name: "test\trepo",
						owner: { login: "test\norg" },
					},
				});
				const signature = createGitHubSignature(payload, TEST_SECRET);

				const res = await app.request("/github", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"X-Hub-Signature-256": signature,
						"X-GitHub-Event": "push",
						"X-GitHub-Delivery": "whitespace-test",
					},
					body: payload,
				});

				expect(res.status).toBe(200);
				const callArgs = vi.mocked(natsClient.publishWithRetry).mock.calls[0];
				const subject = callArgs?.[0] as string;
				expect(subject).toBe("github.test\norg.test\trepo.push");
			});

			it("should handle empty org/repo gracefully", async () => {
				const payload = JSON.stringify({
					action: "created",
					// No repository or organization
				});
				const signature = createGitHubSignature(payload, TEST_SECRET);

				const res = await app.request("/github", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"X-Hub-Signature-256": signature,
						"X-GitHub-Event": "installation",
						"X-GitHub-Delivery": "empty-org-test",
					},
					body: payload,
				});

				expect(res.status).toBe(200);
				const callArgs = vi.mocked(natsClient.publishWithRetry).mock.calls[0];
				const subject = callArgs?.[0] as string;
				expect(subject).toBe("github.?.?.installation");
			});
		});

		describe("empty and edge case payloads", () => {
			it("should handle empty JSON object", async () => {
				const payload = JSON.stringify({});
				const signature = createGitHubSignature(payload, TEST_SECRET);

				const res = await app.request("/github", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"X-Hub-Signature-256": signature,
						"X-GitHub-Event": "push",
						"X-GitHub-Delivery": "empty-payload-test",
					},
					body: payload,
				});

				expect(res.status).toBe(200);
				expect(natsClient.publishWithRetry).toHaveBeenCalled();
			});

			it("should handle JSON array payload", async () => {
				const payload = JSON.stringify([{ test: "data" }]);
				const signature = createGitHubSignature(payload, TEST_SECRET);

				const res = await app.request("/github", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"X-Hub-Signature-256": signature,
						"X-GitHub-Event": "push",
						"X-GitHub-Delivery": "array-payload-test",
					},
					body: payload,
				});

				// Arrays should be handled gracefully (treated as empty object for extraction)
				expect(res.status).toBe(200);
			});

			it("should handle null values in payload", async () => {
				const payload = JSON.stringify({
					repository: null,
					organization: null,
					action: null,
				});
				const signature = createGitHubSignature(payload, TEST_SECRET);

				const res = await app.request("/github", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"X-Hub-Signature-256": signature,
						"X-GitHub-Event": "push",
						"X-GitHub-Delivery": "null-values-test",
					},
					body: payload,
				});

				expect(res.status).toBe(200);
				const callArgs = vi.mocked(natsClient.publishWithRetry).mock.calls[0];
				const subject = callArgs?.[0] as string;
				expect(subject).toBe("github.?.?.push");
			});
		});

		describe("Content-Type edge cases", () => {
			it("should accept Content-Type with charset", async () => {
				const payload = JSON.stringify({
					repository: {
						name: "test-repo",
						owner: { login: "test-org" },
					},
				});
				const signature = createGitHubSignature(payload, TEST_SECRET);

				const res = await app.request("/github", {
					method: "POST",
					headers: {
						"Content-Type": "application/json; charset=utf-8",
						"X-Hub-Signature-256": signature,
						"X-GitHub-Event": "push",
						"X-GitHub-Delivery": "charset-test",
					},
					body: payload,
				});

				expect(res.status).toBe(200);
			});

			it("should reject non-JSON Content-Type", async () => {
				const payload = "not json";
				const signature = createGitHubSignature(payload, TEST_SECRET);

				const res = await app.request("/github", {
					method: "POST",
					headers: {
						"Content-Type": "text/plain",
						"X-Hub-Signature-256": signature,
						"X-GitHub-Event": "push",
						"X-GitHub-Delivery": "text-content-test",
					},
					body: payload,
				});

				// Hono returns 415 Unsupported Media Type for non-JSON Content-Type
				// or 400 if it tries to parse invalid JSON - both are acceptable rejections
				expect(res.status).toBeGreaterThanOrEqual(400);
				expect(res.status).toBeLessThan(500);
			});
		});
	});
});
