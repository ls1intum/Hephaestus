import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
	listPracticeHealthOptions,
	listPracticeReportsOptions,
} from "@/api/@tanstack/react-query.gen";
import { PracticeOverviewPage } from "@/components/practices/overview/PracticeOverviewPage";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useWorkspaceAccess } from "@/hooks/use-workspace-access";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";
import { httpStatusOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/practice-overview/")({
	component: PracticeOverviewContainer,
});

function PracticeOverviewContainer() {
	const { workspaceSlug, isAdmin, isLoading: accessLoading } = useWorkspaceAccess();
	const { practicesEnabled } = useWorkspaceFeatures(workspaceSlug);
	const slug = workspaceSlug ?? "";
	const hasWorkspace = Boolean(workspaceSlug);
	const showNoWorkspace = !accessLoading && !hasWorkspace;

	const canQueryHealth = hasWorkspace && practicesEnabled;
	const canQueryRoster = canQueryHealth && isAdmin;

	const healthQuery = useQuery({
		...listPracticeHealthOptions({ path: { workspaceSlug: slug } }),
		enabled: canQueryHealth,
	});
	const rosterQuery = useQuery({
		...listPracticeReportsOptions({ path: { workspaceSlug: slug } }),
		enabled: canQueryRoster,
		// Each roster fetch is an audited disclosure (the server writes a PRACTICE_ROSTER audit
		// row per request), so don't refetch gratuitously on focus/reconnect.
		refetchOnWindowFocus: false,
		refetchOnReconnect: false,
	});

	if (showNoWorkspace) {
		return <NoWorkspace />;
	}

	const serverRefusedAccess =
		httpStatusOf(healthQuery.error) === 403 || (isAdmin && httpStatusOf(rosterQuery.error) === 403);
	const isForbidden = !accessLoading && hasWorkspace && (!practicesEnabled || serverRefusedAccess);
	const isError = !isForbidden && (healthQuery.isError || rosterQuery.isError);

	return (
		<PracticeOverviewPage
			workspaceSlug={slug}
			health={healthQuery.data}
			roster={rosterQuery.data}
			isLoading={
				accessLoading ||
				(canQueryHealth && healthQuery.isPending) ||
				(canQueryRoster && rosterQuery.isPending)
			}
			isForbidden={isForbidden}
			isError={isError}
			showRoster={isAdmin}
			onRetry={() => {
				healthQuery.refetch();
				if (isAdmin) rosterQuery.refetch();
			}}
		/>
	);
}
