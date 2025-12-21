import { createRouter } from "@/shared/http/hono";
import type { AppOpenAPI, AppRouteHandler } from "@/shared/http/types";
import { getGroupedThreadsHandler, getThreadHandler } from "./threads.handler";
import { getGroupedThreadsRoute, getThreadRoute } from "./threads.routes";

const router: AppOpenAPI = createRouter()
	// Order matters: /grouped must come before /{threadId}
	.openapi(
		getGroupedThreadsRoute,
		getGroupedThreadsHandler as AppRouteHandler<typeof getGroupedThreadsRoute>,
	)
	.openapi(getThreadRoute, getThreadHandler as AppRouteHandler<typeof getThreadRoute>);

export type ThreadsRoutes = typeof router;
export default router;
