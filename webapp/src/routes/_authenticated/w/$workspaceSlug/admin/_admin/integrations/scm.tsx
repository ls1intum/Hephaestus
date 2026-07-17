import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { ExternalLinkIcon, WebhookIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	addRepositoryToMonitorMutation,
	getConnectionSyncStatusOptions,
	getConnectionSyncStatusQueryKey,
	getIntegrationCatalogOptions,
	getRepositoriesToMonitorOptions,
	getWorkspaceOptions,
	listConnectionSyncJobsOptions,
	listConnectionSyncJobsQueryKey,
	listConnectionSyncResourcesOptions,
	listConnectionSyncResourcesQueryKey,
	removeRepositoryToMonitorMutation,
	triggerSyncJobMutation,
	updateConnectionSyncJobMutation,
} from "@/api/@tanstack/react-query.gen";
import { AdminRepositoriesSettings } from "@/components/admin/integrations/AdminRepositoriesSettings";
import { ConnectionStateNotice } from "@/components/admin/integrations/ConnectionStateNotice";
import { IntegrationCardHeading } from "@/components/admin/integrations/IntegrationCardHeading";
import { IntegrationPageHeader } from "@/components/admin/integrations/IntegrationPageHeader";
import { JobHistoryCard } from "@/components/admin/integrations/JobHistoryCard";
import {
	SCM_CLASS_KEYS,
	SyncResourcesTable,
} from "@/components/admin/integrations/SyncResourcesTable";
import { SyncStatusHeader } from "@/components/admin/integrations/SyncStatusHeader";
import { syncPollInterval } from "@/components/admin/integrations/sync-format";
import { GithubIcon, GitlabIcon } from "@/components/icons/brand";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useLivePushUnavailable } from "@/hooks/use-sync-liveness";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/integrations/scm",
)({
	component: ScmIntegrationPage,
});

const JOBS_PAGE_SIZE = 10;

