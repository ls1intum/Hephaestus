import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { formatDistanceToNow } from "date-fns";
import { AlertCircleIcon, ArrowRightIcon, PlugZapIcon } from "lucide-react";
import { toast } from "sonner";
import {
	getConnectionSyncStatusOptions,
	getConnectionSyncStatusQueryKey,
	getIntegrationCatalogOptions,
	triggerSyncJobMutation,
} from "@/api/@tanstack/react-query.gen";
import type { IntegrationCatalogEntry } from "@/api/types.gen";
import { ActiveJobProgress } from "@/components/admin/integrations/ActiveJobProgress";
import { ConnectionHealthBadge } from "@/components/admin/integrations/ConnectionHealthBadge";
import { SyncNowButton } from "@/components/admin/integrations/SyncNowButton";
import { asDate } from "@/components/admin/integrations/sync-format";
import { WebhookLivenessIndicator } from "@/components/admin/integrations/WebhookLivenessIndicator";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { GithubIcon, GitlabIcon, OutlineIcon, SlackIcon } from "@/components/icons/brand";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/integrations/")(
	{
		component: IntegrationsOverview,
	},
);

const DETAIL_ROUTE: Record<
	IntegrationCatalogEntry["kind"],
	| "/w/$workspaceSlug/admin/integrations/scm"
	| "/w/$workspaceSlug/admin/integrations/slack"
	| "/w/$workspaceSlug/admin/integrations/outline"
> = {
	GITHUB: "/w/$workspaceSlug/admin/integrations/scm",
	GITLAB: "/w/$workspaceSlug/admin/integrations/scm",
	SLACK: "/w/$workspaceSlug/admin/integrations/slack",
	OUTLINE: "/w/$workspaceSlug/admin/integrations/outline",
};

const KIND_ICON: Record<IntegrationCatalogEntry["kind"], React.ReactNode> = {
	GITHUB: <GithubIcon className="size-5" />,
	GITLAB: <GitlabIcon className="size-5" />,
	SLACK: <SlackIcon className="size-5" />,
	OUTLINE: <OutlineIcon className="size-5" />,
};

function IntegrationsOverview() {
	const { workspaceSlug } = useActiveWorkspaceSlug();
	const slug = workspaceSlug ?? "";

	const catalogQuery = useQuery({
		...getIntegrationCatalogOptions({ path: { workspaceSlug: slug } }),
		enabled: Boolean(workspaceSlug),
	});

	return (
		<div className="container mx-auto max-w-5xl space-y-6 py-6">
			<header>
				<h1 className="text-3xl font-bold tracking-tight">Integrations</h1>
				<p className="text-muted-foreground">
					Connection health, sync activity and quick actions for every integration this workspace
					can use. Open one for its full detail — resources, controls and job history.
				</p>
			</header>

			{catalogQuery.isLoading ? (
				<div className="grid gap-4 sm:grid-cols-2">
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
				<div className="grid gap-4 sm:grid-cols-2">
					{(catalogQuery.data ?? []).map((entry) => (
						<IntegrationOverviewCard key={entry.kind} workspaceSlug={slug} entry={entry} />
					))}
				</div>
			)}
		</div>
	);
}

function IntegrationOverviewCard({
	workspaceSlug,
	entry,
}: {
	workspaceSlug: string;
	entry: IntegrationCatalogEntry;
}) {
	const queryClient = useQueryClient();
	const connectionId = entry.connectionId;

	const statusQuery = useQuery({
		...getConnectionSyncStatusOptions({
			path: { workspaceSlug, connectionId: connectionId ?? -1 },
		}),
		enabled: entry.connected && connectionId != null,
		// Adaptive polling: tight while a job is running, relaxed otherwise (SSE hints, when
		// available, invalidate immediately — this is the fallback that keeps the UI correct
		// without it).
		refetchInterval: (query) => (query.state.data?.activeJob ? 5_000 : 60_000),
		refetchOnWindowFocus: true,
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

	const detailTo = DETAIL_ROUTE[entry.kind];
	const status = statusQuery.data;
	const active = entry.connectionState === "ACTIVE";
	const scm = entry.kind === "GITHUB" || entry.kind === "GITLAB";

	return (
		<Card>
			<CardHeader className="flex flex-row items-start justify-between gap-2 space-y-0">
				<div className="flex items-center gap-2">
					{KIND_ICON[entry.kind]}
					<h2 data-slot="card-title" className="text-base leading-snug font-medium">
						{entry.displayName}
					</h2>
				</div>
				{entry.connected && status && <ConnectionHealthBadge health={status.health} />}
			</CardHeader>
			<CardContent className="space-y-3">
				{!entry.connected ? (
					<div className="space-y-3">
						<p className="flex items-center gap-1.5 text-muted-foreground text-sm">
							<PlugZapIcon className="size-4" />
							Not connected
						</p>
						{scm ? (
							<p className="text-muted-foreground text-sm">
								Source control is selected when the workspace is created.
							</p>
						) : (
							<Button
								size="sm"
								nativeButton={false}
								render={<Link to={detailTo} params={{ workspaceSlug }} />}
							>
								Connect
								<ArrowRightIcon className="size-3.5" />
							</Button>
						)}
					</div>
				) : !active ? (
					<p className="text-muted-foreground text-sm">
						Connection is {entry.connectionState?.toLowerCase()}. Sync controls are available only
						while it is active.
					</p>
				) : statusQuery.isLoading ? (
					<Skeleton className="h-16 w-full" />
				) : statusQuery.isError ? (
					<p className="flex items-center gap-1.5 text-destructive text-sm">
						<AlertCircleIcon className="size-4" />
						Couldn't load sync status
					</p>
				) : (
					status && (
						<div className="space-y-2 text-sm">
							<div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-muted-foreground">
								<span>
									{status.lastSuccessfulSyncAt
										? `Last synced ${formatDistanceToNow(asDate(status.lastSuccessfulSyncAt) ?? new Date(), { addSuffix: true })}`
										: "Never synced"}
								</span>
								<WebhookLivenessIndicator lastEventAt={status.lastEventProcessedAt} />
							</div>
							{status.resourceCounts.errored > 0 && (
								<p className="flex items-center gap-1.5 text-destructive">
									<AlertCircleIcon className="size-4" />
									{status.resourceCounts.errored} of {status.resourceCounts.total} resources errored
								</p>
							)}
							<ActiveJobProgress job={status.activeJob} />
						</div>
					)
				)}
			</CardContent>
			{active && (
				<div className="flex items-center justify-between gap-2 px-6 pb-6">
					<SyncNowButton
						onClick={() => {
							if (connectionId == null) return;
							triggerSync.mutate({
								path: { workspaceSlug, connectionId },
								body: { type: "RECONCILIATION" },
							});
						}}
						isTriggering={triggerSync.isPending}
						activeJob={status?.activeJob}
					/>
					<Button
						size="sm"
						variant="ghost"
						nativeButton={false}
						render={<Link to={detailTo} params={{ workspaceSlug }} />}
					>
						View details
						<ArrowRightIcon className="size-3.5" />
					</Button>
				</div>
			)}
		</Card>
	);
}
