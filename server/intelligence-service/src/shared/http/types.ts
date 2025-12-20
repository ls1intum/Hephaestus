import type { OpenAPIHono, RouteConfig, RouteHandler } from "@hono/zod-openapi";
import type { Schema } from "hono";
import type { PinoLogger } from "hono-pino";

// Headers must be lowercase for Hono's case-sensitive header lookup
// These are set by MentorProxyController in the application server
export const WORKSPACE_ID_HEADER = "x-workspace-id";
export const WORKSPACE_SLUG_HEADER = "x-workspace-slug";
export const USER_ID_HEADER = "x-user-id";
export const USER_LOGIN_HEADER = "x-user-login";
export const USER_NAME_HEADER = "x-user-name";

export interface AppBindings {
	Variables: {
		logger: PinoLogger;
		workspaceId: number | null;
		workspaceSlug: string | null;
		userId: number | null;
		userLogin: string | null;
		userName: string | null; // User's display name (may differ from login)
	};
}

export type AppOpenAPI<S extends Schema = Record<string, never>> = OpenAPIHono<AppBindings, S>;
export type AppRouteHandler<R extends RouteConfig> = RouteHandler<R, AppBindings>;