function ScmIntegrationPage() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";
	const [jobsPage, setJobsPage] = useState(0);
	const livePushUnavailable = useLivePushUnavailable();

	const workspaceQueryOptions = getWorkspaceOptions({ path: { workspaceSlug: slug } });
	const workspaceQuery = useQuery({
		...workspaceQueryOptions,
		enabled: Boolean(workspaceSlug),
	});
	const workspaceData = workspaceQuery.data;

	const kind = workspaceData ? (workspaceData.kind === "GITLAB" ? "GITLAB" : "GITHUB") : undefined;
	const label = kind === "GITLAB" ? "GitLab" : kind === "GITHUB" ? "GitHub" : "Source control";
	const isAppInstallationWorkspace = kind === "GITHUB" && workspaceData?.installationId != null;

	const catalogQueryOptions = getIntegrationCatalogOptions({ path: { workspaceSlug: slug } });
	const catalogQuery = useQuery({
		...catalogQueryOptions,
		enabled: Boolean(workspaceSlug),
	});
	const entry = kind ? catalogQuery.data?.find((e) => e.kind === kind) : undefined;
	const hasConnection = entry?.connected === true;
	const isConnectionActive = entry?.connectionState === "ACTIVE";
	const connectionId = hasConnection ? entry.connectionId : undefined;

	const statusQueryOptions = getConnectionSyncStatusOptions({
		path: { workspaceSlug: slug, connectionId: connectionId ?? -1 },
	});
	const statusQuery = useQuery({
		...statusQueryOptions,
		enabled: Boolean(workspaceSlug) && connectionId != null,
		refetchInterval: (query) =>
			syncPollInterval(query.state.data?.activeJob != null, livePushUnavailable),
	});
	const status = statusQuery.data;
	const hasActiveJob = status?.activeJob != null;

	const resourcesQueryOptions = listConnectionSyncResourcesOptions({
		path: { workspaceSlug: slug, connectionId: connectionId ?? -1 },
	});
	const {
		data: resources,
		isLoading: isResourcesLoading,
		isError: isResourcesError,
		error: resourcesError,
		refetch: refetchResources,
	} = useQuery({
		...resourcesQueryOptions,
		enabled: Boolean(workspaceSlug) && connectionId != null,
		refetchInterval: syncPollInterval(hasActiveJob, livePushUnavailable),
	});

	const jobsQueryOptions = listConnectionSyncJobsOptions({
		path: { workspaceSlug: slug, connectionId: connectionId ?? -1 },
		query: { page: jobsPage, size: JOBS_PAGE_SIZE },
	});
	const {
		data: jobsPageData,
		isLoading: isJobsLoading,
		isError: isJobsError,
		error: jobsError,
		refetch: refetchJobs,
	} = useQuery({
		...jobsQueryOptions,
		enabled: Boolean(workspaceSlug) && connectionId != null,
		refetchInterval: syncPollInterval(hasActiveJob, livePushUnavailable),
		// Every page is a new query key, so without this a page turn re-enters `pending` and collapses
		// the table into skeletons. Keep the previous page on screen while the next one loads.
		placeholderData: (previousData) => previousData,
	});

	const repositoriesQueryOptions = getRepositoriesToMonitorOptions({
		path: { workspaceSlug: slug },
	});
	const {
		data: repositories,
		isLoading: isLoadingRepositories,
		error: repositoriesError,
		refetch: refetchRepositories,
	} = useQuery({
		...repositoriesQueryOptions,
		enabled: Boolean(workspaceSlug),
	});

	const invalidateSyncState = () => {
		if (connectionId == null) return;
		queryClient.invalidateQueries({
			queryKey: getConnectionSyncStatusQueryKey({
				path: { workspaceSlug: slug, connectionId },
			}),
		});
		queryClient.invalidateQueries({
			queryKey: listConnectionSyncJobsQueryKey({
				path: { workspaceSlug: slug, connectionId },
			}),
		});
		queryClient.invalidateQueries({
			queryKey: listConnectionSyncResourcesQueryKey({
				path: { workspaceSlug: slug, connectionId },
			}),
		});
	};

	// Monitoring a repository does not just change the list — it changes what this connection syncs,
	// and normally enqueues a lifecycle job. Refresh the sync state here rather than hoping the SSE
	// hint beats the toast.
	const onRepositorySetChanged = () => {
		queryClient.invalidateQueries({ queryKey: repositoriesQueryOptions.queryKey });
		invalidateSyncState();
	};

	const addRepository = useMutation({
		...addRepositoryToMonitorMutation(),
		onSuccess: onRepositorySetChanged,
		// The inline FieldError says "invalid"; the toast says WHY (403 not an owner, 409 already
		// monitored). Both siblings below toast on failure — a mutation that fails silently is the
		// outlier, not the norm.
		onError: (e) => {
			toast.error("Failed to add repository", { description: problemDetailOf(e) });
		},
	});
	const removeRepository = useMutation({
		...removeRepositoryToMonitorMutation(),
		onSuccess: onRepositorySetChanged,
		onError: (e) => {
			toast.error("Failed to stop monitoring repository", { description: problemDetailOf(e) });
		},
	});

	const triggerSync = useMutation({
		...triggerSyncJobMutation(),
		onSuccess: (job) => {
			invalidateSyncState();
			toast.success(job.type === "BACKFILL" ? "Backfill started" : "Sync started");
		},
		onError: (e) => {
			toast.error("Failed to start sync", { description: problemDetailOf(e) });
		},
	});

	const cancelJob = useMutation({
		...updateConnectionSyncJobMutation(),
		onSuccess: () => {
			invalidateSyncState();
			toast.success("Cancelling — stopping after current step…");
		},
		onError: (e) => {
			toast.error("Failed to cancel sync", { description: problemDetailOf(e) });
		},
	});

	const activeJob = status?.activeJob;

	// Sync and Backfill share one mutation, so `isPending` alone cannot say which button the admin
	// pressed — spinning both is a lie about what is happening. The in-flight variables already carry
	// the answer, so no extra state is needed to tell them apart.
	const pendingTriggerType = triggerSync.isPending ? triggerSync.variables?.body?.type : undefined;
	const triggeringType =
		pendingTriggerType === "RECONCILIATION" || pendingTriggerType === "BACKFILL"
			? pendingTriggerType
			: null;

	return (
		<div className="container mx-auto max-w-5xl space-y-8 py-6">
			<IntegrationPageHeader
				icon={
					kind === "GITLAB" ? (
						<GitlabIcon className="size-6" />
					) : kind === "GITHUB" ? (
						<GithubIcon className="size-6" />
					) : (
						<WebhookIcon className="size-6" />
					)
				}
				title={label}
				description={`Connection health, repositories and sync activity for this workspace's ${label} connection.`}
			/>

			{/* A suspended/uninstalled connection paints an otherwise normal-looking header; the shared
			    notice is what says sync stopped and why — the same explanation Slack and Outline show. */}
			{hasConnection && !isConnectionActive && (
				<ConnectionStateNotice connectionState={entry?.connectionState} displayName={label} />
			)}

			<SyncStatusHeader
				label={label}
				status={status}
				isLoading={workspaceQuery.isLoading || catalogQuery.isLoading || statusQuery.isLoading}
				error={workspaceQuery.error ?? catalogQuery.error ?? statusQuery.error}
				isConnectionActive={isConnectionActive}
				triggeringType={triggeringType}
				actions={
					isAppInstallationWorkspace && (
						<Button
							variant="outline"
							size="sm"
							nativeButton={false}
							render={
								<a
									href="https://github.com/settings/installations"
									target="_blank"
									rel="noreferrer"
								/>
							}
						>
							Manage installation on GitHub
							<ExternalLinkIcon className="size-3.5" />
						</Button>
					)
				}
				isCancelling={cancelJob.isPending}
				onRetry={() => {
					workspaceQuery.refetch();
					catalogQuery.refetch();
					statusQuery.refetch();
				}}
				onSync={() => {
					if (connectionId == null) return;
					triggerSync.mutate({
						path: { workspaceSlug: slug, connectionId },
						body: { type: "RECONCILIATION" },
					});
				}}
				onBackfill={() => {
					if (connectionId == null) return;
					triggerSync.mutate({
						path: { workspaceSlug: slug, connectionId },
						body: { type: "BACKFILL" },
					});
				}}
				onCancel={() => {
					if (connectionId == null || activeJob == null) return;
					cancelJob.mutate({
						path: { workspaceSlug: slug, connectionId, jobId: activeJob.id },
						body: { cancelRequested: true },
					});
				}}
			/>

			{/* The canonical repository list: every monitored repo with its per-class counts and freshness.
			    The add/remove management card sits below it, so this stays the one full-height scroller. */}
			{hasConnection && (
				<Card>
					<CardHeader>
						<IntegrationCardHeading>Repository sync state</IntegrationCardHeading>
					</CardHeader>
					<CardContent>
						<SyncResourcesTable
							resources={resources ?? []}
							isLoading={isResourcesLoading}
							isError={isResourcesError}
							error={resourcesError}
							onRetry={() => refetchResources()}
							resourceNoun="repository"
							resourceNounPlural="repositories"
							// Without the cadence the ledger prints every reading and judges none of them —
							// the server sends it precisely so the client doesn't have to guess a schedule.
							syncIntervalSeconds={status?.syncIntervalSeconds}
							expectedClassKeys={SCM_CLASS_KEYS}
						/>
					</CardContent>
				</Card>
			)}

			{isConnectionActive && !isAppInstallationWorkspace && (
				<AdminRepositoriesSettings
					repositories={(repositories ?? []).map((repo) => ({ nameWithOwner: repo }))}
					providerLabel={label}
					isLoading={isLoadingRepositories}
					error={repositoriesError as Error | null}
					addRepositoryError={addRepository.error as Error | null}
					isAddingRepository={addRepository.isPending}
					isRemovingRepository={removeRepository.isPending}
					onAddRepository={(nameWithOwner) => {
						addRepository.mutate({ path: { workspaceSlug: slug }, query: { nameWithOwner } });
					}}
					onRemoveRepository={(nameWithOwner) => {
						removeRepository.mutate({ path: { workspaceSlug: slug }, query: { nameWithOwner } });
					}}
					onRetry={() => refetchRepositories()}
				/>
			)}

			{hasConnection && (
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
