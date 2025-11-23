import { useQuery } from "@tanstack/react-query";
import { useLocation } from "@tanstack/react-router";
import { useEffect, useMemo } from "react";
import { listWorkspaces1Options } from "@/api/@tanstack/react-query.gen";
import { useWorkspaceStore } from "@/stores/workspace-store";

/**
 * Returns the active workspace slug, the available workspaces and a setter to switch between them.
 * Selection is persisted locally so page reloads keep the same workspace.
 */
export function useActiveWorkspaceSlug() {
	const { selectedSlug, setSelectedSlug } = useWorkspaceStore();
	const query = useQuery(listWorkspaces1Options());
	const workspaces = query.data ?? [];
	const location = useLocation();

	// Extract slug from the current path if present (/w/<slug>/...)
	const slugFromPath = useMemo(() => {
		const match = location.pathname.match(/^\/w\/([^/]+)/);
		return match?.[1];
	}, [location.pathname]);

	const activeSlug =
		slugFromPath && workspaces.some((ws) => ws.workspaceSlug === slugFromPath)
			? slugFromPath
			: selectedSlug &&
					workspaces.some((ws) => ws.workspaceSlug === selectedSlug)
				? selectedSlug
				: (workspaces[0]?.workspaceSlug ?? undefined);

	// Keep store in sync if the stored slug is no longer valid
	useEffect(() => {
		if (selectedSlug && activeSlug !== selectedSlug) {
			setSelectedSlug(activeSlug);
		}
	}, [activeSlug, selectedSlug, setSelectedSlug]);

	return {
		workspaceSlug: activeSlug,
		workspaces,
		selectWorkspace: setSelectedSlug,
		isLoading: query.isLoading,
		error: query.error as Error | null,
	};
}
