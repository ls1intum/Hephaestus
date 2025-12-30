import { useQuery } from "@tanstack/react-query";
import { useLocation } from "@tanstack/react-router";
import { useEffect } from "react";
import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { extractWorkspaceSlug } from "@/lib/workspace-paths";
import { useWorkspaceStore } from "@/stores/workspace-store";

/**
 * Returns the active workspace slug, the available workspaces and a setter to switch between them.
 * Selection is persisted locally so page reloads keep the same workspace.
 */
export function useActiveWorkspaceSlug() {
	const { selectedSlug, setSelectedSlug } = useWorkspaceStore();
	const { isAuthenticated, isLoading: authLoading } = useAuth();
	const query = useQuery({
		...listWorkspacesOptions(),
		enabled: isAuthenticated && !authLoading,
	});
	const workspaces = Array.isArray(query.data) ? query.data : [];
	const location = useLocation();

	// Extract slug from the current path if present (/w/<slug>/...)
	// Uses the centralized workspace path helper
	const slugFromPath = extractWorkspaceSlug(location.pathname);

	const isValidSlug = (slug?: string) =>
		slug != null && workspaces.some((ws) => ws.workspaceSlug === slug);

	const activeSlug = (() => {
		if (slugFromPath) {
			return isValidSlug(slugFromPath) ? slugFromPath : undefined;
		}

		if (isValidSlug(selectedSlug)) {
			return selectedSlug;
		}

		return workspaces[0]?.workspaceSlug ?? undefined;
	})();

	// Keep store in sync if the stored slug is no longer valid
	useEffect(() => {
		if (selectedSlug && activeSlug !== selectedSlug) {
			setSelectedSlug(activeSlug);
		}
	}, [activeSlug, selectedSlug, setSelectedSlug]);

	// Clear persisted selection when the user logs out to avoid cross-user leakage
	useEffect(() => {
		if (!isAuthenticated && selectedSlug) {
			setSelectedSlug(undefined);
		}
	}, [isAuthenticated, selectedSlug, setSelectedSlug]);

	return {
		workspaceSlug: activeSlug,
		workspaces,
		selectWorkspace: setSelectedSlug,
		isLoading: query.isLoading,
		error: query.error as Error | null,
	};
}
