import { createRouter } from "@/shared/http/hono";
import type { AppOpenAPI, AppRouteHandler } from "@/shared/http/types";

import { mentorChatHandler } from "./chat.handler";
import { mentorChatRoute } from "./chat.routes";

const router: AppOpenAPI = createRouter().openapi(
	mentorChatRoute,
	mentorChatHandler as AppRouteHandler<typeof mentorChatRoute>,
);

export type ChatRoutes = typeof router;
export default router;
