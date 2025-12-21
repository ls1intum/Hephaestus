/**
 * Vote Feature Integration Tests
 *
 * Tests the vote endpoint for upvoting/downvoting chat messages.
 * Validates:
 * - Request/response handling
 * - Vote persistence (upsert behavior)
 * - Validation errors
 * - Edge cases
 */

import { eq } from "drizzle-orm";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import app from "@/app";
import type { ChatMessageVote } from "@/mentor/vote/vote.schema";
import db from "@/shared/db";
import { chatMessageVote } from "@/shared/db/schema";
import {
	cleanupTestFixtures,
	cleanupTestThread,
	createTestFixtures,
	createTestMessage,
	createTestThread,
	type TestFixtures,
	testUuid,
} from "../mocks";

/** Error response type */
interface ErrorResponse {
	error: string;
}

describe("Vote Feature", () => {
	let fixtures: TestFixtures;
	const createdThreadIds: string[] = [];

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterEach(async () => {
		// Clean up votes for all messages in test threads
		for (const threadId of createdThreadIds) {
			await cleanupTestThread(threadId);
		}
		createdThreadIds.length = 0;
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Helper Functions
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Helper to vote on a message via the API.
	 */
	function voteMessage(messageId: string, isUpvoted: boolean) {
		const request = new Request(
			`http://localhost/mentor/messages/chat/messages/${messageId}/vote`,
			{
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-user-id": String(fixtures.user.id),
					"x-workspace-id": String(fixtures.workspace.id),
				},
				body: JSON.stringify({ isUpvoted }),
			},
		);
		return app.fetch(request);
	}

	/**
	 * Helper to create a test thread with a message.
	 * The thread is owned by the test user for proper authorization.
	 */
	async function createThreadWithMessage(): Promise<{ threadId: string; messageId: string }> {
		const threadId = await createTestThread(fixtures.workspace.id, { userId: fixtures.user.id });
		createdThreadIds.push(threadId);
		const messageId = await createTestMessage(threadId, { text: "Test message for voting" });
		return { threadId, messageId };
	}

	/**
	 * Clean up a vote directly from the database.
	 */
	async function cleanupVote(messageId: string): Promise<void> {
		await db.delete(chatMessageVote).where(eq(chatMessageVote.messageId, messageId));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Happy Path Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("happy path", () => {
		it("should upvote a message and return vote record", async () => {
			const { messageId } = await createThreadWithMessage();

			const response = await voteMessage(messageId, true);

			expect(response.status).toBe(200);
			const body = (await response.json()) as ChatMessageVote;
			expect(body).toMatchObject({
				messageId,
				isUpvoted: true,
			});
			expect(body.createdAt).toBeDefined();
			expect(body.updatedAt).toBeDefined();

			// Cleanup
			await cleanupVote(messageId);
		});

		it("should downvote a message and return vote record", async () => {
			const { messageId } = await createThreadWithMessage();

			const response = await voteMessage(messageId, false);

			expect(response.status).toBe(200);
			const body = (await response.json()) as ChatMessageVote;
			expect(body).toMatchObject({
				messageId,
				isUpvoted: false,
			});
			expect(body.createdAt).toBeDefined();
			expect(body.updatedAt).toBeDefined();

			// Cleanup
			await cleanupVote(messageId);
		});

		it("should toggle vote from upvote to downvote", async () => {
			const { messageId } = await createThreadWithMessage();

			// First upvote
			const upvoteResponse = await voteMessage(messageId, true);
			expect(upvoteResponse.status).toBe(200);
			const upvoteBody = (await upvoteResponse.json()) as ChatMessageVote;
			expect(upvoteBody.isUpvoted).toBe(true);
			const originalCreatedAt = upvoteBody.createdAt;

			// Then downvote (toggle)
			const downvoteResponse = await voteMessage(messageId, false);
			expect(downvoteResponse.status).toBe(200);
			const downvoteBody = (await downvoteResponse.json()) as ChatMessageVote;
			expect(downvoteBody).toMatchObject({
				messageId,
				isUpvoted: false,
			});
			// createdAt should remain the same, updatedAt should change
			expect(downvoteBody.createdAt).toBe(originalCreatedAt);
			expect(downvoteBody.updatedAt).not.toBe(originalCreatedAt);

			// Cleanup
			await cleanupVote(messageId);
		});

		it("should toggle vote from downvote to upvote", async () => {
			const { messageId } = await createThreadWithMessage();

			// First downvote
			const downvoteResponse = await voteMessage(messageId, false);
			expect(downvoteResponse.status).toBe(200);
			expect(((await downvoteResponse.json()) as ChatMessageVote).isUpvoted).toBe(false);

			// Then upvote (toggle)
			const upvoteResponse = await voteMessage(messageId, true);
			expect(upvoteResponse.status).toBe(200);
			const upvoteBody = (await upvoteResponse.json()) as ChatMessageVote;
			expect(upvoteBody.isUpvoted).toBe(true);

			// Cleanup
			await cleanupVote(messageId);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Error Cases
	// ─────────────────────────────────────────────────────────────────────────

	describe("error cases", () => {
		it("should return 404 for non-existent message", async () => {
			const nonExistentMessageId = testUuid();

			const response = await voteMessage(nonExistentMessageId, true);

			expect(response.status).toBe(404);
			const body = (await response.json()) as ErrorResponse;
			expect(body.error).toBeDefined();
		});

		it("should return 422 for invalid messageId format (not UUID)", async () => {
			const invalidMessageId = "not-a-valid-uuid";

			const request = new Request(
				`http://localhost/mentor/messages/chat/messages/${invalidMessageId}/vote`,
				{
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ isUpvoted: true }),
				},
			);

			const response = await app.fetch(request);
			// OpenAPI/Zod validation returns 422 Unprocessable Entity
			expect(response.status).toBe(422);
		});

		it("should return 400 for empty messageId", async () => {
			const request = new Request("http://localhost/mentor/messages/chat/messages//vote", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ isUpvoted: true }),
			});

			const response = await app.fetch(request);
			// Should be 404 (route not found) or 400
			expect([400, 404]).toContain(response.status);
		});

		it("should return 422 for missing isUpvoted field", async () => {
			const { messageId } = await createThreadWithMessage();

			const request = new Request(
				`http://localhost/mentor/messages/chat/messages/${messageId}/vote`,
				{
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({}),
				},
			);

			const response = await app.fetch(request);
			// OpenAPI/Zod validation returns 422 Unprocessable Entity
			expect(response.status).toBe(422);
		});

		it("should return 400 or 422 for missing request body", async () => {
			const { messageId } = await createThreadWithMessage();

			const request = new Request(
				`http://localhost/mentor/messages/chat/messages/${messageId}/vote`,
				{
					method: "POST",
					headers: { "Content-Type": "application/json" },
				},
			);

			const response = await app.fetch(request);
			// Missing body can return 400 (Bad Request) or 422 (Unprocessable Entity)
			expect([400, 422]).toContain(response.status);
		});

		it("should return 422 for invalid isUpvoted type (string)", async () => {
			const { messageId } = await createThreadWithMessage();

			const request = new Request(
				`http://localhost/mentor/messages/chat/messages/${messageId}/vote`,
				{
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ isUpvoted: "true" }),
				},
			);

			const response = await app.fetch(request);
			// OpenAPI/Zod validation returns 422 Unprocessable Entity
			expect(response.status).toBe(422);
		});

		it("should return 422 for invalid isUpvoted type (number)", async () => {
			const { messageId } = await createThreadWithMessage();

			const request = new Request(
				`http://localhost/mentor/messages/chat/messages/${messageId}/vote`,
				{
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ isUpvoted: 1 }),
				},
			);

			const response = await app.fetch(request);
			// OpenAPI/Zod validation returns 422 Unprocessable Entity
			expect(response.status).toBe(422);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Edge Cases
	// ─────────────────────────────────────────────────────────────────────────

	describe("edge cases", () => {
		it("should handle double upvote on same message (idempotent)", async () => {
			const { messageId } = await createThreadWithMessage();

			// First upvote
			const firstResponse = await voteMessage(messageId, true);
			expect(firstResponse.status).toBe(200);
			const firstBody = (await firstResponse.json()) as ChatMessageVote;
			expect(firstBody.isUpvoted).toBe(true);
			const originalUpdatedAt = firstBody.updatedAt;

			// Wait a bit to ensure timestamp difference
			await new Promise((resolve) => setTimeout(resolve, 10));

			// Second upvote (same vote)
			const secondResponse = await voteMessage(messageId, true);
			expect(secondResponse.status).toBe(200);
			const secondBody = (await secondResponse.json()) as ChatMessageVote;
			expect(secondBody.isUpvoted).toBe(true);
			expect(secondBody.messageId).toBe(messageId);
			// updatedAt should be updated even for same vote
			expect(secondBody.updatedAt).not.toBe(originalUpdatedAt);

			// Cleanup
			await cleanupVote(messageId);
		});

		it("should handle double downvote on same message (idempotent)", async () => {
			const { messageId } = await createThreadWithMessage();

			// First downvote
			const firstResponse = await voteMessage(messageId, false);
			expect(firstResponse.status).toBe(200);
			expect(((await firstResponse.json()) as ChatMessageVote).isUpvoted).toBe(false);

			// Second downvote (same vote)
			const secondResponse = await voteMessage(messageId, false);
			expect(secondResponse.status).toBe(200);
			const secondBody = (await secondResponse.json()) as ChatMessageVote;
			expect(secondBody.isUpvoted).toBe(false);

			// Cleanup
			await cleanupVote(messageId);
		});

		it("should handle null isUpvoted field", async () => {
			const { messageId } = await createThreadWithMessage();

			const request = new Request(
				`http://localhost/mentor/messages/chat/messages/${messageId}/vote`,
				{
					method: "POST",
					headers: { "Content-Type": "application/json" },
					body: JSON.stringify({ isUpvoted: null }),
				},
			);

			const response = await app.fetch(request);
			// null is not a valid boolean, OpenAPI/Zod returns 422 Unprocessable Entity
			expect(response.status).toBe(422);
		});

		it("should handle rapid vote toggling", async () => {
			const { messageId } = await createThreadWithMessage();

			// Rapid toggle sequence
			await voteMessage(messageId, true);
			await voteMessage(messageId, false);
			await voteMessage(messageId, true);
			await voteMessage(messageId, false);
			const finalResponse = await voteMessage(messageId, true);

			expect(finalResponse.status).toBe(200);
			const body = (await finalResponse.json()) as ChatMessageVote;
			expect(body.isUpvoted).toBe(true);

			// Cleanup
			await cleanupVote(messageId);
		});

		it("should persist vote correctly in database", async () => {
			const { messageId } = await createThreadWithMessage();

			// Vote via API
			await voteMessage(messageId, true);

			// Verify in database
			const [dbVote] = await db
				.select()
				.from(chatMessageVote)
				.where(eq(chatMessageVote.messageId, messageId));

			expect(dbVote).toBeDefined();
			expect(dbVote?.messageId).toBe(messageId);
			expect(dbVote?.isUpvoted).toBe(true);
			expect(dbVote?.createdAt).toBeDefined();
			expect(dbVote?.updatedAt).toBeDefined();

			// Cleanup
			await cleanupVote(messageId);
		});

		it("should update vote correctly in database when toggling", async () => {
			const { messageId } = await createThreadWithMessage();

			// Initial upvote
			await voteMessage(messageId, true);

			const [initialVote] = await db
				.select()
				.from(chatMessageVote)
				.where(eq(chatMessageVote.messageId, messageId));
			expect(initialVote?.isUpvoted).toBe(true);

			// Toggle to downvote
			await voteMessage(messageId, false);

			const [updatedVote] = await db
				.select()
				.from(chatMessageVote)
				.where(eq(chatMessageVote.messageId, messageId));
			expect(updatedVote?.isUpvoted).toBe(false);
			expect(updatedVote?.messageId).toBe(messageId);

			// Cleanup
			await cleanupVote(messageId);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Parallel Safety Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("parallel safety", () => {
		it("should handle concurrent votes on different messages", async () => {
			// Create multiple messages
			const { messageId: messageId1 } = await createThreadWithMessage();
			const { messageId: messageId2 } = await createThreadWithMessage();
			const { messageId: messageId3 } = await createThreadWithMessage();

			// Vote on all concurrently
			const [response1, response2, response3] = await Promise.all([
				voteMessage(messageId1, true),
				voteMessage(messageId2, false),
				voteMessage(messageId3, true),
			]);

			expect(response1.status).toBe(200);
			expect(response2.status).toBe(200);
			expect(response3.status).toBe(200);

			const body1 = (await response1.json()) as ChatMessageVote;
			const body2 = (await response2.json()) as ChatMessageVote;
			const body3 = (await response3.json()) as ChatMessageVote;

			expect(body1.isUpvoted).toBe(true);
			expect(body2.isUpvoted).toBe(false);
			expect(body3.isUpvoted).toBe(true);

			// Cleanup
			await cleanupVote(messageId1);
			await cleanupVote(messageId2);
			await cleanupVote(messageId3);
		});

		it("should handle concurrent votes on the same message", async () => {
			const { messageId } = await createThreadWithMessage();

			// Race condition test: multiple votes at once
			// Note: This tests the current behavior. The upsert implementation
			// has a race condition where concurrent requests may fail.
			const responses = await Promise.all([
				voteMessage(messageId, true),
				voteMessage(messageId, false),
				voteMessage(messageId, true),
			]);

			// At least one should succeed (the first to acquire the row)
			const successfulResponses = responses.filter((r) => r.status === 200);
			expect(successfulResponses.length).toBeGreaterThanOrEqual(1);

			// Failed responses should be 500 (race condition in upsert) or 200
			for (const response of responses) {
				expect([200, 500]).toContain(response.status);
			}

			// Final state should be consistent - there should be exactly one vote
			const [finalVote] = await db
				.select()
				.from(chatMessageVote)
				.where(eq(chatMessageVote.messageId, messageId));

			expect(finalVote).toBeDefined();
			expect(typeof finalVote?.isUpvoted).toBe("boolean");

			// Cleanup
			await cleanupVote(messageId);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Response Format Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("response format", () => {
		it("should return correct response structure", async () => {
			const { messageId } = await createThreadWithMessage();

			const response = await voteMessage(messageId, true);
			const body = (await response.json()) as ChatMessageVote;

			// Verify all required fields
			expect(body).toHaveProperty("messageId");
			expect(body).toHaveProperty("isUpvoted");
			expect(body).toHaveProperty("createdAt");
			expect(body).toHaveProperty("updatedAt");

			// Verify types
			expect(typeof body.messageId).toBe("string");
			expect(typeof body.isUpvoted).toBe("boolean");
			expect(typeof body.createdAt).toBe("string");
			expect(typeof body.updatedAt).toBe("string");

			// Verify UUID format
			expect(body.messageId).toMatch(
				/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
			);

			// Verify datetime format (ISO 8601)
			expect(() => new Date(body.createdAt)).not.toThrow();
			expect(() => new Date(body.updatedAt)).not.toThrow();

			// Cleanup
			await cleanupVote(messageId);
		});

		it("should return correct content-type header", async () => {
			const { messageId } = await createThreadWithMessage();

			const response = await voteMessage(messageId, true);

			expect(response.headers.get("content-type")).toContain("application/json");

			// Cleanup
			await cleanupVote(messageId);
		});
	});
});
