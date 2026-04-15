import { createFileRoute, Navigate, Outlet } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/practices")({
	component: PracticesLayout,
});

function PracticesLayout() {
	const { workspaceSlug } = Route.useParams();
	const { practicesEnabled, isLoading: featuresLoading } = useWorkspaceFeatures(workspaceSlug);

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
