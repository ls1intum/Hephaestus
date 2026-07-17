import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { toast } from "sonner";
import {
	getConnectionSyncStatusOptions,
	getConnectionSyncStatusQueryKey,
	getIntegrationCatalogOptions,
	getWorkspaceOptions,
	listConnectionSyncJobsOptions,
	listConnectionSyncJobsQueryKey,
	listSlackChannelCandidatesOptions,
	listSlackChannelConsentEventsQueryKey,
	listSlackChannelsOptions,
	registerSlackChannelMutation,
	triggerSyncJobMutation,
	updateConnectionSyncJobMutation,
	updateSlackChannelConsentMutation,
} from "@/api/@tanstack/react-query.gen";
import type { SlackConsentState } from "@/components/admin/integrations/AdminSlackChannelsSettings";
import { AdminSlackChannelsSettings } from "@/components/admin/integrations/AdminSlackChannelsSettings";
import { AdminSlackNotificationSettings } from "@/components/admin/integrations/AdminSlackNotificationSettings";
import { ConnectionStateNotice } from "@/components/admin/integrations/ConnectionStateNotice";
import { IntegrationPageHeader } from "@/components/admin/integrations/IntegrationPageHeader";
import { JobHistoryCard } from "@/components/admin/integrations/JobHistoryCard";
import { SyncStatusHeader } from "@/components/admin/integrations/SyncStatusHeader";
import { syncPollInterval } from "@/components/admin/integrations/sync-format";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { SlackIcon } from "@/components/icons/brand";
import { Skeleton } from "@/components/ui/skeleton";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useLivePushUnavailable } from "@/hooks/use-sync-liveness";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/integrations/slack",
)({
	component: SlackIntegrationPage,
});

const JOBS_PAGE_SIZE = 10;

