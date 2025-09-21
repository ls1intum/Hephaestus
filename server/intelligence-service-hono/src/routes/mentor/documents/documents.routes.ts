import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent, jsonContentRequired } from "stoker/openapi/helpers";
import {
	CreateDocumentRequestSchema,
	DeleteAfterQuerySchema,
	DocumentIdParamsSchema,
	DocumentSchema,
	DocumentSummarySchema,
	PaginationQuerySchema,
	UpdateDocumentRequestSchema,
	VersionParamsSchema,
} from "./documents.schemas";

export const createDocumentRoute = createRoute({
	path: "/documents",
	method: "post",
	tags: ["documents"],
	summary: "Create a new document",
	request: {
		body: jsonContentRequired(CreateDocumentRequestSchema, "Create document"),
	},
	responses: {
		[HttpStatusCodes.CREATED]: jsonContent(DocumentSchema, "Created document"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export const getDocumentRoute = createRoute({
	path: "/documents/{id}",
	method: "get",
	tags: ["documents"],
	summary: "Get latest version of a document",
	request: { params: DocumentIdParamsSchema },
	responses: {
		[HttpStatusCodes.OK]: jsonContent(DocumentSchema, "Document"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(
			z.object({ error: z.string() }),
			"Not found",
		),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export const updateDocumentRoute = createRoute({
	path: "/documents/{id}",
	method: "put",
	tags: ["documents"],
	summary: "Update a document (creates new version)",
	request: {
		params: DocumentIdParamsSchema,
		body: jsonContentRequired(UpdateDocumentRequestSchema, "Update document"),
	},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(DocumentSchema, "Updated document"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(
			z.object({ error: z.string() }),
			"Not found",
		),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export const deleteDocumentRoute = createRoute({
	path: "/documents/{id}",
	method: "delete",
	tags: ["documents"],
	summary: "Delete a document and all versions",
	request: { params: DocumentIdParamsSchema },
	responses: {
		[HttpStatusCodes.NO_CONTENT]: { description: "Deleted" },
		[HttpStatusCodes.NOT_FOUND]: jsonContent(
			z.object({ error: z.string() }),
			"Not found",
		),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export const listDocumentsRoute = createRoute({
	path: "/documents",
	method: "get",
	tags: ["documents"],
	summary: "List latest version of documents (no auth; all users)",
	request: { query: PaginationQuerySchema },
	responses: {
		[HttpStatusCodes.OK]: jsonContent(
			z.array(DocumentSummarySchema),
			"Document summaries",
		),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export const listVersionsRoute = createRoute({
	path: "/documents/{id}/versions",
	method: "get",
	tags: ["documents"],
	summary: "List versions of a document",
	request: { params: DocumentIdParamsSchema, query: PaginationQuerySchema },
	responses: {
		[HttpStatusCodes.OK]: jsonContent(
			z.array(DocumentSchema),
			"Document versions",
		),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(
			z.object({ error: z.string() }),
			"Not found",
		),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export const getVersionRoute = createRoute({
	path: "/documents/{id}/versions/{versionNumber}",
	method: "get",
	tags: ["documents"],
	summary: "Get specific version",
	request: { params: VersionParamsSchema },
	responses: {
		[HttpStatusCodes.OK]: jsonContent(DocumentSchema, "Document"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(
			z.object({ error: z.string() }),
			"Not found",
		),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export const deleteAfterRoute = createRoute({
	path: "/documents/{id}/versions",
	method: "delete",
	tags: ["documents"],
	summary: "Delete versions after timestamp",
	request: { params: DocumentIdParamsSchema, query: DeleteAfterQuerySchema },
	responses: {
		[HttpStatusCodes.OK]: jsonContent(
			z.array(DocumentSchema),
			"Deleted versions",
		),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(
			z.object({ error: z.string() }),
			"Not found",
		),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export type HandleCreateDocumentRoute = typeof createDocumentRoute;
export type HandleGetDocumentRoute = typeof getDocumentRoute;
export type HandleUpdateDocumentRoute = typeof updateDocumentRoute;
export type HandleDeleteDocumentRoute = typeof deleteDocumentRoute;
export type HandleListDocumentsRoute = typeof listDocumentsRoute;
export type HandleListVersionsRoute = typeof listVersionsRoute;
export type HandleGetVersionRoute = typeof getVersionRoute;
export type HandleDeleteAfterRoute = typeof deleteAfterRoute;
