import { ERROR_MESSAGES, HTTP_STATUS } from "@/shared/constants";
import type { DocumentKind } from "@/shared/document";
import type { AppRouteHandler } from "@/shared/http/types";
import { extractErrorMessage, getLogger } from "@/shared/utils";
import {
	createDocument,
	deleteDocument,
	deleteVersionsAfter,
	getDocumentById,
	getDocumentVersion,
	listDocuments,
	listVersions,
	updateDocument,
} from "./data";
import type {
	createDocumentRoute,
	deleteAfterRoute,
	deleteDocumentRoute,
	getDocumentRoute,
	getVersionRoute,
	listDocumentsRoute,
	listVersionsRoute,
	updateDocumentRoute,
} from "./documents.routes";

export const createDocumentHandler: AppRouteHandler<typeof createDocumentRoute> = async (c) => {
	const logger = getLogger(c);
	const workspaceId = c.get("workspaceId");
	const body = c.req.valid("json");

	if (!workspaceId) {
		return c.json({ error: "Missing workspace context" }, { status: HTTP_STATUS.BAD_REQUEST });
	}

	try {
		const doc = await createDocument({
			title: body.title,
			content: body.content,
			kind: body.kind as DocumentKind,
			workspaceId,
		});

		if (!doc) {
			throw new Error(ERROR_MESSAGES.INSERT_FAILED);
		}
		return c.json(doc, { status: HTTP_STATUS.CREATED });
	} catch (err) {
		logger.error({ err: extractErrorMessage(err) }, "Create document failed");
		return c.json(
			{ error: ERROR_MESSAGES.INTERNAL_ERROR },
			{ status: HTTP_STATUS.INTERNAL_SERVER_ERROR },
		);
	}
};

export const getDocumentHandler: AppRouteHandler<typeof getDocumentRoute> = async (c) => {
	const { id } = c.req.valid("param");
	const doc = await getDocumentById(id);
	if (!doc) {
		return c.json({ error: ERROR_MESSAGES.DOCUMENT_NOT_FOUND }, { status: HTTP_STATUS.NOT_FOUND });
	}
	return c.json(doc, { status: HTTP_STATUS.OK });
};

export const updateDocumentHandler: AppRouteHandler<typeof updateDocumentRoute> = async (c) => {
	const logger = getLogger(c);
	const { id } = c.req.valid("param");
	const body = c.req.valid("json");
	try {
		const doc = await updateDocument(id, {
			title: body.title,
			content: body.content,
			kind: body.kind as DocumentKind,
		});

		if (!doc) {
			return c.json(
				{ error: ERROR_MESSAGES.DOCUMENT_NOT_FOUND },
				{ status: HTTP_STATUS.NOT_FOUND },
			);
		}
		return c.json(doc, { status: HTTP_STATUS.OK });
	} catch (err) {
		logger.error({ err: extractErrorMessage(err) }, "Update document failed");
		return c.json(
			{ error: ERROR_MESSAGES.INTERNAL_ERROR },
			{ status: HTTP_STATUS.INTERNAL_SERVER_ERROR },
		);
	}
};

export const deleteDocumentHandler: AppRouteHandler<typeof deleteDocumentRoute> = async (c) => {
	const logger = getLogger(c);
	const { id } = c.req.valid("param");
	try {
		const existing = await getDocumentById(id);
		if (!existing) {
			return c.json(
				{ error: ERROR_MESSAGES.DOCUMENT_NOT_FOUND },
				{ status: HTTP_STATUS.NOT_FOUND },
			);
		}

		await deleteDocument(id);
		return c.body(null, { status: HTTP_STATUS.NO_CONTENT });
	} catch (err) {
		logger.error({ err: extractErrorMessage(err) }, "Delete document failed");
		return c.json(
			{ error: ERROR_MESSAGES.INTERNAL_ERROR },
			{ status: HTTP_STATUS.INTERNAL_SERVER_ERROR },
		);
	}
};

export const listDocumentsHandler: AppRouteHandler<typeof listDocumentsRoute> = async (c) => {
	const { page, size } = c.req.valid("query");
	const docs = await listDocuments();

	const start = page * size;
	const end = start + size;
	const pageItems = docs.slice(start, end).map((d) => ({
		id: d.id,
		title: d.title,
		kind: d.kind,
		createdAt: d.createdAt,
		userId: d.userId,
	}));
	return c.json(pageItems, { status: HTTP_STATUS.OK });
};

export const listVersionsHandler: AppRouteHandler<typeof listVersionsRoute> = async (c) => {
	const { id } = c.req.valid("param");
	const { page, size } = c.req.valid("query");
	const versions = await listVersions(id);

	if (versions.length === 0) {
		return c.json({ error: ERROR_MESSAGES.DOCUMENT_NOT_FOUND }, { status: HTTP_STATUS.NOT_FOUND });
	}

	// Reverse to get newest first for pagination
	const reversed = [...versions].reverse();
	const start = page * size;
	const end = start + size;
	return c.json(reversed.slice(start, end), { status: HTTP_STATUS.OK });
};

export const getVersionHandler: AppRouteHandler<typeof getVersionRoute> = async (c) => {
	const { id, versionNumber } = c.req.valid("param");
	const doc = await getDocumentVersion(id, versionNumber);
	if (!doc) {
		return c.json({ error: ERROR_MESSAGES.DOCUMENT_NOT_FOUND }, { status: HTTP_STATUS.NOT_FOUND });
	}
	return c.json(doc, { status: HTTP_STATUS.OK });
};

export const deleteAfterHandler: AppRouteHandler<typeof deleteAfterRoute> = async (c) => {
	const logger = getLogger(c);
	const { id } = c.req.valid("param");
	const { after } = c.req.valid("query");
	try {
		// Get versions to return before deleting
		const versions = await listVersions(id);
		const afterDate = new Date(after);
		const toDelete = versions.filter((v) => new Date(v.createdAt) > afterDate);

		if (toDelete.length === 0) {
			return c.json(
				{ error: ERROR_MESSAGES.DOCUMENT_NOT_FOUND },
				{ status: HTTP_STATUS.NOT_FOUND },
			);
		}

		// Delete versions after the specified timestamp
		const minVersionToDelete = Math.min(...toDelete.map((v) => v.versionNumber));
		await deleteVersionsAfter(id, minVersionToDelete - 1);

		return c.json(toDelete, { status: HTTP_STATUS.OK });
	} catch (err) {
		logger.error({ err: extractErrorMessage(err) }, "Delete after failed");
		return c.json(
			{ error: ERROR_MESSAGES.INTERNAL_ERROR },
			{ status: HTTP_STATUS.INTERNAL_SERVER_ERROR },
		);
	}
};
