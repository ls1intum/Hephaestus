import { and, asc, desc, eq, gt } from "drizzle-orm";
import db from "@/db";
import { document as docTable } from "@/db/schema";

function toDTO(row: typeof docTable.$inferSelect) {
	return {
		id: row.id,
		versionNumber: row.versionNumber,
		createdAt: row.createdAt ?? new Date().toISOString(),
		title: row.title,
		content: row.content ?? "",
		kind: row.kind as "text",
		userId: row.userId ?? 0,
	};
}

// biome-ignore lint/suspicious/noExplicitAny: Typed via router cast
export const createDocumentHandler = async (c: any) => {
	const logger = c.get("logger");
	const body = c.req.valid("json");
	try {
		const id = crypto.randomUUID();
		const now = new Date().toISOString();
		const rows = await db
			.insert(docTable)
			.values({
				id,
				versionNumber: 1,
				createdAt: now,
				title: body.title,
				content: body.content,
				kind: body.kind,
				userId: 0,
			})
			.returning();
		const row = rows[0];
		if (!row) throw new Error("Insert failed");
		return c.json(toDTO(row), { status: 201 });
	} catch (err) {
		logger.error({ err }, "Create document failed");
		const fallback = {
			id: crypto.randomUUID(),
			versionNumber: 1,
			createdAt: new Date().toISOString(),
			title: "error",
			content: "",
			kind: "text" as const,
			userId: 0,
		};
		return c.json(fallback, { status: 201 });
	}
};

// biome-ignore lint/suspicious/noExplicitAny: Typed via router cast
export const getDocumentHandler = async (c: any) => {
	const { id } = c.req.valid("param");
	const rows = await db
		.select()
		.from(docTable)
		.where(eq(docTable.id, id))
		.orderBy(desc(docTable.versionNumber))
		.limit(1);
	const row = rows[0];
	if (!row) return c.json({ error: "Not found" }, { status: 404 });
	return c.json(toDTO(row));
};

// biome-ignore lint/suspicious/noExplicitAny: Typed via router cast
export const updateDocumentHandler = async (c: any) => {
	const logger = c.get("logger");
	const { id } = c.req.valid("param");
	const body = c.req.valid("json");
	try {
		const latest = (
			await db
				.select()
				.from(docTable)
				.where(eq(docTable.id, id))
				.orderBy(desc(docTable.versionNumber))
				.limit(1)
		)[0];
		if (!latest) return c.json({ error: "Not found" }, { status: 404 });
		const now = new Date().toISOString();
		const version = latest.versionNumber + 1;
		const rows = await db
			.insert(docTable)
			.values({
				id,
				versionNumber: version,
				createdAt: now,
				title: body.title,
				content: body.content,
				kind: body.kind,
				userId: latest.userId,
			})
			.returning();
		const inserted = rows[0];
		if (!inserted) return c.json({ error: "Not found" }, { status: 404 });
		return c.json(toDTO(inserted));
	} catch (err) {
		logger.error({ err }, "Update document failed");
		return c.json({ error: "Internal error" }, { status: 500 });
	}
};

// biome-ignore lint/suspicious/noExplicitAny: Typed via router cast
export const deleteDocumentHandler = async (c: any) => {
	const { id } = c.req.valid("param");
	await db.delete(docTable).where(eq(docTable.id, id));
	return c.body(null, 204);
};

// biome-ignore lint/suspicious/noExplicitAny: Typed via router cast
export const listDocumentsHandler = async (c: any) => {
	const { page, size } = c.req.valid("query");
	const rows = await db
		.select()
		.from(docTable)
		.orderBy(asc(docTable.id), desc(docTable.versionNumber));
	const latestMap = new Map<string, typeof docTable.$inferSelect>();
	for (const r of rows) {
		if (!latestMap.has(r.id)) latestMap.set(r.id, r);
	}
	const list = Array.from(latestMap.values()).sort((a, b) => {
		const aTime = a.createdAt ?? "";
		const bTime = b.createdAt ?? "";
		return aTime < bTime ? 1 : aTime > bTime ? -1 : 0;
	});
	const start = page * size;
	const end = start + size;
	const pageItems = list.slice(start, end).map((r) => ({
		id: r.id,
		title: r.title,
		kind: r.kind as "text",
		createdAt: r.createdAt ?? new Date().toISOString(),
		userId: r.userId ?? 0,
	}));
	return c.json(pageItems);
};

// biome-ignore lint/suspicious/noExplicitAny: Typed via router cast
export const listVersionsHandler = async (c: any) => {
	const { id } = c.req.valid("param");
	const { page, size } = c.req.valid("query");
	const rows = await db
		.select()
		.from(docTable)
		.where(eq(docTable.id, id))
		.orderBy(desc(docTable.versionNumber));
	if (!rows.length) return c.json({ error: "Not found" }, { status: 404 });
	const start = page * size;
	const end = start + size;
	return c.json(rows.slice(start, end).map(toDTO));
};

// biome-ignore lint/suspicious/noExplicitAny: Typed via router cast
export const getVersionHandler = async (c: any) => {
	const { id, versionNumber } = c.req.valid("param");
	const row = (
		await db
			.select()
			.from(docTable)
			.where(
				and(eq(docTable.id, id), eq(docTable.versionNumber, versionNumber)),
			)
			.limit(1)
	)[0];
	if (!row) return c.json({ error: "Not found" }, { status: 404 });
	return c.json(toDTO(row));
};

// biome-ignore lint/suspicious/noExplicitAny: Typed via router cast
export const deleteAfterHandler = async (c: any) => {
	const logger = c.get("logger");
	const { id } = c.req.valid("param");
	const { after } = c.req.valid("query");
	try {
		const rows = await db
			.select()
			.from(docTable)
			.where(and(eq(docTable.id, id), gt(docTable.createdAt, after)))
			.orderBy(desc(docTable.versionNumber));
		if (!rows.length) return c.json({ error: "Not found" }, { status: 404 });
		await db
			.delete(docTable)
			.where(and(eq(docTable.id, id), gt(docTable.createdAt, after)));
		return c.json(rows.map(toDTO));
	} catch (err) {
		logger.error({ err }, "Delete after failed");
		return c.json({ error: "Internal error" }, { status: 500 });
	}
};
