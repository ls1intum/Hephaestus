import { Navigate } from "@tanstack/react-router";

import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useAuth } from "@/integrations/auth/AuthContext";

export function RedirectToWorkspace() {
	const { isAuthenticated } = useAuth();
	const { workspaces, isLoading } = useActiveWorkspaceSlug();

	if (!isAuthenticated || isLoading) {
		return null;
	}

	const workspaceSlug = workspaces[0]?.workspaceSlug;
	if (!workspaceSlug) {
		return <NoWorkspace />;
	}

	return <Navigate to="/w/$workspaceSlug" params={{ workspaceSlug }} replace />;
}
