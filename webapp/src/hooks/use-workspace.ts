import { createContext, use } from "react";
import type { Workspace } from "@/api/types.gen";

/**
 * Context for the current workspace, populated by the workspace layout route.
 * @see webapp/src/routes/_authenticated/w/$workspaceSlug.tsx
 */
export const WorkspaceContext = createContext<Workspace | null>(null);

/**
 * Returns the current workspace from the layout route context.
 * Must be used within a workspace route (/w/$workspaceSlug/*).
 * @throws Error when called outside a workspace route
 */
export function useWorkspace(): Workspace {
	const workspace = use(WorkspaceContext);
	if (!workspace) {
		throw new Error("useWorkspace must be used within a workspace route");
	}
	return workspace;
}
