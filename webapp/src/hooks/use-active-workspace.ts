import { useQuery } from "@tanstack/react-query";
import { useLocation, useNavigate } from "@tanstack/react-router";
import { useEffect, useRef } from "react";
import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import { useWorkspaceStore } from "@/stores/workspace-store";

/**
 * Returns the active workspace slug, the available workspaces and a setter to switch between them.
 * Selection is persisted locally so page reloads keep the same workspace.
 *
 * When the current workspace becomes inaccessible (e.g., SUSPENDED or PURGED),
 * this hook automatically redirects to another available workspace.
 */
export function useActiveWorkspaceSlug() {
	const { selectedSlug, setSelectedSlug } = useWorkspaceStore();
	const { isAuthenticated, isLoading: authLoading } = useAuth();
	const navigate = useNavigate();
	const query = useQuery({
		...listWorkspacesOptions(),
		enabled: isAuthenticated && !authLoading,
		// Short stale time to ensure we detect workspace status changes quickly
		staleTime: 30_000, // 30 seconds
		refetchOnWindowFocus: true,
	});
	const workspaces = Array.isArray(query.data) ? query.data : [];
	const location = useLocation();

	// Track if we've already attempted a redirect to prevent infinite loops
	const hasAttemptedRedirect = useRef(false);

	// Extract slug from the current path if present (/w/<slug>/...)
	// React Compiler handles memoization automatically
	const pathMatch = location.pathname.match(/^\/w\/([^/]+)/);
	const slugFromPath = pathMatch?.[1];

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

	// Reset redirect tracking when location or workspaces change meaningfully
	// Also reset when workspaces list changes (e.g., after server restart or query refetch)
	const workspaceSlugs = workspaces.map((ws) => ws.workspaceSlug).join(",");
	useEffect(() => {
		hasAttemptedRedirect.current = false;
	}, [slugFromPath, workspaceSlugs]);

	// Check if the slug from the URL path is valid (exists in available workspaces)
	const slugFromPathIsValid = slugFromPath != null && workspaces.some((ws) => ws.workspaceSlug === slugFromPath);

	// Redirect to another workspace when current workspace becomes inaccessible
	// This handles the case where a workspace becomes SUSPENDED/PURGED while the user is viewing it
	useEffect(() => {
		// Only attempt redirect if:
		// 1. We have a slug in the URL path
		// 2. Workspaces have loaded (not loading and we have data or confirmed empty)
		// 3. The slug is not valid (workspace became inaccessible)
		// 4. We haven't already attempted a redirect for this path
		const workspacesLoaded = !query.isLoading && query.data !== undefined;

		if (workspacesLoaded && slugFromPath && !slugFromPathIsValid && !hasAttemptedRedirect.current) {
			hasAttemptedRedirect.current = true;

			const fallbackSlug = workspaces[0]?.workspaceSlug;
			if (fallbackSlug) {
				// Redirect to the same relative path under a different workspace
				const remainder = location.pathname.replace(/^\/w\/[^/]+/, "");
				setSelectedSlug(fallbackSlug);
				navigate({
					to: `/w/${fallbackSlug}${remainder || "/"}` as never,
					replace: true,
				});
			} else {
				// No workspaces available, redirect to home
				navigate({
					to: "/",
					replace: true,
				});
			}
		}
	}, [
		slugFromPath,
		slugFromPathIsValid,
		query.isLoading,
		query.data,
		workspaces,
		location.pathname,
		navigate,
		setSelectedSlug,
	]);

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
