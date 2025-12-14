import { createRouter } from "@/shared/http/hono";
import type { AppOpenAPI, AppRouteHandler } from "@/shared/http/types";
import { getGroupedThreadsHandler } from "./threads.handler";
import { getGroupedThreadsRoute } from "./threads.routes";

const router: AppOpenAPI = createRouter().openapi(
	getGroupedThreadsRoute,
	getGroupedThreadsHandler as AppRouteHandler<typeof getGroupedThreadsRoute>,
);

export type ThreadsRoutes = typeof router;
export default router;
