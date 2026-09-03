import { useQuery } from "@tanstack/react-query";
import { Navigate } from "@tanstack/react-router";
import type { ReactNode } from "react";

import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { Spinner } from "@/components/ui/spinner";

export function WorkspaceRouteGuard({
	children,
	workspaceSlug,
}: {
	children: ReactNode;
	workspaceSlug: string;
}) {
	const query = useQuery({ ...listWorkspacesOptions(), staleTime: 0 });
	if (query.isPending || query.isFetching) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" role="status" aria-label="Loading workspace" />
			</div>
		);
	}
	const workspaces = Array.isArray(query.data) ? query.data : [];
	if (query.error || workspaces.some((workspace) => workspace.workspaceSlug === workspaceSlug)) {
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
