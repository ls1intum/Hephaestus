import type { OpenAPIHono, RouteConfig, RouteHandler } from "@hono/zod-openapi";
import type { Schema } from "hono";
import type { PinoLogger } from "hono-pino";

export const WORKSPACE_SLUG_HEADER = "x-workspace-slug";

export interface AppBindings {
	Variables: {
		logger: PinoLogger;
		workspaceSlug: string | null;
	};
}

export type AppOpenAPI<S extends Schema = Record<string, never>> = OpenAPIHono<AppBindings, S>;
export type AppRouteHandler<R extends RouteConfig> = RouteHandler<R, AppBindings>;