function SlackIntegrationPage() {
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

	const catalogQueryOptions = getIntegrationCatalogOptions({ path: { workspaceSlug: slug } });
	const catalogQuery = useQuery({
		...catalogQueryOptions,
		enabled: Boolean(workspaceSlug),
	});
	const catalog = catalogQuery.data;
	const entry = catalog?.find((e) => e.kind === "SLACK");
	const hasConnection = entry?.connected === true;
	const isConnectionActive =
		entry?.connectionState === "ACTIVE" && workspaceData?.hasSlackToken === true;
	const connectionId = hasConnection ? entry.connectionId : undefined;

	const statusQuery = useQuery({
		...getConnectionSyncStatusOptions({
			path: { workspaceSlug: slug, connectionId: connectionId ?? -1 },
		}),
		enabled: Boolean(workspaceSlug) && connectionId != null,
		refetchInterval: (query) =>
			syncPollInterval(query.state.data?.activeJob != null, livePushUnavailable),
	});
	const status = statusQuery.data;
	const hasActiveJob = status?.activeJob != null;

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

	const slackChannelsQueryOptions = listSlackChannelsOptions({ path: { workspaceSlug: slug } });
	const {
		data: slackChannels,
		isLoading: isLoadingSlackChannels,
		isError: isSlackChannelsError,
		refetch: refetchSlackChannels,
	} = useQuery({
		...slackChannelsQueryOptions,
		enabled: Boolean(workspaceSlug && workspaceData?.hasSlackToken),
		refetchInterval: syncPollInterval(hasActiveJob, livePushUnavailable),
	});

	const slackChannelCandidatesQueryOptions = listSlackChannelCandidatesOptions({
		path: { workspaceSlug: slug },
	});
	const {
		data: slackChannelCandidates,
		isLoading: isLoadingSlackChannelCandidates,
		isError: isSlackChannelCandidatesError,
		refetch: refetchSlackChannelCandidates,
	} = useQuery({
		...slackChannelCandidatesQueryOptions,
		enabled: Boolean(workspaceSlug && workspaceData?.hasSlackToken),
	});

	const invalidateSlackChannels = () => {
		queryClient.invalidateQueries({ queryKey: slackChannelsQueryOptions.queryKey });
		queryClient.invalidateQueries({ queryKey: slackChannelCandidatesQueryOptions.queryKey });
	};

	const registerSlackChannel = useMutation({
		...registerSlackChannelMutation(),
		onSuccess: () => {
			toast.success("Channel added");
			invalidateSlackChannels();
		},
		onError: (e) => {
			toast.error("Failed to add channel", { description: problemDetailOf(e) });
		},
	});

	const updateSlackChannelConsent = useMutation({
		...updateSlackChannelConsentMutation(),
		onSuccess: (_data, variables) => {
			if (variables.body?.consentState === "REVOKED") {
				toast.success("Channel removed and its data erased");
			} else {
				toast.success("Channel updated");
			}
			invalidateSlackChannels();
			// This transition *is* a new row in that channel's consent history, and the history sheet
			// is cached across open/close — without this it would reopen still denying the change.
			queryClient.invalidateQueries({
				queryKey: listSlackChannelConsentEventsQueryKey({
					path: { workspaceSlug: slug, slackChannelId: variables.path.slackChannelId },
				}),
			});
		},
		onError: (e, variables) => {
			if (variables.body?.consentState === "REVOKED") {
				toast.error("Failed to remove channel", { description: problemDetailOf(e) });
			} else {
				toast.error("Failed to update channel", { description: problemDetailOf(e) });
			}
		},
	});

	const triggerSync = useMutation({
		...triggerSyncJobMutation(),
		onSuccess: () => {
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
			toast.success("Sync started");
		},
		onError: (e) => {
			toast.error("Failed to start sync", { description: problemDetailOf(e) });
		},
	});

	const cancelJob = useMutation({
		...updateConnectionSyncJobMutation(),
		onSuccess: () => {
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
			// Matches SCM/Outline, which both confirm the cancel — a silent one reads as a no-op.
			toast.success("Cancelling — stopping after current channel…");
		},
		onError: (e) => {
			toast.error("Failed to cancel sync", { description: problemDetailOf(e) });
		},
	});

	const routeLoading = workspaceQuery.isLoading || catalogQuery.isLoading;
	const routeError = workspaceQuery.error ?? catalogQuery.error;

	return (
		<div className="container mx-auto max-w-5xl space-y-8 py-6">
			<IntegrationPageHeader
				icon={<SlackIcon className="size-6" />}
				title="Slack"
				description="Connection, weekly digest, monitored channels and sync activity for this workspace's Slack app."
			/>

			{routeLoading && <Skeleton className="h-48 w-full" />}

			{routeError && (
				<QueryErrorAlert
					error={routeError}
					title="We couldn't load the Slack connection"
					onRetry={() => {
						workspaceQuery.refetch();
						catalogQuery.refetch();
						statusQuery.refetch();
					}}
				/>
			)}

			{!routeLoading && !routeError && statusQuery.isError && (
				<QueryErrorAlert
					error={statusQuery.error}
					title="We couldn't load Slack sync status"
					onRetry={() => statusQuery.refetch()}
				/>
			)}

			{!routeLoading && !routeError && hasConnection && !isConnectionActive && (
				<ConnectionStateNotice connectionState={entry?.connectionState} displayName="Slack" />
			)}

			{!routeLoading && !routeError && status && (
				<SyncStatusHeader
					label="Slack"
					status={status}
					isConnectionActive={isConnectionActive}
					// Slack's only manual trigger is a reconciliation, so a bare `isPending` names it exactly.
					triggeringType={triggerSync.isPending ? "RECONCILIATION" : null}
					isCancelling={cancelJob.isPending}
					onRetry={() => statusQuery.refetch()}
					onSync={() => {
						if (connectionId == null) return;
						triggerSync.mutate({
							path: { workspaceSlug: slug, connectionId },
							body: { type: "RECONCILIATION" },
						});
					}}
					onCancel={() => {
						const jobId = status.activeJob?.id;
						if (connectionId == null || jobId == null) return;
						cancelJob.mutate({
							path: { workspaceSlug: slug, connectionId, jobId },
							body: { cancelRequested: true },
						});
					}}
				/>
			)}

			{workspaceSlug != null && !routeLoading && !routeError && (
				<AdminSlackNotificationSettings
					key={`slack:${workspaceData?.slackConnectionId ?? "none"}:${workspaceData?.leaderboardNotificationChannelId ?? ""}:${workspaceData?.leaderboardNotificationEnabled ?? false}:${workspaceData?.leaderboardScheduleDay ?? ""}:${workspaceData?.leaderboardScheduleTime ?? ""}:${workspaceData?.leaderboardNotificationTeam ?? ""}`}
					workspaceSlug={slug}
					hasSlackConnection={isConnectionActive}
					slackConnectionId={workspaceData?.slackConnectionId ?? undefined}
					channelId={workspaceData?.leaderboardNotificationChannelId ?? undefined}
					teamLabel={workspaceData?.leaderboardNotificationTeam ?? undefined}
					enabled={workspaceData?.leaderboardNotificationEnabled ?? false}
					scheduleDay={workspaceData?.leaderboardScheduleDay ?? undefined}
					scheduleTime={workspaceData?.leaderboardScheduleTime ?? undefined}
					channelCandidates={slackChannelCandidates ?? []}
					onSaved={() => {
						queryClient.invalidateQueries({ queryKey: workspaceQueryOptions.queryKey });
						queryClient.invalidateQueries({ queryKey: catalogQueryOptions.queryKey });
						invalidateSlackChannels();
					}}
				/>
			)}

			{workspaceSlug != null && !routeLoading && !routeError && (
				<AdminSlackChannelsSettings
					workspaceSlug={slug}
					hasSlackConnection={isConnectionActive}
					channels={isConnectionActive ? (slackChannels ?? []) : []}
					channelCandidates={slackChannelCandidates ?? []}
					isLoading={
						isConnectionActive && (isLoadingSlackChannels || isLoadingSlackChannelCandidates)
					}
					isError={isConnectionActive && (isSlackChannelsError || isSlackChannelCandidatesError)}
					onRetry={() => {
						refetchSlackChannels();
						refetchSlackChannelCandidates();
					}}
					onRegisterChannel={async ({
						slackChannelId,
						channelName,
					}: {
						slackChannelId: string;
						channelName?: string;
					}) => {
						await registerSlackChannel.mutateAsync({
							path: { workspaceSlug: slug },
							body: { slackChannelId, channelName },
						});
					}}
					onUpdateConsent={async ({
						slackChannelId,
						consentState,
						reason,
					}: {
						slackChannelId: string;
						consentState: SlackConsentState;
						reason?: string;
					}) => {
						await updateSlackChannelConsent.mutateAsync({
							path: { workspaceSlug: slug, slackChannelId },
							body: { consentState, reason },
						});
					}}
					onRemoveChannel={async ({
						slackChannelId,
						reason,
					}: {
						slackChannelId: string;
						reason?: string;
					}) => {
						await updateSlackChannelConsent.mutateAsync({
							path: { workspaceSlug: slug, slackChannelId },
							body: { consentState: "REVOKED", reason: reason?.trim() ? reason : undefined },
						});
					}}
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
