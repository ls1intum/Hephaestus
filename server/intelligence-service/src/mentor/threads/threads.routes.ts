import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent } from "stoker/openapi/helpers";
import { EXPORTED_TAG } from "@/shared/http/exported-tag";
import { ChatThreadGroupSchema } from "./threads.schema";

export const getGroupedThreadsRoute = createRoute({
	path: "/threads/grouped",
	method: "get",
	tags: ["mentor", ...EXPORTED_TAG],
	summary: "List chat threads grouped by time buckets",
	operationId: "getGroupedThreads",
	request: {},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(z.array(ChatThreadGroupSchema), "Grouped chat threads"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			z.object({ error: z.string() }),
			"Internal error",
		),
	},
});

export type HandleGetGroupedThreadsRoute = typeof getGroupedThreadsRoute;
