import { createFileRoute, Navigate, Outlet } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/practices")({
	component: PracticesLayout,
	staticData: {
		workspaceSwitch: { target: "admin.practices" },
	},
});

function PracticesLayout() {
	const { workspaceSlug } = Route.useParams();
	const { isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const { practicesEnabled, isLoading: featuresLoading } = useWorkspaceFeatures(workspaceSlug);

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	if (!featuresLoading && !practicesEnabled && workspaceSlug) {
		return <Navigate to="/w/$workspaceSlug/admin/settings" params={{ workspaceSlug }} replace />;
	}

	if (featuresLoading || !practicesEnabled) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	return <Outlet />;
}
