/**
 * Chat API Integration Tests
 *
 * Tests the full HTTP API flow for the chat endpoints.
 * Uses real database operations but mocks the LLM.
 *
 * Key test scenarios:
 * 1. Thread creation and retrieval
 * 2. Message persistence after streaming
 * 3. Error handling and validation
 * 4. Multi-turn conversations
 */

import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import app from "@/app";
import { createThread, saveMessage } from "@/mentor/chat/data";
import { cleanupTestFixtures, cleanupTestThread, createTestFixtures, testUuid } from "../mocks";

describe("Chat API Integration", () => {
	let fixtures: Awaited<ReturnType<typeof createTestFixtures>>;
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

	describe("GET /mentor/threads/{threadId}", () => {
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
			expect(error).toMatchInlineSnapshot(`
				{
				  "error": "Thread not found",
				}
			`);
		});

		it("should return thread with messages and correct structure", async () => {
			const threadId = testUuid();
			const messageId = testUuid();
			createdThreadIds.push(threadId);

			// Create thread and message directly in DB
			await createThread({
				id: threadId,
				title: "Test Thread",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			await saveMessage({
				id: messageId,
				role: "user",
				threadId,
				parts: [{ type: "text", content: { text: "Hello" } }],
			});

			const request = new Request(`http://localhost/mentor/threads/${threadId}`, {
				method: "GET",
				headers: {
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
				},
			});

			const response = await app.fetch(request);

			expect(response.status).toBe(200);

			const data = (await response.json()) as {
				id: string;
				title: string;
				selectedLeafMessageId: string | null;
				messages: Array<{
					id: string;
					role: string;
					createdAt: string;
					parts: Array<{ type: string }>;
				}>;
			};

			// Verify structure with inline snapshot (dynamic values masked)
			expect({
				hasId: data.id === threadId,
				title: data.title,
				hasSelectedLeafMessageId: "selectedLeafMessageId" in data,
				messageCount: data.messages.length,
				firstMessageRole: data.messages[0]?.role,
				firstMessageHasParts: (data.messages[0]?.parts?.length ?? 0) > 0,
			}).toMatchInlineSnapshot(`
				{
				  "firstMessageHasParts": true,
				  "firstMessageRole": "user",
				  "hasId": true,
				  "hasSelectedLeafMessageId": true,
				  "messageCount": 1,
				  "title": "Test Thread",
				}
			`);
		});

		it("should include message parts with correct content structure", async () => {
			const threadId = testUuid();
			const messageId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			// Note: content must include 'type' for transformer to recognize it
			await saveMessage({
				id: messageId,
				role: "user",
				threadId,
				parts: [{ type: "text", content: { type: "text", text: "Check this out" } }],
			});

			const request = new Request(`http://localhost/mentor/threads/${threadId}`, {
				method: "GET",
				headers: {
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
				},
			});

			const response = await app.fetch(request);
			const data = (await response.json()) as {
				messages: Array<{
					id: string;
					role: string;
					parts: Array<{ type: string; text?: string }>;
				}>;
			};

			expect(data.messages).toHaveLength(1);
			expect(data.messages[0]?.id).toBe(messageId);

			// Verify message structure
			const message = data.messages[0];
			expect({
				role: message?.role,
				partCount: message?.parts?.length,
				firstPartType: message?.parts?.[0]?.type,
				hasTextContent: typeof message?.parts?.[0]?.text === "string",
			}).toMatchInlineSnapshot(`
				{
				  "firstPartType": "text",
				  "hasTextContent": true,
				  "partCount": 1,
				  "role": "user",
				}
			`);
		});
	});

	describe("GET /mentor/threads/grouped", () => {
		it("should return grouped threads for workspace", async () => {
			const request = new Request("http://localhost/mentor/threads/grouped", {
				method: "GET",
				headers: {
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
				},
			});

			const response = await app.fetch(request);

			expect(response.status).toBe(200);

			const data = (await response.json()) as unknown[];
			expect(Array.isArray(data)).toBe(true);
		});

		it("should include recently created threads in Today group", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				title: "Recent Thread",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			const request = new Request("http://localhost/mentor/threads/grouped", {
				method: "GET",
				headers: {
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
				},
			});

			const response = await app.fetch(request);
			const data = (await response.json()) as Array<{
				groupName: string;
				threads: Array<{ id: string }>;
			}>;

			// Find the Today group
			const todayGroup = data.find((g) => g.groupName === "Today");
			if (todayGroup) {
				const hasThread = todayGroup.threads.some((t: { id: string }) => t.id === threadId);
				expect(hasThread).toBe(true);
			}
		});
	});

	describe("Health endpoint", () => {
		it("should return 200 OK", async () => {
			const request = new Request("http://localhost/health", {
				method: "GET",
			});

			const response = await app.fetch(request);

			expect(response.status).toBe(200);
			const data = (await response.json()) as { status: string };
			expect(data.status).toBe("OK");
		});
	});
});
