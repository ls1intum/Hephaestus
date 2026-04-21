import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Navigate, Outlet, useMatches } from "@tanstack/react-router";
import { listWorkspacesOptions } from "@/api/@tanstack/react-query.gen";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useAuth } from "@/integrations/auth/AuthContext";
import { buildWorkspaceSwitchPlan, getWorkspaceRouteMatch } from "@/lib/workspace-switching";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug")({
	component: WorkspaceLayout,
});

function WorkspaceLayout() {
	const { workspaceSlug } = Route.useParams();
	const { isAuthenticated, isLoading: authLoading } = useAuth();
	const workspaceRoute = useMatches({
		select: (matches) => getWorkspaceRouteMatch(matches),
	});
	const workspaceQuery = useQuery({
		...listWorkspacesOptions(),
		enabled: isAuthenticated && !authLoading,
		staleTime: 30_000,
		refetchOnWindowFocus: true,
	});

	const workspaces = Array.isArray(workspaceQuery.data) ? workspaceQuery.data : [];
	const activeWorkspace = workspaces.find((workspace) => workspace.workspaceSlug === workspaceSlug);

	if (authLoading || workspaceQuery.isLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}

	if (activeWorkspace) {
		return <Outlet />;
	}

	const fallbackSlug = workspaces[0]?.workspaceSlug;
	if (!fallbackSlug) {
		return <NoWorkspace />;
	}

	const target = buildWorkspaceSwitchPlan(workspaceRoute, fallbackSlug);

	if (target.kind === "relative") {
		if (target.preserveSearch) {
			return (
				<Navigate
					from={target.from as never}
					to={target.to as never}
					params={{ workspaceSlug: fallbackSlug } as never}
					search={true}
					replace
				/>
			);
		}

		return (
			<Navigate
				from={target.from as never}
				to={target.to as never}
				params={{ workspaceSlug: fallbackSlug } as never}
				replace
			/>
		);
	}

	return (
		<Navigate
			to={target.to}
			params={target.params}
			{...(target.preserveSearch ? { search: (prev) => prev } : {})}
			replace
		/>
	);
}
