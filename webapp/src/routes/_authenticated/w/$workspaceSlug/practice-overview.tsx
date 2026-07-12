import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { PracticeOverviewPage } from "@/components/practices/PracticeOverviewPage";
import { Spinner } from "@/components/ui/spinner";
import { useWorkspaceAccess } from "@/hooks/use-workspace-access";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/practice-overview")({
	component: PracticeOverviewRoute,
});

/**
 * The mentor view: workspace health, the developer roster matrix and the per-developer
 * drill-down. Workspace ADMIN or OWNER only (the server enforces this too); non-admins are
 * redirected the same way the admin layout does. Also gated on the practices feature.
 */
function PracticeOverviewRoute() {
	const { workspaceSlug, isAdmin, isLoading: accessLoading } = useWorkspaceAccess();
	const { practicesEnabled, isLoading: featuresLoading } = useWorkspaceFeatures();
	const navigate = useNavigate();
	const isLoading = accessLoading || featuresLoading;
	const allowed = isAdmin && practicesEnabled;

	useEffect(() => {
		if (!isLoading && !allowed && workspaceSlug) {
			navigate({
				to: "/w/$workspaceSlug",
				params: { workspaceSlug },
				replace: true,
			});
		}
	}, [isLoading, allowed, workspaceSlug, navigate]);

	if (isLoading || !allowed || !workspaceSlug) {
		return (
			<div className="flex h-96 items-center justify-center">
				<Spinner className="size-8" />
			</div>
		);
	}

	return <PracticeOverviewPage workspaceSlug={workspaceSlug} />;
}
