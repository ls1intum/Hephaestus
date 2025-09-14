import { createRouter } from "@/lib/create-app";
import type { AppOpenAPI, AppRouteHandler } from "@/lib/types";
import { generatePoemHandler } from "./poem.handlers";
import { generatePoem } from "./poem.routes";

const router: AppOpenAPI = createRouter().openapi(
	generatePoem,
	generatePoemHandler as AppRouteHandler<typeof generatePoem>,
);

export type PoemRoutes = typeof router;
export default router;
