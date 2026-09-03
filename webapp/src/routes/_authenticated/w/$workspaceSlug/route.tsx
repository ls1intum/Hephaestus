import { createFileRoute, Outlet } from "@tanstack/react-router";

import { WorkspaceRouteGuard } from "@/routes/-WorkspaceRouteGuard";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug")({
	component: WorkspaceLayout,
});

function WorkspaceLayout() {
	const { workspaceSlug } = Route.useParams();
	return (
		<WorkspaceRouteGuard workspaceSlug={workspaceSlug}>
			<Outlet />
		</WorkspaceRouteGuard>
	);
}
