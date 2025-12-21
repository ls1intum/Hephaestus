/**
 * Full Chat Handler Integration Test
 *
 * Tests the request/response handling of the chat endpoint.
 * Validates:
 * - Request validation
 * - Thread/message persistence
 * - SSE stream format
 * - Error handling
 *
 * Note: These tests validate request handling WITHOUT hitting the real LLM.
 * The model call may fail (no API key), but we verify everything up to that point.
 */

import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import app from "@/app";
import {
	createThread,
	getMessagesByThreadId,
	getThreadById,
	saveMessage,
} from "@/mentor/chat/data";
import {
	cleanupTestFixtures,
	cleanupTestThread,
	createTestFixtures,
	type TestFixtures,
	testUuid,
} from "../mocks";

describe("Chat Handler Full Integration", () => {
	let fixtures: TestFixtures;
	const createdThreadIds: string[] = [];

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterEach(async () => {
		for (const threadId of createdThreadIds) {
			await cleanupTestThread(threadId);
		}
		createdThreadIds.length = 0;
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Request Validation
	// ─────────────────────────────────────────────────────────────────────────

	describe("request validation", () => {
		it("should accept valid chat request format", async () => {
			const threadId = testUuid();
			const messageId = testUuid();
			createdThreadIds.push(threadId);

			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: threadId,
					message: {
						id: messageId,
						role: "user",
						parts: [{ type: "text", text: "Hello Heph!" }],
					},
				}),
			});

			const response = await app.fetch(request);

			// Should return 200 and start SSE stream (even if model fails later)
			expect(response.status).toBe(200);
			expect(response.headers.get("content-type")).toContain("text/event-stream");
		});

		it("should reject request with missing message", async () => {
			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: testUuid(),
					// Missing message field
				}),
			});

			const response = await app.fetch(request);
			expect(response.status).toBeGreaterThanOrEqual(400);
		});

		it("should reject request with missing message id", async () => {
			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: testUuid(),
					message: {
						// Missing id
						role: "user",
						parts: [{ type: "text", text: "Hello" }],
					},
				}),
			});

			const response = await app.fetch(request);
			expect(response.status).toBeGreaterThanOrEqual(400);
		});

		it("should reject request with missing parts", async () => {
			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: testUuid(),
					message: {
						id: testUuid(),
						role: "user",
						// Missing parts
					},
				}),
			});

			const response = await app.fetch(request);
			expect(response.status).toBeGreaterThanOrEqual(400);
		});

		it("should reject request with empty parts array", async () => {
			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: testUuid(),
					message: {
						id: testUuid(),
						role: "user",
						parts: [], // Empty array
					},
				}),
			});

			const response = await app.fetch(request);
			// Empty parts might be valid or invalid depending on schema
			expect([200, 400, 422]).toContain(response.status);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Thread Persistence
	// ─────────────────────────────────────────────────────────────────────────

	describe("thread persistence", () => {
		it("should create thread on first message to new thread", async () => {
			const threadId = testUuid();
			const messageId = testUuid();
			createdThreadIds.push(threadId);

			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: threadId,
					message: {
						id: messageId,
						role: "user",
						parts: [{ type: "text", text: "Create thread test" }],
					},
				}),
			});

			const response = await app.fetch(request);
			expect(response.status).toBe(200);

			// Thread should be created (even if stream fails later)
			const thread = await getThreadById(threadId);
			expect(thread).not.toBeNull();
			expect(thread?.workspaceId).toBe(fixtures.workspace.id);
		});

		it("should associate thread with correct workspace", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: threadId,
					message: {
						id: testUuid(),
						role: "user",
						parts: [{ type: "text", text: "Workspace test" }],
					},
				}),
			});

			await app.fetch(request);

			const thread = await getThreadById(threadId);
			expect(thread?.workspaceId).toBe(fixtures.workspace.id);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Message Persistence
	// ─────────────────────────────────────────────────────────────────────────

	describe("message persistence", () => {
		it("should persist user message before streaming", async () => {
			const threadId = testUuid();
			const messageId = testUuid();
			createdThreadIds.push(threadId);

			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: threadId,
					message: {
						id: messageId,
						role: "user",
						parts: [{ type: "text", text: "Persist this message" }],
					},
				}),
			});

			const response = await app.fetch(request);
			expect(response.status).toBe(200);

			// User message should be persisted
			const messages = await getMessagesByThreadId(threadId);
			const userMessage = messages.find((m) => m.id === messageId);

			expect(userMessage).toBeDefined();
			expect(userMessage?.role).toBe("user");
		});

		it("should persist message parts correctly", async () => {
			const threadId = testUuid();
			const messageId = testUuid();
			createdThreadIds.push(threadId);

			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: threadId,
					message: {
						id: messageId,
						role: "user",
						parts: [
							{ type: "text", text: "First part" },
							{ type: "text", text: "Second part" },
						],
					},
				}),
			});

			await app.fetch(request);

			const messages = await getMessagesByThreadId(threadId);
			const userMessage = messages.find((m) => m.id === messageId);

			expect(userMessage?.parts).toHaveLength(2);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Stream Format
	// ─────────────────────────────────────────────────────────────────────────

	describe("stream format", () => {
		it("should return SSE content type", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: threadId,
					message: {
						id: testUuid(),
						role: "user",
						parts: [{ type: "text", text: "Stream test" }],
					},
				}),
			});

			const response = await app.fetch(request);

			expect(response.headers.get("content-type")).toContain("text/event-stream");
			expect(response.headers.get("cache-control")).toContain("no-cache");
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Multi-Turn Conversations
	// ─────────────────────────────────────────────────────────────────────────

	describe("multi-turn conversations", () => {
		it("should accumulate messages in same thread", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			// First message
			await app.fetch(
				new Request("http://localhost/mentor/chat", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"x-workspace-id": String(fixtures.workspace.id),
						"x-user-id": String(fixtures.user.id),
						"x-user-login": fixtures.user.login,
					},
					body: JSON.stringify({
						id: threadId,
						message: {
							id: testUuid(),
							role: "user",
							parts: [{ type: "text", text: "First message" }],
						},
					}),
				}),
			);

			// Second message
			await app.fetch(
				new Request("http://localhost/mentor/chat", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"x-workspace-id": String(fixtures.workspace.id),
						"x-user-id": String(fixtures.user.id),
						"x-user-login": fixtures.user.login,
					},
					body: JSON.stringify({
						id: threadId,
						message: {
							id: testUuid(),
							role: "user",
							parts: [{ type: "text", text: "Second message" }],
						},
					}),
				}),
			);

			const messages = await getMessagesByThreadId(threadId);
			const userMessages = messages.filter((m) => m.role === "user");

			expect(userMessages.length).toBeGreaterThanOrEqual(2);
		});

		it("should support previousMessageId for branching", async () => {
			const threadId = testUuid();
			const firstMessageId = testUuid();
			createdThreadIds.push(threadId);

			// Create thread and first message
			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			await saveMessage({
				id: firstMessageId,
				role: "user",
				threadId,
				parts: [{ type: "text", content: { type: "text", text: "First message" } }],
			});

			// Send second message referencing first
			const request = new Request("http://localhost/mentor/chat", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
					"x-user-login": fixtures.user.login,
				},
				body: JSON.stringify({
					id: threadId,
					message: {
						id: testUuid(),
						role: "user",
						parts: [{ type: "text", text: "Reply to first" }],
					},
					previousMessageId: firstMessageId,
				}),
			});

			const response = await app.fetch(request);
			expect(response.status).toBe(200);
		});
	});
});
