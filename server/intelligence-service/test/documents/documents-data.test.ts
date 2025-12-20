/**
 * Documents Data Layer Tests
 *
 * Tests the data access functions for document CRUD operations.
 * Uses real database with test fixtures.
 */

import { eq } from "drizzle-orm";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import {
	createDocument,
	deleteDocument,
	deleteVersionsAfter,
	getDocumentById,
	getDocumentVersion,
	listDocuments,
	listVersions,
	updateDocument,
} from "@/mentor/documents/data";
import db from "@/shared/db";
import { document as docTable } from "@/shared/db/schema";
import { cleanupTestFixtures, createTestFixtures, type TestFixtures } from "../mocks";

describe("Documents Data Layer", () => {
	let fixtures: TestFixtures;
	const createdDocIds: string[] = [];

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterEach(async () => {
		// Clean up created documents
		for (const id of createdDocIds) {
			await db.delete(docTable).where(eq(docTable.id, id));
		}
		createdDocIds.length = 0;
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	// ─────────────────────────────────────────────────────────────────────────
	// createDocument Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("createDocument", () => {
		it("should create a new document with auto-generated ID", async () => {
			const doc = await createDocument({
				title: "Test Document",
				content: "Hello world",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			expect(doc).not.toBeNull();
			expect(doc?.id).toBeDefined();
			expect(doc?.title).toBe("Test Document");
			expect(doc?.content).toBe("Hello world");
			expect(doc?.kind).toBe("text");
			expect(doc?.versionNumber).toBe(1);

			if (doc) {
				createdDocIds.push(doc.id);
			}
		});

		it("should create a document with specified ID", async () => {
			const specifiedId = crypto.randomUUID();
			const doc = await createDocument({
				id: specifiedId,
				title: "Custom ID Document",
				content: "Content",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			expect(doc?.id).toBe(specifiedId);
			if (doc) {
				createdDocIds.push(doc.id);
			}
		});

		it("should set versionNumber to 1 for new documents", async () => {
			const doc = await createDocument({
				title: "Version Test",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			expect(doc?.versionNumber).toBe(1);
			if (doc) {
				createdDocIds.push(doc.id);
			}
		});

		it("should set createdAt timestamp", async () => {
			const before = Date.now();
			const doc = await createDocument({
				title: "Timestamp Test",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			const after = Date.now();

			expect(doc?.createdAt).toBeDefined();
			const createdAtTime = new Date(doc?.createdAt).getTime();
			expect(createdAtTime).toBeGreaterThanOrEqual(before - 1000);
			expect(createdAtTime).toBeLessThanOrEqual(after + 1000);

			if (doc) {
				createdDocIds.push(doc.id);
			}
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// getDocumentById Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("getDocumentById", () => {
		it("should return document by ID", async () => {
			const created = await createDocument({
				title: "Get Test",
				content: "Content",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (created) {
				createdDocIds.push(created.id);
			}

			const retrieved = await getDocumentById(created?.id);
			expect(retrieved).not.toBeNull();
			expect(retrieved?.id).toBe(created?.id);
			expect(retrieved?.title).toBe("Get Test");
		});

		it("should return null for non-existent ID", async () => {
			const result = await getDocumentById("00000000-0000-0000-0000-000000000000");
			expect(result).toBeNull();
		});

		it("should return latest version when multiple exist", async () => {
			const doc = await createDocument({
				title: "Version 1",
				content: "v1",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (doc) {
				createdDocIds.push(doc.id);
			}

			await updateDocument(doc?.id, {
				title: "Version 2",
				content: "v2",
				kind: "text",
			});

			const retrieved = await getDocumentById(doc?.id);
			expect(retrieved?.versionNumber).toBe(2);
			expect(retrieved?.title).toBe("Version 2");
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// getDocumentVersion Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("getDocumentVersion", () => {
		it("should return specific version", async () => {
			const doc = await createDocument({
				title: "Version 1",
				content: "v1",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (doc) {
				createdDocIds.push(doc.id);
			}

			await updateDocument(doc?.id, {
				title: "Version 2",
				content: "v2",
				kind: "text",
			});

			const v1 = await getDocumentVersion(doc?.id, 1);
			const v2 = await getDocumentVersion(doc?.id, 2);

			expect(v1?.title).toBe("Version 1");
			expect(v1?.versionNumber).toBe(1);
			expect(v2?.title).toBe("Version 2");
			expect(v2?.versionNumber).toBe(2);
		});

		it("should return null for non-existent version", async () => {
			const doc = await createDocument({
				title: "Test",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (doc) {
				createdDocIds.push(doc.id);
			}

			const result = await getDocumentVersion(doc?.id, 999);
			expect(result).toBeNull();
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// updateDocument Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("updateDocument", () => {
		it("should create new version with incremented version number", async () => {
			const doc = await createDocument({
				title: "Original",
				content: "original content",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (doc) {
				createdDocIds.push(doc.id);
			}

			const updated = await updateDocument(doc?.id, {
				title: "Updated",
				content: "updated content",
				kind: "text",
			});

			expect(updated?.versionNumber).toBe(2);
			expect(updated?.title).toBe("Updated");
			expect(updated?.content).toBe("updated content");
		});

		it("should return null for non-existent document", async () => {
			const result = await updateDocument("00000000-0000-0000-0000-000000000000", {
				title: "Title",
				content: "",
				kind: "text",
			});
			expect(result).toBeNull();
		});

		it("should preserve userId and workspaceId from original", async () => {
			const doc = await createDocument({
				title: "Original",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (doc) {
				createdDocIds.push(doc.id);
			}

			const updated = await updateDocument(doc?.id, {
				title: "Updated",
				content: "",
				kind: "text",
			});

			expect(updated?.workspaceId).toBe(fixtures.workspace.id);
			expect(updated?.userId).toBe(fixtures.user.id);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// listDocuments Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("listDocuments", () => {
		it("should return all documents", async () => {
			const doc1 = await createDocument({
				title: "Doc 1",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			const doc2 = await createDocument({
				title: "Doc 2",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (doc1) {
				createdDocIds.push(doc1.id);
			}
			if (doc2) {
				createdDocIds.push(doc2.id);
			}

			const docs = await listDocuments();
			const ourDocs = docs.filter((d) => createdDocIds.includes(d.id));

			expect(ourDocs.length).toBe(2);
		});

		it("should return only latest version of each document", async () => {
			const doc = await createDocument({
				title: "v1",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (doc) {
				createdDocIds.push(doc.id);
			}

			await updateDocument(doc?.id, { title: "v2", content: "", kind: "text" });
			await updateDocument(doc?.id, { title: "v3", content: "", kind: "text" });

			const docs = await listDocuments();
			const ourDoc = docs.find((d) => d.id === doc?.id);

			expect(ourDoc?.versionNumber).toBe(3);
			expect(ourDoc?.title).toBe("v3");
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// listVersions Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("listVersions", () => {
		it("should return all versions of a document", async () => {
			const doc = await createDocument({
				title: "v1",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (doc) {
				createdDocIds.push(doc.id);
			}

			await updateDocument(doc?.id, { title: "v2", content: "", kind: "text" });
			await updateDocument(doc?.id, { title: "v3", content: "", kind: "text" });

			const versions = await listVersions(doc?.id);
			expect(versions.length).toBe(3);
			expect(versions[0].versionNumber).toBe(1);
			expect(versions[1].versionNumber).toBe(2);
			expect(versions[2].versionNumber).toBe(3);
		});

		it("should return empty array for non-existent document", async () => {
			const versions = await listVersions("00000000-0000-0000-0000-000000000000");
			expect(versions).toEqual([]);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// deleteDocument Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("deleteDocument", () => {
		it("should delete document and all versions", async () => {
			const doc = await createDocument({
				title: "To Delete",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			// Don't add to createdDocIds since we're deleting it

			await updateDocument(doc?.id, { title: "v2", content: "", kind: "text" });

			const deleted = await deleteDocument(doc?.id);
			expect(deleted).toBe(true);

			const retrieved = await getDocumentById(doc?.id);
			expect(retrieved).toBeNull();
		});

		it("should return false for non-existent document", async () => {
			const result = await deleteDocument("00000000-0000-0000-0000-000000000000");
			expect(result).toBe(false);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// deleteVersionsAfter Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("deleteVersionsAfter", () => {
		it("should delete versions after specified version", async () => {
			const doc = await createDocument({
				title: "v1",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (doc) {
				createdDocIds.push(doc.id);
			}

			await updateDocument(doc?.id, { title: "v2", content: "", kind: "text" });
			await updateDocument(doc?.id, { title: "v3", content: "", kind: "text" });

			const count = await deleteVersionsAfter(doc?.id, 1);
			expect(count).toBe(2);

			const versions = await listVersions(doc?.id);
			expect(versions.length).toBe(1);
			expect(versions[0].versionNumber).toBe(1);
		});

		it("should return 0 if no versions to delete", async () => {
			const doc = await createDocument({
				title: "Single",
				content: "",
				kind: "text",
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});
			if (doc) {
				createdDocIds.push(doc.id);
			}

			const count = await deleteVersionsAfter(doc?.id, 1);
			expect(count).toBe(0);
		});
	});
});
