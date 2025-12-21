import type { Context, Next } from "hono";
import type { AppBindings } from "./types";
import {
	USER_FIRST_NAME_HEADER,
	USER_ID_HEADER,
	USER_LOGIN_HEADER,
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
 * - X-User-First-Name: The user's first name (for personalization)
 */
export function workspaceContext() {
	return async (c: Context<AppBindings>, next: Next) => {
		const workspaceIdHeader = c.req.header(WORKSPACE_ID_HEADER);
		const workspaceSlug = c.req.header(WORKSPACE_SLUG_HEADER) ?? null;
		const userIdHeader = c.req.header(USER_ID_HEADER);
		const userLogin = c.req.header(USER_LOGIN_HEADER) ?? null;
		const userFirstName = c.req.header(USER_FIRST_NAME_HEADER) ?? null;

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
		c.set("userName", userFirstName);
		await next();
	};
}

/**
 * Validated context with non-null userId and workspaceId.
 * Returned by `getValidatedContext` when context is valid.
 */
export interface ValidatedContext {
	readonly userId: number;
	readonly workspaceId: number;
	readonly workspaceSlug: string | null;
	readonly userLogin: string | null;
	readonly userName: string | null;
}

/**
 * Extracts and validates user context from Hono context.
 *
 * Use this in handlers that require authenticated user context.
 * Returns `null` if userId or workspaceId is missing.
 *
 * @example
 * ```ts
 * const ctx = getValidatedContext(c);
 * if (!ctx) {
 *   return c.json({ error: ERROR_MESSAGES.MISSING_CONTEXT }, { status: HTTP_STATUS.BAD_REQUEST });
 * }
 * // ctx.userId and ctx.workspaceId are guaranteed to be numbers
 * ```
 */
export function getValidatedContext(c: Context<AppBindings>): ValidatedContext | null {
	const userId = c.get("userId");
	const workspaceId = c.get("workspaceId");

	if (userId === null || workspaceId === null) {
		return null;
	}

	return {
		userId,
		workspaceId,
		workspaceSlug: c.get("workspaceSlug"),
		userLogin: c.get("userLogin"),
		userName: c.get("userName"),
	};
}
