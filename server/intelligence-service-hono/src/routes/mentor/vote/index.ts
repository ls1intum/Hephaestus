import { createRouter } from "@/lib/create-app";
import type { AppOpenAPI, AppRouteHandler } from "@/lib/types";
import { voteMessageHandler } from "./vote.handlers";
import { voteMessageRoute } from "./vote.routes";

const router: AppOpenAPI = createRouter().openapi(
	voteMessageRoute,
	voteMessageHandler as AppRouteHandler<typeof voteMessageRoute>,
);

export type VoteRoutes = typeof router;
export default router;
