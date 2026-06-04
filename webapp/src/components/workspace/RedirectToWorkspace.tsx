import { useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useAuth } from "@/integrations/auth/AuthContext";

/**
 * Lands a signed-in visitor on their workspace: navigates (replacing history) to the last selected
 * workspace, or the first available one. Renders nothing while the selection hydrates, and shows
 * {@link NoWorkspace} when the account has no workspace yet. Used by the public index route for
 * authenticated visitors, so they never see the marketing landing page.
 */
export function RedirectToWorkspace() {
	const navigate = useNavigate();
	const { isAuthenticated } = useAuth();
	const { workspaceSlug, workspaces, selectWorkspace, isLoading } = useActiveWorkspaceSlug();

	useEffect(() => {
		if (!isAuthenticated || isLoading) return;
		const targetSlug = workspaceSlug ?? workspaces[0]?.workspaceSlug;
		if (targetSlug) {
			selectWorkspace(targetSlug);
			navigate({
				to: "/w/$workspaceSlug",
				params: { workspaceSlug: targetSlug },
				replace: true,
			});
		}
	}, [isAuthenticated, isLoading, workspaceSlug, workspaces, selectWorkspace, navigate]);

	if (!isAuthenticated || isLoading) {
		return null;
	}

	if (!workspaceSlug && workspaces.length === 0) {
		return <NoWorkspace />;
	}

	return null;
}
