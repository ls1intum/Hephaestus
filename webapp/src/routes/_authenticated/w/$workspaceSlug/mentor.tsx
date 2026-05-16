import { createFileRoute, Navigate, Outlet } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/mentor")({
	component: MentorLayout,
});

function MentorLayout() {
	const { workspaceSlug, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const { mentorEnabled, isLoading: featuresLoading } = useWorkspaceFeatures();

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	if (!featuresLoading && !mentorEnabled && workspaceSlug) {
		return <Navigate to="/w/$workspaceSlug" params={{ workspaceSlug }} replace />;
	}

	if (featuresLoading || !mentorEnabled) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	return <Outlet />;
}
