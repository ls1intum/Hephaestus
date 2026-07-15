import { Link } from "@tanstack/react-router";
import { formatDistanceToNow } from "date-fns";
import { AlertCircleIcon, ArrowRightIcon, PlugZapIcon } from "lucide-react";
import type { ConnectionSyncStatus, IntegrationCatalogEntry } from "@/api/types.gen";
import { GithubIcon, GitlabIcon, OutlineIcon, SlackIcon } from "@/components/icons/brand";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ActiveJobProgress } from "./ActiveJobProgress";
import { ConnectionHealthBadge } from "./ConnectionHealthBadge";
import { LastProcessedEvent } from "./LastProcessedEvent";
import { SyncNowButton } from "./SyncNowButton";
import { asDate } from "./sync-format";

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

export interface IntegrationOverviewCardProps {
	workspaceSlug: string;
	entry: IntegrationCatalogEntry;
	status?: ConnectionSyncStatus;
	isStatusLoading?: boolean;
	isStatusError?: boolean;
	isTriggering?: boolean;
	onSync: () => void;
}

export function IntegrationOverviewCard({
	workspaceSlug,
	entry,
	status,
	isStatusLoading = false,
	isStatusError = false,
	isTriggering = false,
	onSync,
}: IntegrationOverviewCardProps) {
	const detailTo = DETAIL_ROUTE[entry.kind];
	const isConnectionActive = entry.connectionState === "ACTIVE";
	const isScm = entry.kind === "GITHUB" || entry.kind === "GITLAB";

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
						{isScm ? (
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
				) : !isConnectionActive ? (
					<p className="text-muted-foreground text-sm">
						Connection is {entry.connectionState?.toLowerCase()}. Sync controls are available only
						while it is active.
					</p>
				) : isStatusLoading ? (
					<Skeleton className="h-16 w-full" />
				) : isStatusError ? (
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
								<LastProcessedEvent lastEventAt={status.lastEventProcessedAt} />
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
			{isConnectionActive && (
				<div className="flex items-center justify-between gap-2 px-6 pb-6">
					<SyncNowButton
						onClick={onSync}
						isTriggering={isTriggering}
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
