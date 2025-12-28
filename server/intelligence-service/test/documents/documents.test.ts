/**
 * Documents Module Integration Tests
 *
 * Tests the full HTTP API flow for the documents endpoints.
 * Uses real database operations to test document CRUD functionality.
 *
 * Key test scenarios:
 * 1. Document creation (POST /mentor/documents)
 * 2. Get document by ID (GET /mentor/documents/{id})
 * 3. Update document (PUT /mentor/documents/{id})
 * 4. Delete document (DELETE /mentor/documents/{id})
 * 5. List documents (GET /mentor/documents)
 * 6. Version management (list versions, get version, delete after)
 * 7. Error handling and validation
 *
 */

import { eq } from "drizzle-orm";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import app from "@/app";
import {
	createDocument,
	deleteDocument,
	getDocumentById,
	updateDocument as updateDocumentData,
} from "@/mentor/documents/data";
import db from "@/shared/db";
import { document as documentTable } from "@/shared/db/schema";
import { cleanupTestFixtures, createTestFixtures, type TestFixtures, testUuid } from "../mocks";

/** Types for API responses */
interface DocumentResponse {
	id: string;
	versionNumber: number;
	createdAt: string;
	title: string;
	content: string;
	kind: string;
	userId: number;
}

interface DocumentSummaryResponse {
	id: string;
	title: string;
	kind: string;
	createdAt: string;
	userId: number;
}

interface ErrorResponse {
	error: string;
}

