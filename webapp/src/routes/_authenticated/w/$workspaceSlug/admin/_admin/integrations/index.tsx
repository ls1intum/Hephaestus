import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { toast } from "sonner";
import {
	getConnectionSyncStatusOptions,
	getConnectionSyncStatusQueryKey,
	getIntegrationCatalogOptions,
	triggerSyncJobMutation,
} from "@/api/@tanstack/react-query.gen";
import { IntegrationOverviewCard } from "@/components/admin/integrations/IntegrationOverviewCard";
import { IntegrationPageHeader } from "@/components/admin/integrations/IntegrationPageHeader";
import { syncPollInterval } from "@/components/admin/integrations/sync-format";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Skeleton } from "@/components/ui/skeleton";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useLivePushUnavailable } from "@/hooks/use-sync-liveness";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/integrations/")(
	{
		component: IntegrationsOverview,
	},
);

function IntegrationsOverview() {
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";

	const catalogQuery = useQuery({
		...getIntegrationCatalogOptions({ path: { workspaceSlug: slug } }),
		enabled: Boolean(workspaceSlug),
	});

	return (
		<div className="container mx-auto max-w-5xl space-y-6 py-6">
			<IntegrationPageHeader
				title="Integrations"
				description="Connection health, sync activity and quick actions for every integration this workspace can use. Open one for its full detail — resources, controls and job history."
			/>

			{catalogQuery.isLoading ? (
				<div className="grid items-stretch gap-4 sm:grid-cols-2">
					<Skeleton className="h-40 w-full" />
					<Skeleton className="h-40 w-full" />
				</div>
			) : catalogQuery.isError ? (
				<QueryErrorAlert
					error={catalogQuery.error}
					title="We couldn't load the integration catalog"
					onRetry={() => catalogQuery.refetch()}
				/>
			) : (
				<div className="grid items-stretch gap-4 sm:grid-cols-2">
					{(catalogQuery.data ?? []).map((entry) => (
						<IntegrationOverviewCardContainer key={entry.kind} workspaceSlug={slug} entry={entry} />
					))}
				</div>
			)}
		</div>
	);
}

function IntegrationOverviewCardContainer({
	workspaceSlug,
	entry,
}: {
	workspaceSlug: string;
	entry: Parameters<typeof IntegrationOverviewCard>[0]["entry"];
}) {
	const queryClient = useQueryClient();
	const connectionId = entry.connectionId;
	const livePushUnavailable = useLivePushUnavailable();

	const statusQuery = useQuery({
		...getConnectionSyncStatusOptions({
			path: { workspaceSlug, connectionId: connectionId ?? -1 },
		}),
		enabled: entry.connected && connectionId != null,
		refetchInterval: (query) =>
			syncPollInterval(query.state.data?.activeJob != null, livePushUnavailable),
	});

	const triggerSync = useMutation({
		...triggerSyncJobMutation(),
		onSuccess: () => {
			if (connectionId == null) return;
			queryClient.invalidateQueries({
				queryKey: getConnectionSyncStatusQueryKey({ path: { workspaceSlug, connectionId } }),
			});
			toast.success(`${entry.displayName} sync started`);
		},
		onError: (e) => {
			toast.error(`Failed to start sync for ${entry.displayName}`, {
				description: problemDetailOf(e),
			});
		},
	});

	return (
		<IntegrationOverviewCard
			workspaceSlug={workspaceSlug}
			entry={entry}
			status={statusQuery.data}
			isStatusLoading={statusQuery.isLoading}
			isStatusError={statusQuery.isError}
			// The card's alert can only name the failure and judge whether Retry helps if it is handed the
			// error and a refetch — without these it rendered a title over a blank card and no way out.
			statusError={statusQuery.error}
			onRetryStatus={() => statusQuery.refetch()}
			isTriggering={triggerSync.isPending}
			onSync={() => {
				if (connectionId == null) return;
				triggerSync.mutate({
					path: { workspaceSlug, connectionId },
					body: { type: "RECONCILIATION" },
				});
			}}
		/>
	);
}
