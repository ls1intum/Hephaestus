/**
 * PR Bad Practice Detector Feature
 *
 * Analyzes pull request diffs to detect potential issues:
 * - Code smells
 * - Bad patterns
 * - Missing tests
 * - etc.
 */
import { createRouter } from "@/shared/http/hono";
import type { AppOpenAPI, AppRouteHandler } from "@/shared/http/types";
import { detectBadPracticesHandler } from "./detector.handler";
import { detectBadPractices } from "./detector.routes";

const router: AppOpenAPI = createRouter().openapi(
	detectBadPractices,
	detectBadPracticesHandler as AppRouteHandler<typeof detectBadPractices>,
);

export type DetectorRoutes = typeof router;
export default router;
