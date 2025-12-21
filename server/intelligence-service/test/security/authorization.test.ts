/**
 * Authorization Tests for Intelligence Service
 *
 * Tests that resources are properly isolated by user and workspace.
 * Verifies that users cannot access other users' data.
 */

import { v4 as uuidv4 } from "uuid";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import app from "@/app";
import { createDocument } from "@/mentor/documents/data";
import {
	cleanupTestFixtures,
	createTestFixtures,
	createTestMessage,
	createTestThread,
	type TestFixtures,
} from "../mocks";

describe("Authorization", () => {
	let fixtures: TestFixtures;
	const createdDocIds: string[] = [];
	const createdThreadIds: string[] = [];

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	afterEach(async () => {
		// Cleanup is handled by test fixtures
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Helper Functions
	// ─────────────────────────────────────────────────────────────────────────

	function makeDocumentRequest(
		method: string,
		path: string,
		userId: number,
		workspaceId: number,
		body?: object,
	) {
		const headers: Record<string, string> = {
			"x-user-id": String(userId),
			"x-workspace-id": String(workspaceId),
		};
		if (body) {
			headers["Content-Type"] = "application/json";
		}
		return app.fetch(
			new Request(`http://localhost/mentor/documents${path}`, {
				method,
				headers,
				body: body ? JSON.stringify(body) : undefined,
			}),
		);
	}

	function makeThreadsRequest(userId: number, workspaceId: number) {
		return app.fetch(
			new Request("http://localhost/mentor/threads/grouped", {
				method: "GET",
				headers: {
					"x-user-id": String(userId),
					"x-workspace-id": String(workspaceId),
				},
			}),
		);
	}

	function makeVoteRequest(messageId: string, userId: number, workspaceId: number) {
		return app.fetch(
			new Request(`http://localhost/mentor/messages/chat/messages/${messageId}/vote`, {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-user-id": String(userId),
					"x-workspace-id": String(workspaceId),
				},
				body: JSON.stringify({ isUpvoted: true }),
			}),
		);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Missing Context Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Missing Context", () => {
		it("should reject document list without userId", async () => {
			const response = await app.fetch(
				new Request("http://localhost/mentor/documents?page=0&size=10", {
					method: "GET",
					headers: {
						"x-workspace-id": String(fixtures.workspace.id),
						// Missing x-user-id
					},
				}),
			);
			expect(response.status).toBe(400);
		});

		it("should reject document list without workspaceId", async () => {
			const response = await app.fetch(
				new Request("http://localhost/mentor/documents?page=0&size=10", {
					method: "GET",
					headers: {
						"x-user-id": String(fixtures.user.id),
						// Missing x-workspace-id
					},
				}),
			);
			expect(response.status).toBe(400);
		});

		it("should reject grouped threads without userId", async () => {
			const response = await app.fetch(
				new Request("http://localhost/mentor/threads/grouped", {
					method: "GET",
					headers: {
						"x-workspace-id": String(fixtures.workspace.id),
						// Missing x-user-id
					},
				}),
			);
			expect(response.status).toBe(400);
		});

		it("should reject vote without context", async () => {
			const response = await app.fetch(
				new Request(`http://localhost/mentor/messages/chat/messages/${uuidv4()}/vote`, {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						// Missing context headers
					},
					body: JSON.stringify({ isUpvoted: true }),
				}),
			);
			expect(response.status).toBe(400);
		});

		it("should reject document create without userId", async () => {
			const response = await app.fetch(
				new Request("http://localhost/mentor/documents", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"x-workspace-id": String(fixtures.workspace.id),
						// Missing x-user-id
					},
					body: JSON.stringify({ title: "Test", content: "Content", kind: "text" }),
				}),
			);
			expect(response.status).toBe(400);
		});

		it("should reject document create without workspaceId", async () => {
			const response = await app.fetch(
				new Request("http://localhost/mentor/documents", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"x-user-id": String(fixtures.user.id),
						// Missing x-workspace-id
					},
					body: JSON.stringify({ title: "Test", content: "Content", kind: "text" }),
				}),
			);
			expect(response.status).toBe(400);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Document Isolation Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Document Isolation", () => {
		it("should only list documents owned by the requesting user", async () => {
			// Create a document for our test user
			const doc = await createDocument({
				title: "User1's Document",
				content: "Private content",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			// List documents as the owning user - should see the document
			const ownerResponse = await makeDocumentRequest(
				"GET",
				"?page=0&size=100",
				fixtures.user.id,
				fixtures.workspace.id,
			);
			expect(ownerResponse.status).toBe(200);
			const ownerDocs = (await ownerResponse.json()) as { id: string }[];
			const found = ownerDocs.some((d) => d.id === doc?.id);
			expect(found).toBe(true);

			// List documents as a different user - should NOT see the document
			const otherUserId = fixtures.user.id + 9999; // Different user ID
			const otherResponse = await makeDocumentRequest(
				"GET",
				"?page=0&size=100",
				otherUserId,
				fixtures.workspace.id,
			);
			expect(otherResponse.status).toBe(200);
			const otherDocs = (await otherResponse.json()) as { id: string }[];
			const foundByOther = otherDocs.some((d) => d.id === doc?.id);
			expect(foundByOther).toBe(false);
		});

		it("should not allow reading document owned by another user", async () => {
			// Create a document for our test user
			const doc = await createDocument({
				title: "Private Doc",
				content: "Secret",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			// Try to read as a different user
			const otherUserId = fixtures.user.id + 9999;
			const response = await makeDocumentRequest(
				"GET",
				`/${doc?.id}`,
				otherUserId,
				fixtures.workspace.id,
			);
			expect(response.status).toBe(404);
		});

		it("should not allow updating document owned by another user", async () => {
			// Create a document
			const doc = await createDocument({
				title: "My Doc",
				content: "My content",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			// Try to update as different user
			const otherUserId = fixtures.user.id + 9999;
			const response = await makeDocumentRequest(
				"PUT",
				`/${doc?.id}`,
				otherUserId,
				fixtures.workspace.id,
				{ title: "Hacked!", content: "Malicious", kind: "text" },
			);
			expect(response.status).toBe(404);
		});

		it("should not allow deleting document owned by another user", async () => {
			// Create a document
			const doc = await createDocument({
				title: "Protected",
				content: "Data",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			// Try to delete as different user
			const otherUserId = fixtures.user.id + 9999;
			const response = await makeDocumentRequest(
				"DELETE",
				`/${doc?.id}`,
				otherUserId,
				fixtures.workspace.id,
			);
			expect(response.status).toBe(404);

			// Verify document still exists for owner
			const ownerResponse = await makeDocumentRequest(
				"GET",
				`/${doc?.id}`,
				fixtures.user.id,
				fixtures.workspace.id,
			);
			expect(ownerResponse.status).toBe(200);
		});

		it("should isolate documents by workspace", async () => {
			// Create a document in workspace A
			const doc = await createDocument({
				title: "Workspace A Doc",
				content: "Content",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			// Try to access from different workspace
			const otherWorkspaceId = fixtures.workspace.id + 9999;
			const response = await makeDocumentRequest(
				"GET",
				`/${doc?.id}`,
				fixtures.user.id,
				otherWorkspaceId,
			);
			expect(response.status).toBe(404);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Thread Isolation Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Thread Isolation", () => {
		it("should only list threads owned by the requesting user", async () => {
			// Create a thread for our test user
			const threadId = await createTestThread(fixtures.workspace.id, {
				userId: fixtures.user.id,
				title: "User's Thread",
			});
			createdThreadIds.push(threadId);

			// List as owner - should see the thread
			const ownerResponse = await makeThreadsRequest(fixtures.user.id, fixtures.workspace.id);
			expect(ownerResponse.status).toBe(200);
			const ownerGroups = (await ownerResponse.json()) as { threads: { id: string }[] }[];
			const allOwnerThreads = ownerGroups.flatMap((g) => g.threads);
			const found = allOwnerThreads.some((t) => t.id === threadId);
			expect(found).toBe(true);

			// List as different user - should NOT see the thread
			const otherUserId = fixtures.user.id + 9999;
			const otherResponse = await makeThreadsRequest(otherUserId, fixtures.workspace.id);
			expect(otherResponse.status).toBe(200);
			const otherGroups = (await otherResponse.json()) as { threads: { id: string }[] }[];
			const allOtherThreads = otherGroups.flatMap((g) => g.threads);
			const foundByOther = allOtherThreads.some((t) => t.id === threadId);
			expect(foundByOther).toBe(false);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Vote Isolation Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Vote Isolation", () => {
		it("should not allow voting on messages in threads owned by other users", async () => {
			// Create a thread owned by the test user
			const threadId = await createTestThread(fixtures.workspace.id, {
				userId: fixtures.user.id,
			});
			createdThreadIds.push(threadId);

			// Add a message to the thread
			const messageId = await createTestMessage(threadId, { text: "Test message" });

			// Try to vote as a different user
			const otherUserId = fixtures.user.id + 9999;
			const response = await makeVoteRequest(messageId, otherUserId, fixtures.workspace.id);

			// Should return 404 because the message doesn't belong to a thread owned by this user
			expect(response.status).toBe(404);
		});

		it("should allow voting on messages in own threads", async () => {
			// Create a thread owned by the test user
			const threadId = await createTestThread(fixtures.workspace.id, {
				userId: fixtures.user.id,
			});
			createdThreadIds.push(threadId);

			// Add a message
			const messageId = await createTestMessage(threadId, { text: "Test message" });

			// Vote as the owner
			const response = await makeVoteRequest(messageId, fixtures.user.id, fixtures.workspace.id);
			expect(response.status).toBe(200);
		});

		it("should not allow voting from different workspace", async () => {
			// Create a thread owned by the test user
			const threadId = await createTestThread(fixtures.workspace.id, {
				userId: fixtures.user.id,
			});
			createdThreadIds.push(threadId);

			// Add a message
			const messageId = await createTestMessage(threadId, { text: "Test message" });

			// Try to vote from different workspace
			const otherWorkspaceId = fixtures.workspace.id + 9999;
			const response = await makeVoteRequest(messageId, fixtures.user.id, otherWorkspaceId);
			expect(response.status).toBe(404);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Document Version Isolation Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Document Version Isolation", () => {
		it("should not allow listing versions of another user's document", async () => {
			// Create a document for our test user
			const doc = await createDocument({
				title: "Versioned Doc",
				content: "V1",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			// Try to list versions as different user
			const otherUserId = fixtures.user.id + 9999;
			const response = await makeDocumentRequest(
				"GET",
				`/${doc?.id}/versions?page=0&size=10`,
				otherUserId,
				fixtures.workspace.id,
			);
			expect(response.status).toBe(404);
		});

		it("should not allow getting specific version of another user's document", async () => {
			// Create a document
			const doc = await createDocument({
				title: "Doc with versions",
				content: "Content",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			// Try to get version 1 as different user
			const otherUserId = fixtures.user.id + 9999;
			const response = await makeDocumentRequest(
				"GET",
				`/${doc?.id}/versions/1`,
				otherUserId,
				fixtures.workspace.id,
			);
			expect(response.status).toBe(404);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Thread Workspace Isolation Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Thread Workspace Isolation", () => {
		it("should not show threads from different workspace", async () => {
			// Create a thread in our workspace
			const threadId = await createTestThread(fixtures.workspace.id, {
				userId: fixtures.user.id,
				title: "Workspace-specific Thread",
			});
			createdThreadIds.push(threadId);

			// Try to list from different workspace - should not see the thread
			const otherWorkspaceId = fixtures.workspace.id + 9999;
			const response = await makeThreadsRequest(fixtures.user.id, otherWorkspaceId);
			expect(response.status).toBe(200);
			const groups = (await response.json()) as { threads: { id: string }[] }[];
			const allThreads = groups.flatMap((g) => g.threads);
			const found = allThreads.some((t) => t.id === threadId);
			expect(found).toBe(false);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Missing Context - Additional Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Missing Context - Additional", () => {
		it("should reject document get without userId", async () => {
			const response = await app.fetch(
				new Request(`http://localhost/mentor/documents/${uuidv4()}`, {
					method: "GET",
					headers: {
						"x-workspace-id": String(fixtures.workspace.id),
					},
				}),
			);
			expect(response.status).toBe(400);
		});

		it("should reject document update without userId", async () => {
			const response = await app.fetch(
				new Request(`http://localhost/mentor/documents/${uuidv4()}`, {
					method: "PUT",
					headers: {
						"Content-Type": "application/json",
						"x-workspace-id": String(fixtures.workspace.id),
					},
					body: JSON.stringify({ title: "Test", content: "Test", kind: "text" }),
				}),
			);
			expect(response.status).toBe(400);
		});

		it("should reject document delete without userId", async () => {
			const response = await app.fetch(
				new Request(`http://localhost/mentor/documents/${uuidv4()}`, {
					method: "DELETE",
					headers: {
						"x-workspace-id": String(fixtures.workspace.id),
					},
				}),
			);
			expect(response.status).toBe(400);
		});

		it("should reject list versions without userId", async () => {
			const response = await app.fetch(
				new Request(`http://localhost/mentor/documents/${uuidv4()}/versions?page=0&size=10`, {
					method: "GET",
					headers: {
						"x-workspace-id": String(fixtures.workspace.id),
					},
				}),
			);
			expect(response.status).toBe(400);
		});

		it("should reject get version without userId", async () => {
			const response = await app.fetch(
				new Request(`http://localhost/mentor/documents/${uuidv4()}/versions/1`, {
					method: "GET",
					headers: {
						"x-workspace-id": String(fixtures.workspace.id),
					},
				}),
			);
			expect(response.status).toBe(400);
		});

		it("should reject grouped threads without workspaceId", async () => {
			const response = await app.fetch(
				new Request("http://localhost/mentor/threads/grouped", {
					method: "GET",
					headers: {
						"x-user-id": String(fixtures.user.id),
						// Missing x-workspace-id
					},
				}),
			);
			expect(response.status).toBe(400);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Cross-Boundary Attack Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Cross-Boundary Attacks", () => {
		it("should not leak document existence to other users (consistent 404)", async () => {
			// Create a document
			const doc = await createDocument({
				title: "Secret Doc",
				content: "Secret content",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			// Request from different user should get 404 (not 403)
			// This prevents information disclosure about document existence
			const otherUserId = fixtures.user.id + 9999;
			const existingDocResponse = await makeDocumentRequest(
				"GET",
				`/${doc?.id}`,
				otherUserId,
				fixtures.workspace.id,
			);
			expect(existingDocResponse.status).toBe(404);

			// Non-existent document should also be 404
			const nonExistentResponse = await makeDocumentRequest(
				"GET",
				`/${uuidv4()}`,
				otherUserId,
				fixtures.workspace.id,
			);
			expect(nonExistentResponse.status).toBe(404);
		});

		it("should not allow updating document by changing workspace context", async () => {
			// Create a document in workspace A
			const doc = await createDocument({
				title: "Original",
				content: "Content",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			// Try to update with a different workspace ID
			const otherWorkspaceId = fixtures.workspace.id + 9999;
			const response = await makeDocumentRequest(
				"PUT",
				`/${doc?.id}`,
				fixtures.user.id,
				otherWorkspaceId,
				{ title: "Hacked", content: "Malicious", kind: "text" },
			);
			expect(response.status).toBe(404);

			// Verify original document is unchanged
			const originalDoc = await makeDocumentRequest(
				"GET",
				`/${doc?.id}`,
				fixtures.user.id,
				fixtures.workspace.id,
			);
			expect(originalDoc.status).toBe(200);
			const docData = (await originalDoc.json()) as { title: string };
			expect(docData.title).toBe("Original");
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Thread Detail Isolation Tests (GET /mentor/chat/threads/{threadId})
	// ─────────────────────────────────────────────────────────────────────────

	describe("Thread Detail Isolation", () => {
		function makeGetThreadRequest(threadId: string, userId: number, workspaceId: number) {
			return app.fetch(
				new Request(`http://localhost/mentor/threads/${threadId}`, {
					method: "GET",
					headers: {
						"x-user-id": String(userId),
						"x-workspace-id": String(workspaceId),
					},
				}),
			);
		}

		it("should not allow reading thread owned by another user", async () => {
			// Create a thread owned by our test user
			const threadId = await createTestThread(fixtures.workspace.id, {
				userId: fixtures.user.id,
				title: "Private Thread",
			});
			createdThreadIds.push(threadId);

			// Try to get thread as different user
			const otherUserId = fixtures.user.id + 9999;
			const response = await makeGetThreadRequest(threadId, otherUserId, fixtures.workspace.id);
			expect(response.status).toBe(404);
		});

		it("should not allow reading thread from different workspace", async () => {
			// Create a thread
			const threadId = await createTestThread(fixtures.workspace.id, {
				userId: fixtures.user.id,
			});
			createdThreadIds.push(threadId);

			// Try to read from different workspace
			const otherWorkspaceId = fixtures.workspace.id + 9999;
			const response = await makeGetThreadRequest(threadId, fixtures.user.id, otherWorkspaceId);
			expect(response.status).toBe(404);
		});

		it("should reject get thread without userId", async () => {
			const response = await app.fetch(
				new Request(`http://localhost/mentor/threads/${uuidv4()}`, {
					method: "GET",
					headers: {
						"x-workspace-id": String(fixtures.workspace.id),
					},
				}),
			);
			// Handler returns 400 for missing context
			expect(response.status).toBe(400);
		});

		it("should allow owner to read their own thread", async () => {
			// Arrange
			const threadId = await createTestThread(fixtures.workspace.id, {
				userId: fixtures.user.id,
				title: "My Thread",
			});
			createdThreadIds.push(threadId);

			// Act
			const response = await makeGetThreadRequest(
				threadId,
				fixtures.user.id,
				fixtures.workspace.id,
			);

			// Assert
			expect(response.status).toBe(200);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Delete After Isolation Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("DeleteAfter Isolation", () => {
		it("should not allow deleting versions of another user's document", async () => {
			// Create a document with a version
			const doc = await createDocument({
				title: "Doc to protect",
				content: "Content",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			// Try to delete versions as different user
			const otherUserId = fixtures.user.id + 9999;
			const after = new Date(Date.now() - 1000).toISOString();
			const response = await makeDocumentRequest(
				"DELETE",
				`/${doc?.id}/versions?after=${encodeURIComponent(after)}`,
				otherUserId,
				fixtures.workspace.id,
			);
			expect(response.status).toBe(404);
		});

		it("should not allow deleting versions from different workspace", async () => {
			const doc = await createDocument({
				title: "Doc to protect",
				content: "Content",
				kind: "text",
				userId: fixtures.user.id,
				workspaceId: fixtures.workspace.id,
			});
			expect(doc).toBeDefined();
			if (doc) {
				createdDocIds.push(doc.id);
			}

			const otherWorkspaceId = fixtures.workspace.id + 9999;
			const after = new Date(Date.now() - 1000).toISOString();
			const response = await makeDocumentRequest(
				"DELETE",
				`/${doc?.id}/versions?after=${encodeURIComponent(after)}`,
				fixtures.user.id,
				otherWorkspaceId,
			);
			expect(response.status).toBe(404);
		});
	});
});
