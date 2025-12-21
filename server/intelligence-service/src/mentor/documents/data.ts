import { and, asc, desc, eq, gt } from "drizzle-orm";
import db from "@/shared/db";
import { document as docTable } from "@/shared/db/schema";
import type { DocumentKind } from "@/shared/document";

export type DocumentRecord = {
	id: string;
	versionNumber: number;
	createdAt: string;
	title: string;
	content: string;
	kind: DocumentKind;
	userId: number;
	workspaceId: number;
};

function toDTO(row: typeof docTable.$inferSelect): DocumentRecord {
	return {
		id: row.id,
		versionNumber: row.versionNumber,
		createdAt: row.createdAt ?? new Date().toISOString(),
		title: row.title,
		content: row.content ?? "",
		kind: row.kind as DocumentKind,
		userId: row.userId ?? 0,
		workspaceId: row.workspaceId,
	};
}

/**
 * Create a new document.
 */
export async function createDocument(params: {
	id?: string;
	title: string;
	content: string;
	kind: DocumentKind;
	userId?: number;
	workspaceId: number;
}): Promise<DocumentRecord | null> {
	const id = params.id ?? crypto.randomUUID();
	const now = new Date().toISOString();
	const rows = await db
		.insert(docTable)
		.values({
			id,
			versionNumber: 1,
			createdAt: now,
			title: params.title,
			content: params.content,
			kind: params.kind,
			userId: params.userId ?? 0,
			workspaceId: params.workspaceId,
		})
		.returning();

	return rows[0] ? toDTO(rows[0]) : null;
}

/**
 * Get the latest version of a document by ID, filtered by user and workspace.
 * Returns null if document doesn't exist or doesn't belong to the user/workspace.
 */
export async function getDocumentById(
	id: string,
	userId?: number,
	workspaceId?: number,
): Promise<DocumentRecord | null> {
	const conditions = [eq(docTable.id, id)];
	if (userId !== undefined) {
		conditions.push(eq(docTable.userId, userId));
	}
	if (workspaceId !== undefined) {
		conditions.push(eq(docTable.workspaceId, workspaceId));
	}

	const rows = await db
		.select()
		.from(docTable)
		.where(and(...conditions))
		.orderBy(desc(docTable.versionNumber))
		.limit(1);

	return rows[0] ? toDTO(rows[0]) : null;
}

/**
 * Get a specific version of a document, filtered by user and workspace.
 */
export async function getDocumentVersion(
	id: string,
	versionNumber: number,
	userId?: number,
	workspaceId?: number,
): Promise<DocumentRecord | null> {
	const conditions = [eq(docTable.id, id), eq(docTable.versionNumber, versionNumber)];
	if (userId !== undefined) {
		conditions.push(eq(docTable.userId, userId));
	}
	if (workspaceId !== undefined) {
		conditions.push(eq(docTable.workspaceId, workspaceId));
	}

	const rows = await db
		.select()
		.from(docTable)
		.where(and(...conditions))
		.limit(1);

	return rows[0] ? toDTO(rows[0]) : null;
}

/**
 * Create a new version of a document (update).
 * Verifies ownership before allowing update.
 */
export async function updateDocument(
	id: string,
	params: { title: string; content: string; kind: DocumentKind },
	userId?: number,
	workspaceId?: number,
): Promise<DocumentRecord | null> {
	const latest = await getDocumentById(id, userId, workspaceId);
	if (!latest) {
		return null;
	}

	const now = new Date().toISOString();
	const version = latest.versionNumber + 1;

	const rows = await db
		.insert(docTable)
		.values({
			id,
			versionNumber: version,
			createdAt: now,
			title: params.title,
			content: params.content,
			kind: params.kind,
			userId: latest.userId,
			workspaceId: latest.workspaceId,
		})
		.returning();

	return rows[0] ? toDTO(rows[0]) : null;
}

/**
 * List documents for a specific user and workspace (latest version of each).
 */
export async function listDocumentsByUserAndWorkspace(
	userId: number,
	workspaceId: number,
): Promise<DocumentRecord[]> {
	// Get documents filtered by user and workspace
	const rows = await db
		.select()
		.from(docTable)
		.where(and(eq(docTable.userId, userId), eq(docTable.workspaceId, workspaceId)))
		.orderBy(desc(docTable.createdAt), desc(docTable.versionNumber));

	// Deduplicate by ID (keep first occurrence which is latest version)
	const seen = new Set<string>();
	const result: DocumentRecord[] = [];
	for (const row of rows) {
		if (!seen.has(row.id)) {
			seen.add(row.id);
			result.push(toDTO(row));
		}
	}
	return result;
}

/**
 * List all versions of a document, filtered by user and workspace.
 */
export async function listVersions(
	id: string,
	userId?: number,
	workspaceId?: number,
): Promise<DocumentRecord[]> {
	const conditions = [eq(docTable.id, id)];
	if (userId !== undefined) {
		conditions.push(eq(docTable.userId, userId));
	}
	if (workspaceId !== undefined) {
		conditions.push(eq(docTable.workspaceId, workspaceId));
	}

	const rows = await db
		.select()
		.from(docTable)
		.where(and(...conditions))
		.orderBy(asc(docTable.versionNumber));

	return rows.map(toDTO);
}

/**
 * Delete a document and all its versions.
 * Only deletes if the document belongs to the specified user/workspace.
 */
export async function deleteDocument(
	id: string,
	userId?: number,
	workspaceId?: number,
): Promise<boolean> {
	const conditions = [eq(docTable.id, id)];
	if (userId !== undefined) {
		conditions.push(eq(docTable.userId, userId));
	}
	if (workspaceId !== undefined) {
		conditions.push(eq(docTable.workspaceId, workspaceId));
	}

	const result = await db.delete(docTable).where(and(...conditions));
	return (result.rowCount ?? 0) > 0;
}

/**
 * Delete all versions after a specific version number.
 * Only affects documents belonging to the specified user/workspace.
 */
export async function deleteVersionsAfter(
	id: string,
	versionNumber: number,
	userId?: number,
	workspaceId?: number,
): Promise<number> {
	const conditions = [eq(docTable.id, id), gt(docTable.versionNumber, versionNumber)];
	if (userId !== undefined) {
		conditions.push(eq(docTable.userId, userId));
	}
	if (workspaceId !== undefined) {
		conditions.push(eq(docTable.workspaceId, workspaceId));
	}

	const result = await db.delete(docTable).where(and(...conditions));
	return result.rowCount ?? 0;
}
