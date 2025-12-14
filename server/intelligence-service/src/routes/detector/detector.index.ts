import { createRouter } from "@/lib/create-app";
import type { AppOpenAPI, AppRouteHandler } from "@/lib/types";
import { detectBadPracticesHandler } from "./detector.handlers";
import { detectBadPractices } from "./detector.routes";

const router: AppOpenAPI = createRouter().openapi(
	detectBadPractices,
	detectBadPracticesHandler as AppRouteHandler<typeof detectBadPractices>,
);

export type DetectorRoutes = typeof router;
export default router;
