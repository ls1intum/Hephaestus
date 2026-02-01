import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent } from "stoker/openapi/helpers";
import { EXPORTED_TAG } from "@/shared/http/exported-tag";
import { ErrorResponseSchema } from "@/shared/http/schemas";
import { ChatThreadGroupSchema, ThreadIdParamsSchema, threadDetailSchema } from "./threads.schema";

// ─────────────────────────────────────────────────────────────────────────────
// GET /mentor/threads/grouped - List threads grouped by time
// ─────────────────────────────────────────────────────────────────────────────

export const getGroupedThreadsRoute = createRoute({
	path: "/grouped",
	method: "get" as const,
	tags: ["mentor", ...EXPORTED_TAG],
	summary: "List chat threads grouped by time buckets",
	operationId: "getGroupedThreads",
	request: {},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(z.array(ChatThreadGroupSchema), "Grouped chat threads"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing context"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
	},
});

export type HandleGetGroupedThreadsRoute = typeof getGroupedThreadsRoute;

// ─────────────────────────────────────────────────────────────────────────────
// GET /mentor/threads/{threadId} - Get thread detail with messages
// ─────────────────────────────────────────────────────────────────────────────

export const getThreadRoute = createRoute({
	path: "/{threadId}",
	method: "get" as const,
	tags: ["mentor", ...EXPORTED_TAG],
	summary: "Get mentor chat thread detail",
	operationId: "getThread",
	request: {
		params: ThreadIdParamsSchema,
	},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(threadDetailSchema, "Thread detail with messages"),
		[HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Missing required context"),
		[HttpStatusCodes.NOT_FOUND]: jsonContent(ErrorResponseSchema, "Thread not found"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(ErrorResponseSchema, "Internal error"),
		[HttpStatusCodes.SERVICE_UNAVAILABLE]: jsonContent(
			ErrorResponseSchema,
			"Service temporarily unavailable",
		),
	},
});

export type HandleGetThreadRoute = typeof getThreadRoute;
