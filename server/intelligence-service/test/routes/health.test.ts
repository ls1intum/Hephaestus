/**
 * Health Route Integration Tests
 *
 * Tests the /health endpoint for proper functionality.
 * Uses Hono's built-in app.request() for fast, reliable testing.
 */
import { describe, expect, it } from "vitest";
import app from "@/app";

describe("GET /health", () => {
	it("should return 200 OK with status OK", async () => {
		const res = await app.request("/health");

		expect(res.status).toBe(200);
		expect(res.headers.get("content-type")).toContain("application/json");

		const body = await res.json();
		expect(body).toEqual({ status: "OK" });
	});

	it("should respond quickly (< 100ms)", async () => {
		const start = performance.now();
		await app.request("/health");
		const duration = performance.now() - start;

		expect(duration).toBeLessThan(100);
	});
});
