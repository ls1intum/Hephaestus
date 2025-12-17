/**
 * Chat Data Layer Integration Tests
 *
 * Tests the data persistence layer with a real database.
 * Follows AI SDK pattern: test real behavior, mock only the LLM.
 *
 * Prerequisites:
 * - PostgreSQL running with test database
 * - DATABASE_URL environment variable set
 */

import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import {
	createThread,
	getMessagesByThreadId,
	getThreadById,
	saveMessage,
	updateSelectedLeafMessageId,
	updateThreadTitle,
} from "@/mentor/chat/data";
import { cleanupTestFixtures, cleanupTestThread, createTestFixtures, testUuid } from "../mocks";

describe("Chat Data Layer", () => {
	let fixtures: Awaited<ReturnType<typeof createTestFixtures>>;
	const createdThreadIds: string[] = [];

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterEach(async () => {
		// Clean up threads created during tests
		for (const threadId of createdThreadIds) {
			await cleanupTestThread(threadId);
		}
		createdThreadIds.length = 0;
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	describe("createThread", () => {
		it("should create a thread with minimal fields", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			const thread = await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			expect(thread).toBeDefined();
			expect(thread?.id).toBe(threadId);
			expect(thread?.workspaceId).toBe(fixtures.workspace.id);
			expect(thread?.title).toBeNull();
		});

		it("should create a thread with title and userId", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			const thread = await createThread({
				id: threadId,
				title: "Test Chat Title",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});

			expect(thread).toBeDefined();
			expect(thread?.title).toBe("Test Chat Title");
			expect(thread?.userId).toBe(fixtures.user.id);
		});
	});

	describe("getThreadById", () => {
		it("should return null for non-existent thread", async () => {
			const result = await getThreadById(testUuid());
			expect(result).toBeNull();
		});

		it("should return thread after creation", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				title: "Findable Thread",
				workspaceId: fixtures.workspace.id,
			});

			const thread = await getThreadById(threadId);

			expect(thread).not.toBeNull();
			expect(thread?.id).toBe(threadId);
			expect(thread?.title).toBe("Findable Thread");
		});
	});

	describe("updateThreadTitle", () => {
		it("should update an existing thread title", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				title: "Original Title",
				workspaceId: fixtures.workspace.id,
			});

			await updateThreadTitle(threadId, "Updated Title");

			const thread = await getThreadById(threadId);
			expect(thread?.title).toBe("Updated Title");
		});
	});

	describe("saveMessage", () => {
		it("should save a user message with text parts", async () => {
			const threadId = testUuid();
			const messageId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			await saveMessage({
				id: messageId,
				role: "user",
				threadId,
				parts: [{ type: "text", content: { text: "Hello, AI!" } }],
			});

			const messages = await getMessagesByThreadId(threadId);

			expect(messages).toHaveLength(1);
			expect(messages[0]?.id).toBe(messageId);
			expect(messages[0]?.role).toBe("user");
			expect(messages[0]?.parts).toHaveLength(1);
			expect(messages[0]?.parts[0]?.type).toBe("text");
		});

		it("should save an assistant message with multiple parts", async () => {
			const threadId = testUuid();
			const messageId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			await saveMessage({
				id: messageId,
				role: "assistant",
				threadId,
				parts: [
					{ type: "text", content: { text: "Let me help you." } },
					{ type: "reasoning", content: { text: "The user needs assistance." } },
				],
			});

			const messages = await getMessagesByThreadId(threadId);

			expect(messages).toHaveLength(1);
			expect(messages[0]?.parts).toHaveLength(2);
			expect(messages[0]?.parts[0]?.type).toBe("text");
			expect(messages[0]?.parts[1]?.type).toBe("reasoning");
		});

		it("should preserve parent-child message relationships", async () => {
			const threadId = testUuid();
			const userMessageId = testUuid();
			const assistantMessageId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			// User message
			await saveMessage({
				id: userMessageId,
				role: "user",
				threadId,
				parts: [{ type: "text", content: { text: "First message" } }],
			});

			// Assistant reply
			await saveMessage({
				id: assistantMessageId,
				role: "assistant",
				threadId,
				parts: [{ type: "text", content: { text: "Reply" } }],
				parentMessageId: userMessageId,
			});

			const messages = await getMessagesByThreadId(threadId);

			expect(messages).toHaveLength(2);

			const assistantMessage = messages.find((m) => m.id === assistantMessageId);
			expect(assistantMessage?.parentMessageId).toBe(userMessageId);
		});
	});

	describe("getMessagesByThreadId", () => {
		it("should return empty array for thread with no messages", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			const messages = await getMessagesByThreadId(threadId);
			expect(messages).toEqual([]);
		});

		it("should return messages in chronological order", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			// Create messages with explicit timestamps
			const msg1 = testUuid();
			const msg2 = testUuid();
			const msg3 = testUuid();

			await saveMessage({
				id: msg1,
				role: "user",
				threadId,
				parts: [{ type: "text", content: { text: "First" } }],
				createdAt: new Date("2025-01-01T10:00:00Z"),
			});

			await saveMessage({
				id: msg2,
				role: "assistant",
				threadId,
				parts: [{ type: "text", content: { text: "Second" } }],
				createdAt: new Date("2025-01-01T10:00:01Z"),
			});

			await saveMessage({
				id: msg3,
				role: "user",
				threadId,
				parts: [{ type: "text", content: { text: "Third" } }],
				createdAt: new Date("2025-01-01T10:00:02Z"),
			});

			const messages = await getMessagesByThreadId(threadId);

			expect(messages).toHaveLength(3);
			expect(messages[0]?.id).toBe(msg1);
			expect(messages[1]?.id).toBe(msg2);
			expect(messages[2]?.id).toBe(msg3);
		});
	});

	describe("updateSelectedLeafMessageId", () => {
		it("should update the selected leaf message", async () => {
			const threadId = testUuid();
			const messageId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			await saveMessage({
				id: messageId,
				role: "assistant",
				threadId,
				parts: [{ type: "text", content: { text: "Response" } }],
			});

			await updateSelectedLeafMessageId(threadId, messageId);

			const thread = await getThreadById(threadId);
			expect(thread?.selectedLeafMessageId).toBe(messageId);
		});
	});
});
