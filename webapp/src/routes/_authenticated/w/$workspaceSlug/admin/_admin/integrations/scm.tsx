import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { formatDistanceToNow } from "date-fns";
import { ExternalLinkIcon, WebhookIcon, ZapOffIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	addRepositoryToMonitorMutation,
	cancelConnectionSyncJobMutation,
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
	updateConnectionStatusMutation,
} from "@/api/@tanstack/react-query.gen";
import { ActiveJobProgress } from "@/components/admin/integrations/ActiveJobProgress";
import { AdminRepositoriesSettings } from "@/components/admin/integrations/AdminRepositoriesSettings";
import { ConnectionHealthBadge } from "@/components/admin/integrations/ConnectionHealthBadge";
import { IntegrationPageHeader } from "@/components/admin/integrations/IntegrationPageHeader";
import { RateLimitGauge } from "@/components/admin/integrations/RateLimitGauge";
import { SyncJobsTable } from "@/components/admin/integrations/SyncJobsTable";
import { SyncNowButton } from "@/components/admin/integrations/SyncNowButton";
import { SyncResourcesTable } from "@/components/admin/integrations/SyncResourcesTable";
import { WebhookLivenessIndicator } from "@/components/admin/integrations/WebhookLivenessIndicator";
import { GithubIcon, GitlabIcon } from "@/components/icons/brand";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
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
	const [disconnectOpen, setDisconnectOpen] = useState(false);
	const [jobsPage, setJobsPage] = useState(0);

	const workspaceQueryOptions = getWorkspaceOptions({ path: { workspaceSlug: slug } });
	const { data: workspaceData } = useQuery({
		...workspaceQueryOptions,
		enabled: Boolean(workspaceSlug),
	});

	const kind = workspaceData?.kind === "GITLAB" ? "GITLAB" : "GITHUB";
	const label = kind === "GITLAB" ? "GitLab" : "GitHub";
	const isAppInstallationWorkspace = kind === "GITHUB" && workspaceData?.installationId != null;

	const catalogQueryOptions = getIntegrationCatalogOptions({ path: { workspaceSlug: slug } });
	const catalogQuery = useQuery({
		...catalogQueryOptions,
		enabled: Boolean(workspaceSlug),
	});
	const entry = catalogQuery.data?.find((e) => e.kind === kind);
	const connectionId = entry?.connectionId;

	const statusQueryOptions = getConnectionSyncStatusOptions({
		path: { workspaceSlug: slug, connectionId: connectionId ?? -1 },
	});
	const { data: status, isLoading: isStatusLoading } = useQuery({
		...statusQueryOptions,
		enabled: Boolean(workspaceSlug) && connectionId != null,
		refetchInterval: (query) => (query.state.data?.activeJob ? 5_000 : 60_000),
		refetchOnWindowFocus: true,
	});

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
	});

	// Repositories query — unchanged from the previous settings-page section, just moved here.
	const repositoriesQueryOptions = getRepositoriesToMonitorOptions({
		path: { workspaceSlug: slug },
	});
	const {
		data: repositories,
		isLoading: isLoadingRepositories,
		error: repositoriesError,
	} = useQuery({
		...repositoriesQueryOptions,
		enabled: Boolean(workspaceSlug),
	});

	const addRepository = useMutation({
		...addRepositoryToMonitorMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: repositoriesQueryOptions.queryKey });
		},
	});
	const removeRepository = useMutation({
		...removeRepositoryToMonitorMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: repositoriesQueryOptions.queryKey });
		},
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
		...cancelConnectionSyncJobMutation(),
		onSuccess: () => {
			invalidateSyncState();
			toast.success("Cancelling — stopping after current step…");
		},
		onError: (e) => {
			toast.error("Failed to cancel sync", { description: problemDetailOf(e) });
		},
	});

	const disconnect = useMutation({
		...updateConnectionStatusMutation(),
		onSuccess: () => {
			setDisconnectOpen(false);
			toast.success(`${label} disconnected`);
			queryClient.invalidateQueries({ queryKey: catalogQueryOptions.queryKey });
			queryClient.invalidateQueries({ queryKey: workspaceQueryOptions.queryKey });
		},
		onError: (e) => {
			toast.error(`Failed to disconnect ${label}`, { description: problemDetailOf(e) });
		},
	});

	const activeJob = status?.activeJob;

	return (
		<div className="container mx-auto max-w-5xl space-y-8 py-6">
			<IntegrationPageHeader
				icon={
					kind === "GITLAB" ? <GitlabIcon className="size-6" /> : <GithubIcon className="size-6" />
				}
				title={label}
				description={`Connection health, repositories and sync activity for this workspace's ${label} connection.`}
				actions={status && <ConnectionHealthBadge health={status.health} />}
			/>

			<Card>
				<CardHeader>
					<h2 data-slot="card-title" className="text-base leading-snug font-medium">
						Connection
					</h2>
				</CardHeader>
				<CardContent className="space-y-4">
					{isStatusLoading ? (
						<Skeleton className="h-20 w-full" />
					) : !status ? (
						<p className="text-muted-foreground text-sm">
							No {label} connection found for this workspace.
						</p>
					) : (
						<>
							<div className="grid gap-4 sm:grid-cols-2">
								<div className="space-y-1">
									<p className="text-muted-foreground text-xs uppercase tracking-wide">
										Last successful sync
									</p>
									<p className="text-sm">
										{status.lastSuccessfulSyncAt
											? formatDistanceToNow(new Date(status.lastSuccessfulSyncAt), {
													addSuffix: true,
												})
											: "Never synced"}
									</p>
								</div>
								<div className="space-y-1">
									<p className="text-muted-foreground text-xs uppercase tracking-wide">
										Webhook liveness
									</p>
									<div className="flex items-center gap-2 text-sm">
										{status.webhookRegistered === false ? (
											<span className="flex items-center gap-1.5 text-muted-foreground">
												<ZapOffIcon className="size-4" />
												Not registered
											</span>
										) : (
											<span className="flex items-center gap-1.5">
												<WebhookIcon className="size-4" />
												<WebhookLivenessIndicator lastEventAt={status.lastEventProcessedAt} />
											</span>
										)}
									</div>
								</div>
								<div className="space-y-1">
									<p className="text-muted-foreground text-xs uppercase tracking-wide">
										Rate limit
									</p>
									<RateLimitGauge rateLimit={status.rateLimit} />
								</div>
								{status.backfill && (
									<div className="space-y-1">
										<p className="text-muted-foreground text-xs uppercase tracking-wide">
											Backfill
										</p>
										<p className="text-sm">
											{status.backfill.state}
											{status.backfill.percent != null ? ` — ${status.backfill.percent}%` : ""}
										</p>
									</div>
								)}
							</div>

							<ActiveJobProgress job={activeJob} />

							<div className="flex flex-wrap items-center gap-2 pt-2">
								<SyncNowButton
									onClick={() => {
										if (connectionId == null) return;
										triggerSync.mutate({
											path: { workspaceSlug: slug, connectionId },
											body: { type: "RECONCILIATION" },
										});
									}}
									isTriggering={triggerSync.isPending}
									activeJob={activeJob}
								/>
								{kind === "GITHUB" && (
									<SyncNowButton
										label="Backfill"
										onClick={() => {
											if (connectionId == null) return;
											triggerSync.mutate({
												path: { workspaceSlug: slug, connectionId },
												body: { type: "BACKFILL" },
											});
										}}
										isTriggering={triggerSync.isPending}
										activeJob={activeJob}
									/>
								)}
								{activeJob && (
									<Button
										variant="outline"
										size="sm"
										disabled={cancelJob.isPending || activeJob.cancelRequested}
										onClick={() => {
											if (connectionId == null) return;
											cancelJob.mutate({
												path: { workspaceSlug: slug, connectionId, jobId: activeJob.id },
											});
										}}
									>
										{activeJob.cancelRequested ? "Stopping after current step…" : "Cancel"}
									</Button>
								)}

								{isAppInstallationWorkspace ? (
									<Button
										variant="outline"
										size="sm"
										className="ml-auto"
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
								) : (
									connectionId != null && (
										<Button
											variant="destructive-outline"
											size="sm"
											className="ml-auto"
											onClick={() => setDisconnectOpen(true)}
											disabled={disconnect.isPending}
										>
											{disconnect.isPending ? "Disconnecting…" : `Disconnect ${label}…`}
										</Button>
									)
								)}
							</div>
						</>
					)}
				</CardContent>
			</Card>

			<AdminRepositoriesSettings
				repositories={(repositories ?? []).map((repo) => ({ nameWithOwner: repo }))}
				isLoading={isLoadingRepositories}
				error={repositoriesError as Error | null}
				addRepositoryError={addRepository.error as Error | null}
				isAddingRepository={addRepository.isPending}
				isRemovingRepository={removeRepository.isPending}
				isReadOnly={isAppInstallationWorkspace}
				onAddRepository={(nameWithOwner) => {
					addRepository.mutate({ path: { workspaceSlug: slug }, query: { nameWithOwner } });
				}}
				onRemoveRepository={(nameWithOwner) => {
					removeRepository.mutate({ path: { workspaceSlug: slug }, query: { nameWithOwner } });
				}}
			/>

			<Card>
				<CardHeader>
					<h2 data-slot="card-title" className="text-base leading-snug font-medium">
						Repository sync state
					</h2>
				</CardHeader>
				<CardContent>
					<SyncResourcesTable
						resources={resources ?? []}
						isLoading={isResourcesLoading}
						isError={isResourcesError}
						error={resourcesError}
						onRetry={() => refetchResources()}
						resourceNoun="repository"
					/>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<h2 data-slot="card-title" className="text-base leading-snug font-medium">
						Job history
					</h2>
				</CardHeader>
				<CardContent>
					<SyncJobsTable
						jobs={jobsPageData?.content ?? []}
						isLoading={isJobsLoading}
						isError={isJobsError}
						error={jobsError}
						onRetry={() => refetchJobs()}
						page={jobsPage}
						totalPages={jobsPageData?.totalPages ?? 1}
						onPageChange={setJobsPage}
					/>
				</CardContent>
			</Card>

			<AlertDialog open={disconnectOpen} onOpenChange={setDisconnectOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Disconnect {label}?</AlertDialogTitle>
						<AlertDialogDescription>
							Hephaestus stops syncing repositories and receiving webhook events from this {label}{" "}
							connection. You can reconnect later.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={disconnect.isPending}>Cancel</AlertDialogCancel>
						<AlertDialogAction
							variant="destructive"
							disabled={disconnect.isPending || connectionId == null}
							onClick={() => {
								if (connectionId == null) return;
								disconnect.mutate({
									path: { workspaceSlug: slug, id: connectionId },
									body: { state: "UNINSTALLED" },
								});
							}}
						>
							{disconnect.isPending ? "Disconnecting…" : "Disconnect"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
}