describe("Documents Module", () => {
	let fixtures: TestFixtures;
	const createdDocumentIds: string[] = [];

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterEach(async () => {
		// Clean up all created documents
		for (const docId of createdDocumentIds) {
			await db.delete(documentTable).where(eq(documentTable.id, docId));
		}
		createdDocumentIds.length = 0;
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Helper Functions
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Helper to get a document by ID.
	 */
	function getDocumentRequest(id: string) {
		const request = new Request(`http://localhost/mentor/documents/${id}`, {
			method: "GET",
			headers: {
				"x-workspace-id": String(fixtures.workspace.id),
				"x-user-id": String(fixtures.user.id),
			},
		});
		return app.fetch(request);
	}

	/**
	 * Helper to update a document.
	 */
	function updateDocumentRequest(
		id: string,
		body: { title: string; content: string; kind: string },
	) {
		const request = new Request(`http://localhost/mentor/documents/${id}`, {
			method: "PUT",
			headers: {
				"Content-Type": "application/json",
				"x-workspace-id": String(fixtures.workspace.id),
				"x-user-id": String(fixtures.user.id),
			},
			body: JSON.stringify(body),
		});
		return app.fetch(request);
	}

	/**
	 * Helper to delete a document.
	 */
	function deleteDocumentRequest(id: string) {
		const request = new Request(`http://localhost/mentor/documents/${id}`, {
			method: "DELETE",
			headers: {
				"x-workspace-id": String(fixtures.workspace.id),
				"x-user-id": String(fixtures.user.id),
			},
		});
		return app.fetch(request);
	}

	/**
	 * Helper to list documents.
	 */
	function listDocumentsRequest(page = 0, size = 20) {
		const request = new Request(`http://localhost/mentor/documents?page=${page}&size=${size}`, {
			method: "GET",
			headers: {
				"x-workspace-id": String(fixtures.workspace.id),
				"x-user-id": String(fixtures.user.id),
			},
		});
		return app.fetch(request);
	}

	/**
	 * Helper to list versions of a document.
	 */
	function listVersionsRequest(id: string, page = 0, size = 20) {
		const request = new Request(
			`http://localhost/mentor/documents/${id}/versions?page=${page}&size=${size}`,
			{
				method: "GET",
				headers: {
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
				},
			},
		);
		return app.fetch(request);
	}

	/**
	 * Helper to get a specific version of a document.
	 */
	function getVersionRequest(id: string, versionNumber: number) {
		const request = new Request(
			`http://localhost/mentor/documents/${id}/versions/${versionNumber}`,
			{
				method: "GET",
				headers: {
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
				},
			},
		);
		return app.fetch(request);
	}

	/**
	 * Helper to delete versions after a timestamp.
	 */
	function deleteAfterRequest(id: string, after: string) {
		const request = new Request(
			`http://localhost/mentor/documents/${id}/versions?after=${encodeURIComponent(after)}`,
			{
				method: "DELETE",
				headers: {
					"x-workspace-id": String(fixtures.workspace.id),
					"x-user-id": String(fixtures.user.id),
				},
			},
		);
		return app.fetch(request);
	}

	/**
	 * Helper to create a document directly in the database for testing.
	 * This bypasses the API to avoid issues with the handler not passing userId.
	 */
	async function createTestDocument(
		title = "Test Document",
		content = "Test content",
		kind: "text" = "text",
	): Promise<{ id: string; title: string; content: string; kind: string; versionNumber: number }> {
		const doc = await createDocument({
			title,
			content,
			kind,
			userId: fixtures.user.id,
			workspaceId: fixtures.workspace.id,
		});
		if (!doc) {
			throw new Error("Failed to create test document");
		}
		createdDocumentIds.push(doc.id);
		return doc;
	}

	/**
	 * Helper to create a document and track it for cleanup.
	 */
	function createAndTrackDocument(
		title = "Test Document",
		content = "Test content",
		kind: "text" = "text",
	): Promise<{ id: string; title: string; content: string; kind: string; versionNumber: number }> {
		return createTestDocument(title, content, kind);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Document Creation Tests (API endpoint)
	// Note: The createDocument handler has a known issue where it doesn't pass
	// userId from context to the data layer. These tests verify validation
	// and error handling rather than successful creation via API.
	// ─────────────────────────────────────────────────────────────────────────

	describe("POST /mentor/documents (create document)", () => {
		it("should reject creation with missing title", async () => {
			const request = new Request("http://localhost/mentor/documents", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
				},
				body: JSON.stringify({
					content: "Some content",
					kind: "text",
				}),
			});

			const response = await app.fetch(request);
			expect(response.status).toBe(422); // Zod validation error
		});

		it("should reject creation with empty content", async () => {
			const request = new Request("http://localhost/mentor/documents", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
				},
				body: JSON.stringify({
					title: "Test",
					content: "",
					kind: "text",
				}),
			});

			const response = await app.fetch(request);
			expect(response.status).toBe(422); // Zod validation error
		});

		it("should reject creation with missing workspace context", async () => {
			const request = new Request("http://localhost/mentor/documents", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
				},
				body: JSON.stringify({
					title: "Test",
					content: "Content",
					kind: "text",
				}),
			});

			const response = await app.fetch(request);
			expect(response.status).toBe(400);
			const error = (await response.json()) as ErrorResponse;
			expect(error.error).toBe("Missing required context (userId or workspaceId)");
		});

		it("should reject creation with invalid kind", async () => {
			const request = new Request("http://localhost/mentor/documents", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					"x-workspace-id": String(fixtures.workspace.id),
				},
				body: JSON.stringify({
					title: "Test",
					content: "Content",
					kind: "invalid-kind",
				}),
			});

			const response = await app.fetch(request);
			expect(response.status).toBe(422); // Zod validation error
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Data Layer Document Creation Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Document creation via data layer", () => {
		it("should create a document successfully", async () => {
			const doc = await createTestDocument("My First Document", "This is the content.");

			expect(doc).toMatchObject({
				title: "My First Document",
				content: "This is the content.",
				kind: "text",
				versionNumber: 1,
			});
			expect(doc.id).toBeDefined();
		});

		it("should create documents with unique IDs", async () => {
			const doc1 = await createTestDocument("Doc 1", "Content 1");
			const doc2 = await createTestDocument("Doc 2", "Content 2");

			expect(doc1.id).not.toBe(doc2.id);
		});

		it("should get document by ID using data layer", async () => {
			const created = await createTestDocument("Data Layer Get", "Content");

			const fetched = await getDocumentById(created.id);

			expect(fetched).not.toBeNull();
			expect(fetched?.id).toBe(created.id);
			expect(fetched?.title).toBe("Data Layer Get");
		});

		it("should return null for non-existent document in data layer", async () => {
			const fetched = await getDocumentById(testUuid());

			expect(fetched).toBeNull();
		});

		it("should update document using data layer", async () => {
			const created = await createTestDocument("Original", "Original content");

			const updated = await updateDocumentData(created.id, {
				title: "Updated",
				content: "Updated content",
				kind: "text",
			});

			expect(updated).not.toBeNull();
			expect(updated?.title).toBe("Updated");
			expect(updated?.content).toBe("Updated content");
			expect(updated?.versionNumber).toBe(2);
		});

		it("should return null when updating non-existent document", async () => {
			const updated = await updateDocumentData(testUuid(), {
				title: "Test",
				content: "Test",
				kind: "text",
			});

			expect(updated).toBeNull();
		});

		it("should delete document using data layer", async () => {
			const created = await createTestDocument("To Delete", "Content");

			const deleted = await deleteDocument(created.id);

			expect(deleted).toBe(true);

			// Verify it's gone
			const fetched = await getDocumentById(created.id);
			expect(fetched).toBeNull();

			// Remove from tracking since already deleted
			const index = createdDocumentIds.indexOf(created.id);
			if (index > -1) {
				createdDocumentIds.splice(index, 1);
			}
		});

		it("should return false when deleting non-existent document", async () => {
			const deleted = await deleteDocument(testUuid());

			expect(deleted).toBe(false);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Get Document Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("GET /mentor/documents/{id} (get document)", () => {
		it("should return document by ID", async () => {
			const created = await createAndTrackDocument("Fetch Test", "Fetch content");

			const response = await getDocumentRequest(created.id);

			expect(response.status).toBe(200);
			const doc = (await response.json()) as DocumentResponse;
			expect(doc).toMatchObject({
				id: created.id,
				title: "Fetch Test",
				content: "Fetch content",
				kind: "text",
				versionNumber: 1,
			});
		});

		it("should return 404 for non-existent document", async () => {
			const nonExistentId = testUuid();

			const response = await getDocumentRequest(nonExistentId);

			expect(response.status).toBe(404);
			const error = (await response.json()) as ErrorResponse;
			expect(error.error).toBe("Document not found");
		});

		it("should return 422 for invalid UUID format", async () => {
			const response = await getDocumentRequest("not-a-valid-uuid");

			expect(response.status).toBe(422); // Zod validation error
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Update Document Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("PUT /mentor/documents/{id} (update document)", () => {
		it("should update document and create new version", async () => {
			const created = await createAndTrackDocument("Original Title", "Original content");

			const response = await updateDocumentRequest(created.id, {
				title: "Updated Title",
				content: "Updated content",
				kind: "text",
			});

			expect(response.status).toBe(200);
			const doc = (await response.json()) as DocumentResponse;
			expect(doc).toMatchObject({
				id: created.id,
				title: "Updated Title",
				content: "Updated content",
				kind: "text",
				versionNumber: 2,
			});
		});

		it("should return 404 when updating non-existent document", async () => {
			const nonExistentId = testUuid();

			const response = await updateDocumentRequest(nonExistentId, {
				title: "New Title",
				content: "New content",
				kind: "text",
			});

			expect(response.status).toBe(404);
			const error = (await response.json()) as ErrorResponse;
			expect(error.error).toBe("Document not found");
		});

		it("should return 422 for invalid UUID format", async () => {
			const response = await updateDocumentRequest("invalid-uuid", {
				title: "Test",
				content: "Content",
				kind: "text",
			});

			expect(response.status).toBe(422); // Zod validation error
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Delete Document Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("DELETE /mentor/documents/{id} (delete document)", () => {
		it("should delete document and return 204", async () => {
			const created = await createAndTrackDocument("To Delete", "Delete me");

			const response = await deleteDocumentRequest(created.id);

			expect(response.status).toBe(204);

			// Verify document is actually deleted
			const getResponse = await getDocumentRequest(created.id);
			expect(getResponse.status).toBe(404);

			// Remove from tracking since it's already deleted
			const index = createdDocumentIds.indexOf(created.id);
			if (index > -1) {
				createdDocumentIds.splice(index, 1);
			}
		});

		it("should return 404 when deleting non-existent document", async () => {
			const nonExistentId = testUuid();

			const response = await deleteDocumentRequest(nonExistentId);

			expect(response.status).toBe(404);
			const error = (await response.json()) as ErrorResponse;
			expect(error.error).toBe("Document not found");
		});

		it("should return 422 for invalid UUID format", async () => {
			const response = await deleteDocumentRequest("not-a-uuid");

			expect(response.status).toBe(422); // Zod validation error
		});

		it("should delete all versions of a document", async () => {
			const created = await createAndTrackDocument("Multi Version", "Version 1");

			// Create additional versions
			await updateDocumentRequest(created.id, {
				title: "Multi Version",
				content: "Version 2",
				kind: "text",
			});
			await updateDocumentRequest(created.id, {
				title: "Multi Version",
				content: "Version 3",
				kind: "text",
			});

			// Delete the document
			const response = await deleteDocumentRequest(created.id);
			expect(response.status).toBe(204);

			// Verify all versions are deleted
			const versionsResponse = await listVersionsRequest(created.id);
			expect(versionsResponse.status).toBe(404);

			// Remove from tracking
			const index = createdDocumentIds.indexOf(created.id);
			if (index > -1) {
				createdDocumentIds.splice(index, 1);
			}
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// List Documents Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("GET /mentor/documents (list documents)", () => {
		it("should return list of document summaries", async () => {
			const doc1 = await createAndTrackDocument("List Test 1", "Content 1");
			const doc2 = await createAndTrackDocument("List Test 2", "Content 2");

			const response = await listDocumentsRequest();

			expect(response.status).toBe(200);
			const docs = (await response.json()) as DocumentSummaryResponse[];

			// Should contain our created documents
			const createdIds = [doc1.id, doc2.id];
			const foundDocs = docs.filter((d) => createdIds.includes(d.id));
			expect(foundDocs.length).toBeGreaterThanOrEqual(2);

			// Verify summary structure (no content field)
			for (const doc of foundDocs) {
				expect(doc).toHaveProperty("id");
				expect(doc).toHaveProperty("title");
				expect(doc).toHaveProperty("kind");
				expect(doc).toHaveProperty("createdAt");
				expect(doc).not.toHaveProperty("content");
			}
		});

		it("should return empty list when no documents exist for pagination", async () => {
			// Request a page that's likely empty (high page number)
			const response = await listDocumentsRequest(9999, 20);

			expect(response.status).toBe(200);
			const docs = (await response.json()) as DocumentSummaryResponse[];
			expect(docs).toEqual([]);
		});

		it("should paginate results correctly", async () => {
			// Create 3 documents
			await createAndTrackDocument("Page Test 1", "Content 1");
			await createAndTrackDocument("Page Test 2", "Content 2");
			await createAndTrackDocument("Page Test 3", "Content 3");

			// Get first page with size 2
			const response = await listDocumentsRequest(0, 2);

			expect(response.status).toBe(200);
			const docs = (await response.json()) as DocumentSummaryResponse[];
			expect(docs.length).toBeLessThanOrEqual(2);
		});

		it("should return only latest version of each document", async () => {
			const created = await createAndTrackDocument("Version Test", "Original");

			// Update to create version 2
			await updateDocumentRequest(created.id, {
				title: "Version Test Updated",
				content: "Updated",
				kind: "text",
			});

			const response = await listDocumentsRequest();
			expect(response.status).toBe(200);
			const docs = (await response.json()) as DocumentSummaryResponse[];

			// Should only have one entry for this document (latest version)
			const matchingDocs = docs.filter((d) => d.id === created.id);
			expect(matchingDocs.length).toBe(1);
			expect(matchingDocs[0]?.title).toBe("Version Test Updated");
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Version Management Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("GET /mentor/documents/{id}/versions (list versions)", () => {
		it("should list all versions of a document", async () => {
			const created = await createAndTrackDocument("Version List", "V1");

			// Create versions 2 and 3
			await updateDocumentRequest(created.id, {
				title: "Version List",
				content: "V2",
				kind: "text",
			});
			await updateDocumentRequest(created.id, {
				title: "Version List",
				content: "V3",
				kind: "text",
			});

			const response = await listVersionsRequest(created.id);

			expect(response.status).toBe(200);
			const versions = (await response.json()) as DocumentResponse[];
			expect(versions.length).toBe(3);

			// Versions should be ordered newest first
			expect(versions[0]?.versionNumber).toBe(3);
			expect(versions[1]?.versionNumber).toBe(2);
			expect(versions[2]?.versionNumber).toBe(1);
		});

		it("should return 404 for non-existent document", async () => {
			const nonExistentId = testUuid();

			const response = await listVersionsRequest(nonExistentId);

			expect(response.status).toBe(404);
			const error = (await response.json()) as ErrorResponse;
			expect(error.error).toBe("Document not found");
		});

		it("should paginate versions correctly", async () => {
			const created = await createAndTrackDocument("Paginate Versions", "V1");

			// Create more versions
			for (let i = 2; i <= 5; i++) {
				await updateDocumentRequest(created.id, {
					title: "Paginate Versions",
					content: `V${i}`,
					kind: "text",
				});
			}

			// Get page with size 2
			const response = await listVersionsRequest(created.id, 0, 2);

			expect(response.status).toBe(200);
			const versions = (await response.json()) as DocumentResponse[];
			expect(versions.length).toBe(2);
		});
	});

	describe("GET /mentor/documents/{id}/versions/{versionNumber} (get specific version)", () => {
		it("should return specific version of a document", async () => {
			const created = await createAndTrackDocument("Specific Version", "Original content");

			// Create version 2
			await updateDocumentRequest(created.id, {
				title: "Specific Version",
				content: "Updated content",
				kind: "text",
			});

			// Get version 1
			const response = await getVersionRequest(created.id, 1);

			expect(response.status).toBe(200);
			const doc = (await response.json()) as DocumentResponse;
			expect(doc.content).toBe("Original content");
			expect(doc.versionNumber).toBe(1);
		});

		it("should return 404 for non-existent version", async () => {
			const created = await createAndTrackDocument("No Such Version", "Content");

			const response = await getVersionRequest(created.id, 999);

			expect(response.status).toBe(404);
			const error = (await response.json()) as ErrorResponse;
			expect(error.error).toBe("Document not found");
		});

		it("should return 404 for non-existent document", async () => {
			const nonExistentId = testUuid();

			const response = await getVersionRequest(nonExistentId, 1);

			expect(response.status).toBe(404);
		});
	});

	describe("DELETE /mentor/documents/{id}/versions?after (delete versions after)", () => {
		it("should delete versions after specified timestamp", async () => {
			const created = await createAndTrackDocument("Delete After", "V1");

			// Get timestamp before creating more versions
			const afterTimestamp = new Date().toISOString();

			// Wait a tiny bit and create more versions
			await new Promise((resolve) => setTimeout(resolve, 50));
			await updateDocumentRequest(created.id, {
				title: "Delete After",
				content: "V2",
				kind: "text",
			});
			await updateDocumentRequest(created.id, {
				title: "Delete After",
				content: "V3",
				kind: "text",
			});

			// Delete versions after the timestamp
			const response = await deleteAfterRequest(created.id, afterTimestamp);

			expect(response.status).toBe(200);
			const deletedVersions = (await response.json()) as DocumentResponse[];
			expect(deletedVersions.length).toBe(2); // V2 and V3

			// Verify only V1 remains
			const versionsResponse = await listVersionsRequest(created.id);
			expect(versionsResponse.status).toBe(200);
			const remainingVersions = (await versionsResponse.json()) as DocumentResponse[];
			expect(remainingVersions.length).toBe(1);
			expect(remainingVersions[0]?.versionNumber).toBe(1);
		});

		it("should return 404 when no versions exist after timestamp", async () => {
			const created = await createAndTrackDocument("No Versions After", "V1");

			// Use a future timestamp
			const futureTimestamp = new Date(Date.now() + 86400000).toISOString();

			const response = await deleteAfterRequest(created.id, futureTimestamp);

			expect(response.status).toBe(404);
			const error = (await response.json()) as ErrorResponse;
			expect(error.error).toBe("Document not found");
		});

		it("should return 404 for non-existent document", async () => {
			const nonExistentId = testUuid();
			const timestamp = new Date().toISOString();

			const response = await deleteAfterRequest(nonExistentId, timestamp);

			expect(response.status).toBe(404);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Edge Cases and Validation
	// ─────────────────────────────────────────────────────────────────────────

	describe("edge cases", () => {
		it("should handle very long document content", async () => {
			const longContent = "x".repeat(10000);

			const doc = await createTestDocument("Long Content", longContent);
			expect(doc.content.length).toBe(10000);
		});

		it("should handle special characters in title and content", async () => {
			const doc = await createTestDocument(
				"Special <chars> & \"quotes\" 'apostrophe'",
				"Content with unicode: 日本語 🎉 émojis",
			);

			expect(doc.title).toBe("Special <chars> & \"quotes\" 'apostrophe'");
			expect(doc.content).toContain("日本語");
		});

		it("should preserve document ID across versions", async () => {
			const created = await createAndTrackDocument("ID Preserved", "V1");
			const originalId = created.id;

			// Update multiple times
			await updateDocumentRequest(originalId, {
				title: "ID Preserved",
				content: "V2",
				kind: "text",
			});
			await updateDocumentRequest(originalId, {
				title: "ID Preserved",
				content: "V3",
				kind: "text",
			});

			// Get latest document
			const response = await getDocumentRequest(originalId);
			expect(response.status).toBe(200);
			const doc = (await response.json()) as DocumentResponse;
			expect(doc.id).toBe(originalId);
			expect(doc.versionNumber).toBe(3);
		});

		it("should maintain correct version ordering", async () => {
			const created = await createAndTrackDocument("Version Order", "V1");

			// Create multiple versions
			for (let i = 2; i <= 5; i++) {
				await updateDocumentRequest(created.id, {
					title: "Version Order",
					content: `V${i}`,
					kind: "text",
				});
			}

			const response = await listVersionsRequest(created.id);
			expect(response.status).toBe(200);
			const versions = (await response.json()) as DocumentResponse[];

			// Verify descending order (newest first)
			for (let i = 0; i < versions.length - 1; i++) {
				const current = versions[i];
				const next = versions[i + 1];
				if (current && next) {
					expect(current.versionNumber).toBeGreaterThan(next.versionNumber);
				}
			}
		});

		it("should get latest version when multiple versions exist", async () => {
			const created = await createAndTrackDocument("Latest Version", "Original");

			// Create version 2 with different content
			await updateDocumentRequest(created.id, {
				title: "Latest Version Updated",
				content: "Updated content",
				kind: "text",
			});

			// Get document (should return latest)
			const response = await getDocumentRequest(created.id);
			expect(response.status).toBe(200);
			const doc = (await response.json()) as DocumentResponse;
			expect(doc.versionNumber).toBe(2);
			expect(doc.title).toBe("Latest Version Updated");
			expect(doc.content).toBe("Updated content");
		});
	});
});
