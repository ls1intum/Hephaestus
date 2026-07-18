import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { listConnectionSyncJobsOptions } from "@/api/@tanstack/react-query.gen";
import { ConnectionStateNotice } from "@/components/admin/integrations/ConnectionStateNotice";
import { IntegrationCardHeading } from "@/components/admin/integrations/IntegrationCardHeading";
import { IntegrationPageHeader } from "@/components/admin/integrations/IntegrationPageHeader";
import { JobHistoryCard } from "@/components/admin/integrations/JobHistoryCard";
import { OutlineCollectionsSection } from "@/components/admin/integrations/outline/OutlineCollectionsSection";
import { OutlineConnectCard } from "@/components/admin/integrations/outline/OutlineConnectCard";
import { SyncResourcesTable } from "@/components/admin/integrations/SyncResourcesTable";
import { SyncStatusHeader } from "@/components/admin/integrations/SyncStatusHeader";
import { syncPollInterval } from "@/components/admin/integrations/sync-format";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { OutlineIcon } from "@/components/icons/brand";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useOutlineIntegration } from "@/hooks/use-outline-integration";
import { useLivePushUnavailable } from "@/hooks/use-sync-liveness";

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
	const livePushUnavailable = useLivePushUnavailable();
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
		refetchInterval: syncPollInterval(outline.hasActiveJob, livePushUnavailable),
		// Every page is a new query key, so without this a page turn re-enters `pending` and collapses
		// the table into skeletons. Keep the previous page on screen while the next one loads.
		placeholderData: (previousData) => previousData,
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
					{outline.hasConnection && outline.statusError && (
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

					{/* A suspended/uninstalled connection still paints a normal-looking header; this shared
					    notice is what says sync stopped and why. */}
					{outline.hasConnection && !outline.isConnectionActive && (
						<ConnectionStateNotice
							connectionState={outline.connectionState}
							displayName="Outline"
						/>
					)}

					{/* The connection plane: health, freshness, diagnostics and the Sync/Cancel controls.
					    Gated on the unified status being present. */}
					{outline.status && (
						<SyncStatusHeader label="Outline" {...outline.syncStatusHeaderProps} />
					)}

					{/* The per-collection ledger: how many documents each mirrored collection holds and how
					    fresh it is, tinted against the connection's own cadence. The mirrored-collections
					    card below stays the place to pause, resume or remove — this one only reports. */}
					{outline.hasConnection && (
						<Card>
							<CardHeader>
								<IntegrationCardHeading>Collection sync state</IntegrationCardHeading>
							</CardHeader>
							<CardContent>
								<SyncResourcesTable {...outline.syncResourcesProps} />
							</CardContent>
						</Card>
					)}

					{/* Outline's token-paste lifecycle: the connect form when disconnected, the linked instance
					    + token health + a guarded disconnect when connected. */}
					<OutlineConnectCard {...outline.connectCardProps} />

					{outline.collectionsProps && <OutlineCollectionsSection {...outline.collectionsProps} />}
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
