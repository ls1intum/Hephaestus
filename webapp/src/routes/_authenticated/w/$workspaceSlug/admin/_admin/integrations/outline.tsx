import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { listConnectionSyncJobsOptions } from "@/api/@tanstack/react-query.gen";
import { IntegrationPageHeader } from "@/components/admin/integrations/IntegrationPageHeader";
import { JobHistoryCard } from "@/components/admin/integrations/JobHistoryCard";
import { OutlineIntegrationContent } from "@/components/admin/integrations/OutlineIntegrationContent";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { OutlineIcon } from "@/components/icons/brand";
import { Skeleton } from "@/components/ui/skeleton";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useOutlineIntegration } from "@/hooks/use-outline-integration";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/integrations/outline",
)({
	component: OutlineIntegrationPage,
});

const JOBS_PAGE_SIZE = 10;

function OutlineIntegrationPage() {
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";
	const [jobsPage, setJobsPage] = useState(0);
	const outline = useOutlineIntegration(slug);
	const connectionId = outline.connectionId;

	const {
		data: jobsPageData,
		isLoading: isJobsLoading,
		isError: isJobsError,
		error: jobsError,
		refetch: refetchJobs,
	} = useQuery({
		...listConnectionSyncJobsOptions({
			path: { workspaceSlug: slug, connectionId: connectionId ?? -1 },
			query: { page: jobsPage, size: JOBS_PAGE_SIZE },
		}),
		enabled: Boolean(workspaceSlug) && connectionId != null,
		refetchInterval: 60_000,
	});

	return (
		<div className="container mx-auto max-w-5xl space-y-8 py-6">
			<IntegrationPageHeader
				icon={<OutlineIcon className="size-6" />}
				title="Outline"
				description="Mirror Outline collections so their documents reach practice detection as context."
			/>

			{workspaceSlug != null && outline.isLoading && <Skeleton className="h-48 w-full" />}

			{workspaceSlug != null && outline.connectionsError && (
				<QueryErrorAlert
					error={outline.connectionsError}
					title="We couldn't load the Outline connection"
					onRetry={outline.retryConnections}
				/>
			)}

			{workspaceSlug != null && !outline.isLoading && !outline.connectionsError && (
				<>
					{outline.statusError && (
						<QueryErrorAlert
							error={outline.statusError}
							title="We couldn't load Outline sync status"
							onRetry={outline.retryStatus}
						/>
					)}
					{outline.tokenStatusError && (
						<QueryErrorAlert
							error={outline.tokenStatusError}
							title="We couldn't verify the Outline token"
							onRetry={outline.retryTokenStatus}
						/>
					)}
					<OutlineIntegrationContent
						connectCardProps={outline.connectCardProps}
						collectionsProps={outline.collectionsProps}
					/>
				</>
			)}

			{connectionId != null && (
				<JobHistoryCard
					jobs={jobsPageData?.content ?? []}
					isLoading={isJobsLoading}
					isError={isJobsError}
					error={jobsError}
					onRetry={() => refetchJobs()}
					page={jobsPage}
					totalPages={jobsPageData?.totalPages ?? 1}
					onPageChange={setJobsPage}
				/>
			)}
		</div>
	);
}
