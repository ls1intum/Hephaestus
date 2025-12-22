/**
 * Error Handling Integration Tests (Focused)
 *
 * Tests 7 key error scenarios for the chat API.
 * Focus: Header validation, 404s, invalid bodies, and FK violations.
 */

import { afterAll, beforeAll, describe, expect, it } from "vitest";
import app from "@/app";
import { cleanupTestFixtures, createTestFixtures, testUuid } from "../mocks";

describe("Error Handling Integration", () => {
	let fixtures: Awaited<ReturnType<typeof createTestFixtures>>;

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	// ─────────────────────────────────────────────────────────────────────────────
	// 1. Missing x-workspace-id header
	// ─────────────────────────────────────────────────────────────────────────────

	it("should handle missing x-workspace-id header for document creation", async () => {
		const request = new Request("http://localhost/mentor/documents", {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				"x-user-id": String(fixtures.user.id),
				// x-workspace-id intentionally omitted
			},
			body: JSON.stringify({
				title: "Test Document",
				content: "Some content",
				kind: "text",
			}),
		});

		const response = await app.fetch(request);

		expect(response.status).toBe(400);
		const error = (await response.json()) as { error: string };
		expect(error.error).toContain("workspace");
	});

	// ─────────────────────────────────────────────────────────────────────────────
	// ─────────────────────────────────────────────────────────────────────────────
	// 2. Missing x-user-id header (threads are user-scoped for security)
	// ─────────────────────────────────────────────────────────────────────────────

	it("should return 400 without x-user-id header for thread retrieval", async () => {
		const threadId = testUuid();

		const request = new Request(`http://localhost/mentor/threads/${threadId}`, {
			method: "GET",
			headers: {
				"x-workspace-id": String(fixtures.workspace.id),
				// x-user-id intentionally omitted
			},
		});

		const response = await app.fetch(request);

		// Returns 400 because userId is required for authorization
		expect(response.status).toBe(400);
	});

	// ─────────────────────────────────────────────────────────────────────────────
	// 3. Non-existent thread (GET) - Should return 404
	// ─────────────────────────────────────────────────────────────────────────────

	it("should return 404 for non-existent thread", async () => {
		const nonExistentId = testUuid();

		const request = new Request(`http://localhost/mentor/threads/${nonExistentId}`, {
			method: "GET",
			headers: {
				"x-workspace-id": String(fixtures.workspace.id),
				"x-user-id": String(fixtures.user.id),
			},
		});

		const response = await app.fetch(request);

		expect(response.status).toBe(404);
		const error = (await response.json()) as { error: string };
		expect(error.error).toBe("Thread not found");
	});

	// ─────────────────────────────────────────────────────────────────────────────
	// 4. Invalid message body - Empty body
	// ─────────────────────────────────────────────────────────────────────────────

	it("should return 400 for empty request body", async () => {
		const request = new Request("http://localhost/mentor/chat", {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				"x-workspace-id": String(fixtures.workspace.id),
				"x-user-id": String(fixtures.user.id),
			},
			body: "",
		});

		const response = await app.fetch(request);

		expect(response.status).toBe(400);
	});

	// ─────────────────────────────────────────────────────────────────────────────
	// 5. Invalid message body - Malformed parts array
	// ─────────────────────────────────────────────────────────────────────────────

	it("should return 400 for malformed parts array", async () => {
		const request = new Request("http://localhost/mentor/chat", {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				"x-workspace-id": String(fixtures.workspace.id),
				"x-user-id": String(fixtures.user.id),
			},
			body: JSON.stringify({
				id: testUuid(),
				message: {
					id: testUuid(),
					role: "user",
					parts: [{ type: "invalid-type", content: "test" }],
				},
			}),
		});

		const response = await app.fetch(request);

		expect(response.status).toBe(422);
	});

	// ─────────────────────────────────────────────────────────────────────────────
	// 6. Invalid threadId format - Should return 422
	// ─────────────────────────────────────────────────────────────────────────────

	it("should return 422 for non-UUID threadId", async () => {
		const request = new Request("http://localhost/mentor/threads/not-a-valid-uuid", {
			method: "GET",
			headers: {
				"x-workspace-id": String(fixtures.workspace.id),
				"x-user-id": String(fixtures.user.id),
			},
		});

		const response = await app.fetch(request);

		expect(response.status).toBe(422);
		const error = (await response.json()) as { error?: unknown };
		expect(error).toHaveProperty("error");
	});

	// ─────────────────────────────────────────────────────────────────────────────
	// 7. FK violation - Message with non-existent threadId
	// ─────────────────────────────────────────────────────────────────────────────

	it("should handle chat request with non-existent thread gracefully", async () => {
		const nonExistentThreadId = testUuid();

		const request = new Request("http://localhost/mentor/chat", {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				"x-workspace-id": String(fixtures.workspace.id),
				"x-user-id": String(fixtures.user.id),
			},
			body: JSON.stringify({
				id: nonExistentThreadId,
				message: {
					id: testUuid(),
					role: "user",
					parts: [{ type: "text", text: "Hello" }],
				},
			}),
		});

		const response = await app.fetch(request);

		// The chat endpoint creates threads on-demand, so this should work
		// or return a handled error, not a 500 FK violation
		expect([200, 400, 404]).toContain(response.status);
	});
});
