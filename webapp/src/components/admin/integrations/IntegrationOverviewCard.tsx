import { Link } from "@tanstack/react-router";
import { AlertCircleIcon, ArrowRightIcon, PlugZapIcon } from "lucide-react";
import type { ConnectionSyncStatus, IntegrationCatalogEntry } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { GithubIcon, GitlabIcon, OutlineIcon, SlackIcon } from "@/components/icons/brand";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ActiveJobProgress } from "./ActiveJobProgress";
import { ConnectionHealthBadge } from "./ConnectionHealthBadge";
import { ConnectionStateNotice } from "./ConnectionStateNotice";
import { IntegrationCardHeading } from "./IntegrationCardHeading";
import { LastProcessedEvent } from "./LastProcessedEvent";
import { SyncNowButton } from "./SyncNowButton";
import { relativeTime } from "./sync-format";

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
	/** The thrown status error, so the alert can name what went wrong and judge whether Retry helps. */
	statusError?: unknown;
	/** Refetches the status query. Omit only where no refetch handle exists. */
	onRetryStatus?: () => void;
	isTriggering?: boolean;
	onSync: () => void;
}

export function IntegrationOverviewCard({
	workspaceSlug,
	entry,
	status,
	isStatusLoading = false,
	isStatusError = false,
	statusError,
	onRetryStatus,
	isTriggering = false,
	onSync,
}: IntegrationOverviewCardProps) {
	const detailTo = DETAIL_ROUTE[entry.kind];
	const isConnectionActive = entry.connectionState === "ACTIVE";
	const isScm = entry.kind === "GITHUB" || entry.kind === "GITLAB";

	return (
		<Card>
			<CardHeader>
				<IntegrationCardHeading className="flex items-center gap-2">
					{KIND_ICON[entry.kind]}
					{entry.displayName}
				</IntegrationCardHeading>
				{entry.connected && status && (
					<CardAction>
						<ConnectionHealthBadge health={status.health} isSyncing={status.activeJob != null} />
					</CardAction>
				)}
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
					<ConnectionStateNotice
						connectionState={entry.connectionState}
						displayName={entry.displayName}
					/>
				) : isStatusLoading ? (
					/* Two text lines, matching the status strip this resolves into, so the card keeps its
					   height instead of growing a slab into a pair of lines. */
					<div className="space-y-2">
						<Skeleton className="h-4 w-56" />
						<Skeleton className="h-4 w-32" />
					</div>
				) : isStatusError ? (
					<QueryErrorAlert
						error={statusError}
						title="We couldn't load sync status"
						onRetry={onRetryStatus}
					/>
				) : (
					status && (
						<div className="space-y-2 text-sm">
							<div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-muted-foreground">
								<span>
									{status.lastSuccessfulSyncAt
										? `Last synced ${relativeTime(status.lastSuccessfulSyncAt)}`
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
				<CardFooter className="justify-between gap-2">
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
				</CardFooter>
			)}
		</Card>
	);
}
