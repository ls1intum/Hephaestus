import { createRouter } from "@/lib/create-app";
import type { AppOpenAPI, AppRouteHandler } from "@/lib/types";
import { getGroupedThreadsHandler } from "./threads.handlers";
import { getGroupedThreadsRoute } from "./threads.routes";

const router: AppOpenAPI = createRouter().openapi(
	getGroupedThreadsRoute,
	getGroupedThreadsHandler as AppRouteHandler<typeof getGroupedThreadsRoute>,
);

export type ThreadsRoutes = typeof router;
export default router;
