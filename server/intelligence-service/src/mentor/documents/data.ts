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
		})
		.returning();

	return rows[0] ? toDTO(rows[0]) : null;
}

/**
 * Get the latest version of a document by ID.
 */
export async function getDocumentById(id: string): Promise<DocumentRecord | null> {
	const rows = await db
		.select()
		.from(docTable)
		.where(eq(docTable.id, id))
		.orderBy(desc(docTable.versionNumber))
		.limit(1);

	return rows[0] ? toDTO(rows[0]) : null;
}

/**
 * Get a specific version of a document.
 */
export async function getDocumentVersion(
	id: string,
	versionNumber: number,
): Promise<DocumentRecord | null> {
	const rows = await db
		.select()
		.from(docTable)
		.where(and(eq(docTable.id, id), eq(docTable.versionNumber, versionNumber)))
		.limit(1);

	return rows[0] ? toDTO(rows[0]) : null;
}

/**
 * Create a new version of a document (update).
 */
export async function updateDocument(
	id: string,
	params: { title: string; content: string; kind: DocumentKind },
): Promise<DocumentRecord | null> {
	const latest = await getDocumentById(id);
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
		})
		.returning();

	return rows[0] ? toDTO(rows[0]) : null;
}

/**
 * List all documents (latest version of each).
 */
export async function listDocuments(): Promise<DocumentRecord[]> {
	// Get distinct document IDs with their max version
	const rows = await db
		.select()
		.from(docTable)
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
 * List all versions of a document.
 */
export async function listVersions(id: string): Promise<DocumentRecord[]> {
	const rows = await db
		.select()
		.from(docTable)
		.where(eq(docTable.id, id))
		.orderBy(asc(docTable.versionNumber));

	return rows.map(toDTO);
}

/**
 * Delete a document and all its versions.
 */
export async function deleteDocument(id: string): Promise<boolean> {
	const result = await db.delete(docTable).where(eq(docTable.id, id));
	return (result.rowCount ?? 0) > 0;
}

/**
 * Delete all versions after a specific version number.
 */
export async function deleteVersionsAfter(id: string, versionNumber: number): Promise<number> {
	const result = await db
		.delete(docTable)
		.where(and(eq(docTable.id, id), gt(docTable.versionNumber, versionNumber)));
	return result.rowCount ?? 0;
}
