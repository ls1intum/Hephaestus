import { createContext, use } from "react";
import type { Workspace } from "@/api/types.gen";

/**
 * Context for accessing the current workspace data.
 * This is populated by the workspace layout route loader.
 */
export const WorkspaceContext = createContext<Workspace | null>(null);

/**
 * Hook to access the current workspace.
 * Must be used within a workspace route (under /w/$workspaceSlug).
 *
 * @throws Error if used outside of a workspace route
 */
export function useWorkspace(): Workspace {
	const workspace = use(WorkspaceContext);
	if (!workspace) {
		throw new Error("useWorkspace must be used within a workspace route");
	}
	return workspace;
}
