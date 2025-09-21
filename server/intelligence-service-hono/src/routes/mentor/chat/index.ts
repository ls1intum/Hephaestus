import { createRouter } from "@/lib/create-app";
import type { AppOpenAPI, AppRouteHandler } from "@/lib/types";

import { getThreadHandler, mentorChatHandler } from "./chat.handlers";
import { getThreadRoute, mentorChatRoute } from "./chat.routes";

const router: AppOpenAPI = createRouter()
	.openapi(
		mentorChatRoute,
		mentorChatHandler as AppRouteHandler<typeof mentorChatRoute>,
	)
	.openapi(
		getThreadRoute,
		getThreadHandler as AppRouteHandler<typeof getThreadRoute>,
	);

export type ChatRoutes = typeof router;
export default router;
