import { createRouter } from "@/lib/create-app";
import type { AppOpenAPI, AppRouteHandler } from "@/lib/types";

import { mentorChatHandler } from "./mentor.handlers";
import { mentorChatRoute } from "./mentor.routes";

const router: AppOpenAPI = createRouter().openapi(
	mentorChatRoute,
	mentorChatHandler as AppRouteHandler<typeof mentorChatRoute>,
);

export type MentorRoutes = typeof router;
export default router;
