import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Create a mutable mock state object first
const mockState = { isConnected: true };

// Mock the NATS client - must be before importing app
vi.mock("@/nats/client", () => ({
	natsClient: {
		get isConnected() {
			return mockState.isConnected;
		},
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

import app from "@/app";

type JsonResponse = Record<string, unknown>;

describe("Health Check Route", () => {
	beforeEach(() => {
		vi.clearAllMocks();
		mockState.isConnected = true;
	});

	afterEach(() => {
		vi.restoreAllMocks();
	});

	describe("GET /health", () => {
		it("should return OK when NATS is connected", async () => {
			mockState.isConnected = true;

			const res = await app.request("/health", {
				method: "GET",
			});

			expect(res.status).toBe(200);
			const json = (await res.json()) as JsonResponse;
			expect(json.status).toBe("OK");
			expect(json.nats).toBe("connected");
		});

		it("should return 503 UNHEALTHY when NATS is disconnected", async () => {
			mockState.isConnected = false;

			const res = await app.request("/health", {
				method: "GET",
			});

			expect(res.status).toBe(503);
			const json = (await res.json()) as JsonResponse;
			expect(json.status).toBe("UNHEALTHY");
			expect(json.nats).toBe("disconnected");
		});
	});

	describe("GET /", () => {
		it("should return service info on root path", async () => {
			const res = await app.request("/", {
				method: "GET",
			});

			expect(res.status).toBe(200);
			const json = (await res.json()) as JsonResponse;
			expect(json.service).toBe("webhook-ingest");
			expect(json.status).toBe("running");
		});
	});

	describe("404 handling", () => {
		it("should return 404 for unknown routes", async () => {
			const res = await app.request("/unknown-route", {
				method: "GET",
			});

			expect(res.status).toBe(404);
			const json = (await res.json()) as JsonResponse;
			expect(json.error).toBe("Not found");
		});
	});
});
