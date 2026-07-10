import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
	getCohortPracticeStatusOptions,
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

	const canQueryCohort = hasWorkspace && practicesEnabled;
	const canQueryRoster = canQueryCohort && isAdmin;

	const cohortQuery = useQuery({
		...getCohortPracticeStatusOptions({ path: { workspaceSlug: slug } }),
		enabled: canQueryCohort,
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
		httpStatusOf(cohortQuery.error) === 403 || (isAdmin && httpStatusOf(rosterQuery.error) === 403);
	const isForbidden = !accessLoading && hasWorkspace && (!practicesEnabled || serverRefusedAccess);
	const isError = !isForbidden && (cohortQuery.isError || rosterQuery.isError);

	return (
		<PracticeOverviewPage
			workspaceSlug={slug}
			cohort={cohortQuery.data}
			roster={rosterQuery.data}
			isLoading={
				accessLoading ||
				(canQueryCohort && cohortQuery.isPending) ||
				(canQueryRoster && rosterQuery.isPending)
			}
			isForbidden={isForbidden}
			isError={isError}
			showRoster={isAdmin}
			onRetry={() => {
				cohortQuery.refetch();
				if (isAdmin) rosterQuery.refetch();
			}}
		/>
	);
}
