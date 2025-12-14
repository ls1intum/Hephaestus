import type { Context, Next } from "hono";
import type { AppBindings } from "@/lib/types";
import { WORKSPACE_SLUG_HEADER } from "@/lib/types";

/**
 * Middleware that extracts the workspace slug from the X-Workspace-Slug header
 * and makes it available in the request context.
 *
 * The workspace slug is set by the application server proxy when forwarding
 * requests from /workspaces/{workspaceSlug}/mentor/** to /mentor/**.
 */
export function workspaceContext() {
	return async (c: Context<AppBindings>, next: Next) => {
		const workspaceSlug = c.req.header(WORKSPACE_SLUG_HEADER) ?? null;
		c.set("workspaceSlug", workspaceSlug);
		await next();
	};
}
