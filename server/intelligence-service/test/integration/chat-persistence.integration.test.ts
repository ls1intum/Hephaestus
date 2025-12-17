/**
 * Chat Persistence Module Integration Tests
 *
 * Tests the persistence helper functions that bridge between
 * the handler and the data layer.
 */

import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import {
	loadOrCreateThread,
	persistAssistantMessage,
	persistUserMessage,
	updateTitleIfNeeded,
} from "@/mentor/chat/chat.persistence";
import { getMessagesByThreadId, getThreadById } from "@/mentor/chat/data";
import { cleanupTestFixtures, cleanupTestThread, createTestFixtures, testUuid } from "../mocks";

// Simple no-op logger for tests
const testLogger = {
	error: () => {
		/* noop */
	},
	warn: () => {
		/* noop */
	},
	info: () => {
		/* noop */
	},
	debug: () => {
		/* noop */
	},
};

describe("Chat Persistence Module", () => {
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

	describe("loadOrCreateThread", () => {
		it("should return error when workspaceId is missing", async () => {
			const threadId = testUuid();
			const message = {
				id: testUuid(),
				role: "user" as const,
				parts: [{ type: "text" as const, text: "Hello" }],
			};

			const result = await loadOrCreateThread(threadId, null, message, testLogger);

			expect(result.success).toBe(false);
			if (!result.success) {
				expect(result.error).toContain("Missing workspace ID");
			}
		});

		it("should create new thread when it does not exist", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			const message = {
				id: testUuid(),
				role: "user" as const,
				parts: [{ type: "text" as const, text: "Hello, this is my first message" }],
			};

			const result = await loadOrCreateThread(threadId, fixtures.workspace.id, message, testLogger);

			expect(result.success).toBe(true);
			if (result.success) {
				expect(result.data?.id).toBe(threadId);
				expect(result.data?.title).toBe("Hello, this is my first message");
			}
		});

		it("should return existing thread when it exists", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			// Create thread first
			const { createThread } = await import("@/mentor/chat/data");
			await createThread({
				id: threadId,
				title: "Existing Thread",
				workspaceId: fixtures.workspace.id,
			});

			const message = {
				id: testUuid(),
				role: "user" as const,
				parts: [{ type: "text" as const, text: "Follow-up message" }],
			};

			const result = await loadOrCreateThread(threadId, fixtures.workspace.id, message, testLogger);

			expect(result.success).toBe(true);
			if (result.success) {
				expect(result.data?.id).toBe(threadId);
				expect(result.data?.title).toBe("Existing Thread"); // Not overwritten
			}
		});
	});

	describe("persistUserMessage", () => {
		it("should save a user message to the database", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			// Create thread first
			const { createThread } = await import("@/mentor/chat/data");
			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			const message = {
				id: testUuid(),
				role: "user" as const,
				parts: [{ type: "text" as const, text: "User question" }],
			};

			const result = await persistUserMessage(threadId, message, undefined, testLogger);

			expect(result.success).toBe(true);

			// Verify in database
			const messages = await getMessagesByThreadId(threadId);
			expect(messages).toHaveLength(1);
			expect(messages[0]?.role).toBe("user");
		});

		it("should preserve parent message relationship", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			const { createThread, saveMessage } = await import("@/mentor/chat/data");
			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			// Create parent message
			const parentId = testUuid();
			await saveMessage({
				id: parentId,
				role: "assistant",
				threadId,
				parts: [{ type: "text", content: { text: "Previous response" } }],
			});

			// Create follow-up message
			const message = {
				id: testUuid(),
				role: "user" as const,
				parts: [{ type: "text" as const, text: "Follow-up" }],
			};

			const result = await persistUserMessage(threadId, message, parentId, testLogger);

			expect(result.success).toBe(true);

			const messages = await getMessagesByThreadId(threadId);
			const followUp = messages.find((m) => m.id === message.id);
			expect(followUp?.parentMessageId).toBe(parentId);
		});
	});

	describe("persistAssistantMessage", () => {
		it("should save an assistant message with parts", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			const { createThread, saveMessage } = await import("@/mentor/chat/data");
			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			// Create parent user message
			const userMessageId = testUuid();
			await saveMessage({
				id: userMessageId,
				role: "user",
				threadId,
				parts: [{ type: "text", content: { text: "Question" } }],
			});

			// Persist assistant message
			const assistantId = testUuid();
			const parts = [
				{ type: "text", text: "Here is my response." },
				{ type: "reasoning", text: "I analyzed the question." },
			];

			const result = await persistAssistantMessage(
				threadId,
				assistantId,
				parts,
				userMessageId,
				testLogger,
			);

			expect(result.success).toBe(true);

			// Verify in database
			const messages = await getMessagesByThreadId(threadId);
			const assistantMessage = messages.find((m) => m.id === assistantId);

			expect(assistantMessage).toBeDefined();
			expect(assistantMessage?.role).toBe("assistant");
			expect(assistantMessage?.parentMessageId).toBe(userMessageId);
			expect(assistantMessage?.parts.length).toBeGreaterThanOrEqual(1);
		});

		it("should filter out data-* ephemeral parts", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			const { createThread, saveMessage } = await import("@/mentor/chat/data");
			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			const userMessageId = testUuid();
			await saveMessage({
				id: userMessageId,
				role: "user",
				threadId,
				parts: [{ type: "text", content: { text: "Question" } }],
			});

			const assistantId = testUuid();
			const parts = [
				{ type: "text", text: "Response" },
				{ type: "data-document-create", id: "doc-1" }, // Should be filtered
				{ type: "data-delta", delta: "..." }, // Should be filtered
			];

			await persistAssistantMessage(threadId, assistantId, parts, userMessageId, testLogger);

			const messages = await getMessagesByThreadId(threadId);
			const assistantMessage = messages.find((m) => m.id === assistantId);

			// Only the text part should be saved
			const textParts = assistantMessage?.parts.filter((p) => p.type === "text");
			const dataParts = assistantMessage?.parts.filter((p) => p.type.startsWith("data-"));

			expect(textParts?.length).toBe(1);
			expect(dataParts?.length).toBe(0);
		});
	});

	describe("updateTitleIfNeeded", () => {
		it("should not update title if already set", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			const { createThread } = await import("@/mentor/chat/data");
			await createThread({
				id: threadId,
				title: "Already Has Title",
				workspaceId: fixtures.workspace.id,
			});

			const message = {
				id: testUuid(),
				role: "user" as const,
				parts: [{ type: "text" as const, text: "New message that should not replace title" }],
			};

			await updateTitleIfNeeded(threadId, "Already Has Title", message, testLogger);

			const thread = await getThreadById(threadId);
			expect(thread?.title).toBe("Already Has Title");
		});

		it("should update title if not set", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			const { createThread } = await import("@/mentor/chat/data");
			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			const message = {
				id: testUuid(),
				role: "user" as const,
				parts: [{ type: "text" as const, text: "This should become the title" }],
			};

			await updateTitleIfNeeded(threadId, null, message, testLogger);

			const thread = await getThreadById(threadId);
			expect(thread?.title).toBe("This should become the title");
		});
	});
});
