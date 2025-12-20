import type { Context, Next } from "hono";
import type { AppBindings } from "./types";
import {
	USER_ID_HEADER,
	USER_LOGIN_HEADER,
	USER_NAME_HEADER,
	WORKSPACE_ID_HEADER,
	WORKSPACE_SLUG_HEADER,
} from "./types";

/**
 * Middleware that extracts workspace and user context from headers.
 *
 * Headers are set by MentorProxyController in the application server when
 * forwarding requests from /workspaces/{workspaceSlug}/mentor/** to /mentor/**.
 *
 * - X-Workspace-Id: The workspace's database ID (required for persistence)
 * - X-Workspace-Slug: The workspace's URL slug (for logging/debugging)
 * - X-User-Id: The user's database ID (for document ownership)
 * - X-User-Login: The user's login/username (for logging/debugging)
 * - X-User-Name: The user's display name (for personalization)
 */
export function workspaceContext() {
	return async (c: Context<AppBindings>, next: Next) => {
		const workspaceIdHeader = c.req.header(WORKSPACE_ID_HEADER);
		const workspaceSlug = c.req.header(WORKSPACE_SLUG_HEADER) ?? null;
		const userIdHeader = c.req.header(USER_ID_HEADER);
		const userLogin = c.req.header(USER_LOGIN_HEADER) ?? null;
		const userName = c.req.header(USER_NAME_HEADER) ?? null;

		let workspaceId: number | null = null;
		if (workspaceIdHeader) {
			const parsed = Number.parseInt(workspaceIdHeader, 10);
			if (!Number.isNaN(parsed)) {
				workspaceId = parsed;
			}
		}

		let userId: number | null = null;
		if (userIdHeader) {
			const parsed = Number.parseInt(userIdHeader, 10);
			if (!Number.isNaN(parsed)) {
				userId = parsed;
			}
		}

		c.set("workspaceId", workspaceId);
		c.set("workspaceSlug", workspaceSlug);
		c.set("userId", userId);
		c.set("userLogin", userLogin);
		c.set("userName", userName);
		await next();
	};
}
