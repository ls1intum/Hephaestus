import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent } from "stoker/openapi/helpers";
import { ChatThreadGroupSchema } from "./threads.schemas";

export const getGroupedThreadsRoute = createRoute({
	path: "/threads/grouped",
	method: "get",
	tags: ["mentor"],
	summary: "List chat threads grouped by time buckets",
	operationId: "getGroupedThreads",
	request: {},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(
			z.array(ChatThreadGroupSchema),
			"Grouped chat threads",
		),
	},
});

export type HandleGetGroupedThreadsRoute = typeof getGroupedThreadsRoute;
