import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent, jsonContentRequired } from "stoker/openapi/helpers";
import { EXPORTED_TAG } from "@/shared/http/exported-tag";
import { ErrorResponseSchema } from "@/shared/http/schemas";
import {
	CreateDocumentRequestSchema,
	DeleteAfterQuerySchema,
	DocumentIdParamsSchema,
	DocumentSchema,
	DocumentSummarySchema,
	PaginationQuerySchema,
	UpdateDocumentRequestSchema,
	VersionParamsSchema,
} from "./documents.schema";

export const createDocumentRoute = createRoute({
	path: "/",
	method: "post" as const,
	tags: ["documents", ...EXPORTED_TAG],
	summary: "Create a new document",
	operationId: "createDocument",
	request: {
		body: jsonContentRequired(CreateDocumentRequestSchema, "Create document"),
	},
	responses: {
		[HttpStatusCodes.CREATED]: jsonContent(DocumentSchema, "Created document"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing required context"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
	},
});

export const getDocumentRoute = createRoute({
	path: "/{id}",
	method: "get" as const,
	tags: ["documents", ...EXPORTED_TAG],
	summary: "Get latest version of a document",
	operationId: "getDocument",
	request: { params: DocumentIdParamsSchema },
	responses: {
		[HttpStatusCodes.OK]: jsonContent(DocumentSchema, "Document"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing context"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(ErrorResponseSchema, "Not found"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
	},
});

export const updateDocumentRoute = createRoute({
	path: "/{id}",
	method: "put" as const,
	tags: ["documents", ...EXPORTED_TAG],
	summary: "Update a document (creates new version)",
	operationId: "updateDocument",
	request: {
		params: DocumentIdParamsSchema,
		body: jsonContentRequired(UpdateDocumentRequestSchema, "Update document"),
	},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(DocumentSchema, "Updated document"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing context"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(ErrorResponseSchema, "Not found"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
	},
});

export const deleteDocumentRoute = createRoute({
	path: "/{id}",
	method: "delete" as const,
	tags: ["documents", ...EXPORTED_TAG],
	summary: "Delete a document and all versions",
	operationId: "deleteDocument",
	request: { params: DocumentIdParamsSchema },
	responses: {
		[HttpStatusCodes.NO_CONTENT]: { description: "Deleted" },
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing context"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(ErrorResponseSchema, "Not found"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
	},
});

export const listDocumentsRoute = createRoute({
	path: "/",
	method: "get" as const,
	tags: ["documents", ...EXPORTED_TAG],
	summary: "List documents owned by the authenticated user",
	operationId: "listDocuments",
	request: { query: PaginationQuerySchema },
	responses: {
		[HttpStatusCodes.OK]: jsonContent(z.array(DocumentSummarySchema), "Document summaries"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing context"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
	},
});

export const listVersionsRoute = createRoute({
	path: "/{id}/versions",
	method: "get" as const,
	tags: ["documents", ...EXPORTED_TAG],
	summary: "List versions of a document",
	operationId: "listVersions",
	request: { params: DocumentIdParamsSchema, query: PaginationQuerySchema },
	responses: {
		[HttpStatusCodes.OK]: jsonContent(z.array(DocumentSchema), "Document versions"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing context"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(ErrorResponseSchema, "Not found"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
	},
});

export const getVersionRoute = createRoute({
	path: "/{id}/versions/{versionNumber}",
	method: "get" as const,
	tags: ["documents", ...EXPORTED_TAG],
	summary: "Get specific version",
	operationId: "getVersion",
	request: { params: VersionParamsSchema },
	responses: {
		[HttpStatusCodes.OK]: jsonContent(DocumentSchema, "Document"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing context"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(ErrorResponseSchema, "Not found"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
	},
});

export const deleteAfterRoute = createRoute({
	path: "/{id}/versions",
	method: "delete" as const,
	tags: ["documents", ...EXPORTED_TAG],
	summary: "Delete versions after timestamp",
	operationId: "deleteDocumentVersionsAfter",
	request: { params: DocumentIdParamsSchema, query: DeleteAfterQuerySchema },
	responses: {
		[HttpStatusCodes.OK]: jsonContent(z.array(DocumentSchema), "Deleted versions"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing context"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(ErrorResponseSchema, "Not found"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
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
