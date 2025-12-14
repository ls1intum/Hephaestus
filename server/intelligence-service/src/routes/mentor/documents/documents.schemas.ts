import { z } from "@hono/zod-openapi";

export const DocumentKindEnum = z.enum(["text"]).openapi("DocumentKind");

export const CreateDocumentRequestSchema = z
	.object({
		title: z.string().min(1).max(255),
		content: z.string().min(1),
		kind: DocumentKindEnum,
	})
	.openapi("CreateDocumentRequest");

export const UpdateDocumentRequestSchema = CreateDocumentRequestSchema;

export const DocumentSchema = z
	.object({
		id: z.string().uuid(),
		versionNumber: z.number().int(),
		createdAt: z.string().datetime(),
		title: z.string(),
		content: z.string(),
		kind: DocumentKindEnum,
		userId: z.number().int(),
	})
	.openapi("Document");

export const DocumentSummarySchema = z
	.object({
		id: z.string().uuid(),
		title: z.string(),
		kind: DocumentKindEnum,
		createdAt: z.string().datetime(),
		userId: z.number().int(),
	})
	.openapi("DocumentSummary");

export const DocumentIdParamsSchema = z
	.object({ id: z.string().uuid() })
	.openapi("DocumentIdParams");
export type DocumentIdParams = z.infer<typeof DocumentIdParamsSchema>;

export const VersionParamsSchema = z
	.object({ id: z.string().uuid(), versionNumber: z.coerce.number().int() })
	.openapi("DocumentVersionParams");
export type VersionParams = z.infer<typeof VersionParamsSchema>;

export const PaginationQuerySchema = z
	.object({
		page: z.coerce.number().int().min(0).default(0),
		size: z.coerce.number().int().min(1).max(100).default(20),
	})
	.openapi("PaginationQuery");

export const DeleteAfterQuerySchema = z
	.object({ after: z.string().datetime() })
	.openapi("DeleteAfterQuery");

export type CreateDocumentRequest = z.infer<typeof CreateDocumentRequestSchema>;
export type UpdateDocumentRequest = z.infer<typeof UpdateDocumentRequestSchema>;
export type Document = z.infer<typeof DocumentSchema>;
export type DocumentSummary = z.infer<typeof DocumentSummarySchema>;
