import { Navigate } from "@tanstack/react-router";
import type { ReactNode } from "react";

import { Spinner } from "@/components/ui/spinner";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export function WorkspaceRouteGuard({ children }: { children: ReactNode }) {
	const { workspaceSlug, workspaces, isLoading, workspacesLoaded, error } =
		useActiveWorkspaceSlug();
	if (!workspaceSlug) {
		return children;
	}
	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}
	if (
		error ||
		!workspacesLoaded ||
		workspaces.some((workspace) => workspace.workspaceSlug === workspaceSlug)
	) {
		return children;
	}

	const fallbackSlug = workspaces[0]?.workspaceSlug;
	return fallbackSlug ? (
		<Navigate
			to="/w/$workspaceSlug"
			params={{ workspaceSlug: fallbackSlug }}
			search={() => ({})}
			replace
		/>
	) : (
		<Navigate to="/" search={() => ({})} replace />
	);
}
