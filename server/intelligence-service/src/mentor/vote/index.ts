import { createRouter } from "@/shared/http/hono";
import type { AppOpenAPI, AppRouteHandler } from "@/shared/http/types";
import { voteMessageHandler } from "./vote.handler";
import { voteMessageRoute } from "./vote.routes";

const router: AppOpenAPI = createRouter().openapi(
	voteMessageRoute,
	voteMessageHandler as AppRouteHandler<typeof voteMessageRoute>,
);

export type VoteRoutes = typeof router;
export default router;
