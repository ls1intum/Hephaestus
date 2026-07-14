import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { formatDistanceToNow } from "date-fns";
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
	listSlackChannelsOptions,
	registerSlackChannelMutation,
	triggerSyncJobMutation,
	updateSlackChannelConsentMutation,
} from "@/api/@tanstack/react-query.gen";
import type { SlackConsentState } from "@/components/admin/integrations/AdminSlackChannelsSettings";
import { AdminSlackChannelsSettings } from "@/components/admin/integrations/AdminSlackChannelsSettings";
import { AdminSlackNotificationSettings } from "@/components/admin/integrations/AdminSlackNotificationSettings";
import { ConnectionHealthBadge } from "@/components/admin/integrations/ConnectionHealthBadge";
import { IntegrationPageHeader } from "@/components/admin/integrations/IntegrationPageHeader";
import { SyncJobsTable } from "@/components/admin/integrations/SyncJobsTable";
import { SyncNowButton } from "@/components/admin/integrations/SyncNowButton";
import { WebhookLivenessIndicator } from "@/components/admin/integrations/WebhookLivenessIndicator";
import { SlackIcon } from "@/components/icons/brand";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
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

	const workspaceQueryOptions = getWorkspaceOptions({ path: { workspaceSlug: slug } });
	const { data: workspaceData } = useQuery({
		...workspaceQueryOptions,
		enabled: Boolean(workspaceSlug),
	});

	const catalogQueryOptions = getIntegrationCatalogOptions({ path: { workspaceSlug: slug } });
	const { data: catalog } = useQuery({
		...catalogQueryOptions,
		enabled: Boolean(workspaceSlug),
	});
	const entry = catalog?.find((e) => e.kind === "SLACK");
	const connectionId = entry?.connectionId;

	const { data: status } = useQuery({
		...getConnectionSyncStatusOptions({
			path: { workspaceSlug: slug, connectionId: connectionId ?? -1 },
		}),
		enabled: Boolean(workspaceSlug) && connectionId != null,
		refetchInterval: (query) => (query.state.data?.activeJob ? 5_000 : 60_000),
		refetchOnWindowFocus: true,
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

	// Slack monitored channels — unchanged from the previous settings-page section, just moved here.
	const slackChannelsQueryOptions = listSlackChannelsOptions({ path: { workspaceSlug: slug } });
	const {
		data: slackChannels,
		isLoading: isLoadingSlackChannels,
		isError: isSlackChannelsError,
		refetch: refetchSlackChannels,
	} = useQuery({
		...slackChannelsQueryOptions,
		enabled: Boolean(workspaceSlug && workspaceData?.hasSlackToken),
	});

	const slackChannelCandidatesQueryOptions = listSlackChannelCandidatesOptions({
		path: { workspaceSlug: slug },
	});
	const { data: slackChannelCandidates, isLoading: isLoadingSlackChannelCandidates } = useQuery({
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

	return (
		<div className="container mx-auto max-w-5xl space-y-8 py-6">
			<IntegrationPageHeader
				icon={<SlackIcon className="size-6" />}
				title="Slack"
				description="Connection, weekly digest, monitored channels and sync activity for this workspace's Slack app."
				actions={status && <ConnectionHealthBadge health={status.health} />}
			/>

			{status && (
				<Card>
					<CardHeader>
						<h2 data-slot="card-title" className="text-base leading-snug font-medium">
							Sync status
						</h2>
					</CardHeader>
					<CardContent className="space-y-4">
						<div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
							<span>
								{status.lastSuccessfulSyncAt
									? `Last synced ${formatDistanceToNow(new Date(status.lastSuccessfulSyncAt), { addSuffix: true })}`
									: "Never synced"}
							</span>
							<WebhookLivenessIndicator lastEventAt={status.lastEventProcessedAt} />
						</div>
						<SyncNowButton
							onClick={() => {
								if (connectionId == null) return;
								triggerSync.mutate({
									path: { workspaceSlug: slug, connectionId },
									body: { type: "RECONCILIATION" },
								});
							}}
							isTriggering={triggerSync.isPending}
							activeJob={status.activeJob}
						/>
					</CardContent>
				</Card>
			)}

			{workspaceSlug != null && (
				<AdminSlackNotificationSettings
					key={`slack:${workspaceData?.slackConnectionId ?? "none"}:${workspaceData?.leaderboardNotificationChannelId ?? ""}:${workspaceData?.leaderboardNotificationEnabled ?? false}:${workspaceData?.leaderboardScheduleDay ?? ""}:${workspaceData?.leaderboardScheduleTime ?? ""}:${workspaceData?.leaderboardNotificationTeam ?? ""}`}
					workspaceSlug={slug}
					hasSlackConnection={workspaceData?.hasSlackToken ?? false}
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

			{workspaceSlug != null && (
				<AdminSlackChannelsSettings
					workspaceSlug={slug}
					hasSlackConnection={workspaceData?.hasSlackToken ?? false}
					channels={workspaceData?.hasSlackToken ? (slackChannels ?? []) : []}
					channelCandidates={slackChannelCandidates ?? []}
					isLoading={
						Boolean(workspaceData?.hasSlackToken) &&
						(isLoadingSlackChannels || isLoadingSlackChannelCandidates)
					}
					isError={Boolean(workspaceData?.hasSlackToken) && isSlackChannelsError}
					onRetry={() => refetchSlackChannels()}
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
		</div>
	);
}
