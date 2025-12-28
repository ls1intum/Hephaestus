/**
 * Concurrent Operations Integration Tests
 *
 * Tests race conditions and concurrent database operations.
 * Critical for ensuring data integrity under load.
 *
 */

import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import {
	createThread,
	getMessagesByThreadId,
	getThreadById,
	saveMessage,
} from "@/mentor/chat/data";
import { cleanupTestFixtures, cleanupTestThread, createTestFixtures, testUuid } from "../mocks";

describe("Concurrent Operations", () => {
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

	describe("thread creation", () => {
		it("should handle concurrent thread creation without conflicts", async () => {
			const ids = [testUuid(), testUuid(), testUuid(), testUuid(), testUuid()];
			for (const id of ids) {
				createdThreadIds.push(id);
			}

			// Create 5 threads concurrently
			await Promise.all(
				ids.map((id) =>
					createThread({
						id,
						workspaceId: fixtures.workspace.id,
						title: `Thread ${id.slice(0, 8)}`,
					}),
				),
			);

			// All should exist
			const results = await Promise.all(ids.map((id) => getThreadById(id)));

			expect(results.filter((r) => r !== null)).toHaveLength(5);
		});

		it("should maintain data integrity with rapid sequential creation", async () => {
			const titles = ["First", "Second", "Third", "Fourth", "Fifth"];
			const ids: string[] = [];

			// Create threads rapidly in sequence
			for (const title of titles) {
				const id = testUuid();
				ids.push(id);
				createdThreadIds.push(id);
				await createThread({
					id,
					workspaceId: fixtures.workspace.id,
					title,
				});
			}

			// Verify each has correct title
			for (let i = 0; i < ids.length; i++) {
				const thread = await getThreadById(ids[i] as string);
				expect(thread?.title).toBe(titles[i]);
			}
		});
	});

	describe("message creation", () => {
		it("should handle concurrent message creation in same thread", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			const messageIds = [testUuid(), testUuid(), testUuid()];
			const baseTime = new Date();

			// Create 3 messages concurrently with different createdAt to ensure ordering
			await Promise.all(
				messageIds.map((id, index) =>
					saveMessage({
						id,
						threadId,
						role: "user",
						parts: [{ type: "text", content: { text: `Message ${index + 1}` } }],
						createdAt: new Date(baseTime.getTime() + index * 1000),
					}),
				),
			);

			const messages = await getMessagesByThreadId(threadId);

			expect(messages).toHaveLength(3);
			// Should be ordered by createdAt
			expect(messages.map((m) => m.id)).toEqual(messageIds);
		});

		it("should preserve parent-child relationships under concurrent writes", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			// Create parent message
			const parentId = testUuid();
			await saveMessage({
				id: parentId,
				threadId,
				role: "user",
				parts: [{ type: "text", content: { text: "Parent message" } }],
			});

			// Create multiple child messages concurrently
			const childIds = [testUuid(), testUuid()];
			await Promise.all(
				childIds.map((id, index) =>
					saveMessage({
						id,
						threadId,
						role: "assistant",
						parts: [{ type: "text", content: { text: `Child ${index + 1}` } }],
						parentMessageId: parentId,
					}),
				),
			);

			const messages = await getMessagesByThreadId(threadId);
			const children = messages.filter((m) => m.parentMessageId === parentId);

			expect(children).toHaveLength(2);
		});
	});

	describe("read/write consistency", () => {
		it("should see consistent state during concurrent read and write", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
				title: "Initial Title",
			});

			// Create a message first
			await saveMessage({
				id: testUuid(),
				threadId,
				role: "user",
				parts: [{ type: "text", content: { text: "Initial message" } }],
			});

			// Concurrent read and write
			const [readResult, _writeResult] = await Promise.all([
				getMessagesByThreadId(threadId),
				saveMessage({
					id: testUuid(),
					threadId,
					role: "assistant",
					parts: [{ type: "text", content: { text: "New message" } }],
				}),
			]);

			// Read should see at least the initial message (consistency)
			expect(readResult.length).toBeGreaterThanOrEqual(1);

			// Final read should see both
			const finalMessages = await getMessagesByThreadId(threadId);
			expect(finalMessages).toHaveLength(2);
		});

		it("should handle rapid message retrieval during writes", async () => {
			const threadId = testUuid();
			createdThreadIds.push(threadId);

			await createThread({
				id: threadId,
				workspaceId: fixtures.workspace.id,
			});

			// Write messages while constantly reading
			const writes = Array.from({ length: 5 }, (_, i) =>
				saveMessage({
					id: testUuid(),
					threadId,
					role: i % 2 === 0 ? "user" : "assistant",
					parts: [{ type: "text", content: { text: `Message ${i}` } }],
					createdAt: new Date(Date.now() + i * 100),
				}),
			);

			const reads = Array.from({ length: 5 }, () => getMessagesByThreadId(threadId));

			await Promise.all([...writes, ...reads]);

			// Final state should have all 5 messages
			const finalMessages = await getMessagesByThreadId(threadId);
			expect(finalMessages).toHaveLength(5);
		});
	});
});
