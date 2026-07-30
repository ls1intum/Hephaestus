import { createFileRoute, Navigate, Outlet } from "@tanstack/react-router";
import { StandardPageSurface } from "@/components/core/StandardPageSurface";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { useFeatureFlag } from "@/integrations/feature-flags";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/mentor")({
	staticData: { surface: "fullscreen" },
	component: MentorLayout,
});

function MentorLayout() {
	const { workspaceSlug, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const { mentorEnabled, isLoading: featuresLoading } = useWorkspaceFeatures(workspaceSlug);
	const { enabled: hasMentorAccess, isLoading: accessLoading } = useFeatureFlag("MENTOR_ACCESS");

	if (!workspaceSlug && !isWorkspaceLoading) {
		return (
			<StandardPageSurface className="h-full overflow-auto">
				<NoWorkspace />
			</StandardPageSurface>
		);
	}

	if (!featuresLoading && !accessLoading && (!mentorEnabled || !hasMentorAccess) && workspaceSlug) {
		return <Navigate to="/w/$workspaceSlug" params={{ workspaceSlug }} replace />;
	}

	if (featuresLoading || accessLoading || !mentorEnabled || !hasMentorAccess) {
		return (
			<div className="flex min-h-0 flex-1 items-center justify-center">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	return <Outlet />;
}
