import { createFileRoute, Navigate } from "@tanstack/react-router";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useAuth } from "@/integrations/auth/AuthContext";

export const Route = createFileRoute("/_authenticated/")({
	component: RedirectToWorkspace,
});

function RedirectToWorkspace() {
	const { isAuthenticated } = useAuth();
	const { workspaceSlug, workspaces, isLoading } = useActiveWorkspaceSlug();

	if (!isAuthenticated || isLoading) {
		return null;
	}

	const targetSlug = workspaceSlug ?? workspaces[0]?.workspaceSlug;
	if (targetSlug) {
		return <Navigate to="/w/$workspaceSlug" params={{ workspaceSlug: targetSlug }} replace />;
	}

	if (!workspaceSlug && workspaces.length === 0) {
		return <NoWorkspace />;
	}

	return null;
}
