import { ChevronDownIcon, DatabaseIcon, GaugeIcon, WebhookIcon, ZapOffIcon } from "lucide-react";
import type { ReactNode } from "react";
import type { ConnectionSyncStatus, RateLimitSnapshot } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Button } from "@/components/ui/button";
import { ButtonGroup } from "@/components/ui/button-group";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Item, ItemContent, ItemGroup, ItemMedia, ItemTitle } from "@/components/ui/item";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { ActiveJobProgress } from "./ActiveJobProgress";
import { ConnectionHealthBadge } from "./ConnectionHealthBadge";
import { IntegrationCardHeading } from "./IntegrationCardHeading";
import { RelativeTime } from "./RelativeTime";
import { SyncNowButton } from "./SyncNowButton";
import {
	freshnessTone,
	nextRunLabel,
	relativeTime,
	type SyncTriggerType,
	stateLabel,
} from "./sync-format";

/** Under this share of the budget a rate limit is a thing that will break the next sync, not a stat. */
const RATE_LIMIT_WARNING_FRACTION = 0.1;

/**
 * One diagnostics fact: an icon, a label and a value. Deliberately not a metric tile — these are four
 * scalars an admin scans past on the way to the freshness sentence above, and tile chrome would give
 * them more weight than the reading they exist to qualify.
 */
function DiagnosticItem({
	icon,
	label,
	children,
}: {
	icon: ReactNode;
	label: string;
	children: ReactNode;
}) {
	return (
		<Item size="sm" role="listitem" className="w-auto gap-2 border-0 p-0">
			<ItemMedia variant="icon" className="text-muted-foreground">
				{icon}
			</ItemMedia>
			<ItemContent className="gap-0">
				<span className="text-muted-foreground text-xs">{label}</span>
				<ItemTitle className="font-normal text-sm">{children}</ItemTitle>
			</ItemContent>
		</Item>
	);
}

/**
 * The remaining API budget as a number.
 *
 * This used to be a `Progress` bar, which put "full = healthy" directly beside the job bar's
 * "full = done" — two bars on one card, filling for opposite reasons. The number was always the fact;
 * the bar only added an inverted metaphor. The reset window is the one thing a bare number loses, so
 * it moves into a tooltip, and the only reading that needs colour — a budget about to run out — is the
 * only one that gets it.
 */
function RateLimitValue({ rateLimit }: { rateLimit: RateLimitSnapshot }) {
	const isLow =
		rateLimit.limit > 0 && rateLimit.remaining / rateLimit.limit < RATE_LIMIT_WARNING_FRACTION;
	const value = (
		<span className={isLow ? "text-warning tabular-nums" : "tabular-nums"}>
			{rateLimit.remaining.toLocaleString()}
			<span className="text-muted-foreground"> / {rateLimit.limit.toLocaleString()}</span>
		</span>
	);

	if (!rateLimit.resetAt) return value;

	return (
		<Tooltip>
			<TooltipTrigger className="cursor-help">{value}</TooltipTrigger>
			<TooltipContent>Resets {relativeTime(rateLimit.resetAt)}</TooltipContent>
		</Tooltip>
	);
}

function ConnectionDiagnostics({ status }: { status: ConnectionSyncStatus }) {
	return (
		<ItemGroup className="flex-row flex-wrap gap-x-8 gap-y-3">
			<DiagnosticItem
				icon={status.webhookRegistered === false ? <ZapOffIcon /> : <WebhookIcon />}
				label="Webhook"
			>
				{status.webhookRegistered === false ? (
					<span className="text-muted-foreground">Not registered</span>
				) : status.lastEventProcessedAt ? (
					<RelativeTime value={status.lastEventProcessedAt} />
				) : (
					<span className="text-muted-foreground">No events yet</span>
				)}
			</DiagnosticItem>

			{status.rateLimit && (
				<DiagnosticItem icon={<GaugeIcon />} label="Rate limit">
					<RateLimitValue rateLimit={status.rateLimit} />
				</DiagnosticItem>
			)}

			{status.backfill && (
				/* The scheduled cycle's state — "Disabled" here means the background cycle is off, not that
				   the Run backfill action below (a manual run) is unavailable. */
				<DiagnosticItem icon={<DatabaseIcon />} label="Scheduled backfill">
					{stateLabel(status.backfill.state)}
					{status.backfill.percent != null && (
						<span className="text-muted-foreground tabular-nums">
							{" "}
							· {status.backfill.percent}%
						</span>
					)}
				</DiagnosticItem>
			)}
		</ItemGroup>
	);
}

export interface SyncStatusHeaderProps {
	/** The integration's name, for copy that has to name it ("No GitHub connection found"). */
	label: string;
	status?: ConnectionSyncStatus;
	isLoading?: boolean;
	error?: unknown;
	isConnectionActive: boolean;
	/**
	 * Which trigger the admin just pressed, or `null` when none is in flight. Sync and Backfill are
	 * served by one mutation, so a bare `isPending` cannot name the operation the button should
	 * announce; the caller discriminates on the in-flight variables and passes the answer here.
	 */
	triggeringType?: SyncTriggerType | null;
	isCancelling?: boolean;
	onRetry: () => void;
	onSync: () => void;
	onBackfill?: () => void;
	onCancel: () => void;
	/** Integration-specific trailing controls, e.g. GitHub's "Manage installation" link. */
	actions?: ReactNode;
}

/**
 * The connection plane: health, freshness and the controls that change them, for every integration.
 *
 * The headline row is one sentence — health badge, when the mirror last completed, when it will next
 * run — because that is the whole question this page is opened to answer, and it was previously spread
 * across a page-header badge, an uppercase-labelled metric grid and a schedule the server sent but
 * nothing rendered. The freshness reading is tinted against the connection's own cadence, so "4 hours
 * ago" reads as fine on a six-hourly schedule and as a missed run on an hourly one.
 *
 * Everything below it qualifies that sentence: diagnostics that explain a bad reading, the running
 * job's progress, and one trigger. GitHub's card once offered Sync and Backfill side by side and had
 * to run a protocol between them so neither claimed the other's work; a split button makes the rare
 * operation a menu item and the whole problem disappears with the second button.
 */
export function SyncStatusHeader({
	label,
	status,
	isLoading = false,
	error,
	isConnectionActive,
	triggeringType = null,
	isCancelling = false,
	onRetry,
	onSync,
	onBackfill,
	onCancel,
	actions,
}: SyncStatusHeaderProps) {
	const activeJob = status?.activeJob;
	const canBackfill = status?.backfillSupported === true && onBackfill != null;
	const isTriggerBusy = triggeringType != null || activeJob != null;

	return (
		<Card>
			<CardHeader>
				<IntegrationCardHeading>Connection</IntegrationCardHeading>
			</CardHeader>
			<CardContent className="space-y-4">
				{error ? (
					<QueryErrorAlert
						error={error}
						title={`We couldn't load the ${label} connection`}
						onRetry={onRetry}
					/>
				) : isLoading ? (
					/* Mirrors the loaded layout — a headline line, a diagnostics row, a button row — so
					   resolving swaps text into boxes already the right size instead of shifting the page. */
					<div className="space-y-4">
						<Skeleton className="h-5 w-72" />
						<div className="flex flex-wrap gap-8">
							{Array.from({ length: 3 }, (_, index) => (
								<div key={index} className="space-y-1">
									<Skeleton className="h-3 w-20" />
									<Skeleton className="h-4 w-28" />
								</div>
							))}
						</div>
						<Skeleton className="h-8 w-40" />
					</div>
				) : !status ? (
					<p className="text-muted-foreground text-sm">
						No {label} connection found for this workspace.
					</p>
				) : (
					<>
						<div className="flex flex-wrap items-center gap-x-3 gap-y-2">
							<ConnectionHealthBadge health={status.health} isSyncing={activeJob != null} />
							<p className="text-sm">
								{status.lastSuccessfulSyncAt ? (
									<>
										Last synced{" "}
										<RelativeTime
											value={status.lastSuccessfulSyncAt}
											tone={freshnessTone(status.lastSuccessfulSyncAt, status.syncIntervalSeconds)}
										/>
									</>
								) : (
									/* "Never synced" on a connection with nothing to sync yet is an accusation the
									   facts don't support — a fresh Slack workspace has no activated channels. */
									<span className="text-muted-foreground">
										{status.resourceCounts.total === 0
											? "No resources to sync yet"
											: "Never synced"}
									</span>
								)}
								{nextRunLabel(status.nextScheduledSyncAt) && (
									<span className="text-muted-foreground">
										{" · "}
										{nextRunLabel(status.nextScheduledSyncAt)}
									</span>
								)}
							</p>
						</div>

						<ConnectionDiagnostics status={status} />

						<ActiveJobProgress job={activeJob} />

						{isConnectionActive && (
							<div className="flex flex-wrap items-center gap-2 pt-2">
								{/* Sync / Backfill / Cancel act on one connection, so they read as one control. */}
								<ButtonGroup>
									<SyncNowButton
										onClick={onSync}
										triggeringType={triggeringType}
										activeJob={activeJob}
									/>
									{canBackfill && (
										<DropdownMenu>
											<DropdownMenuTrigger
												render={
													<Button
														variant="outline"
														size="sm"
														disabled={isTriggerBusy}
														aria-label="More sync options"
													>
														<ChevronDownIcon className="size-4" />
													</Button>
												}
											/>
											{/* The popup is anchored to a chevron, so it must opt out of the default
											    trigger-width sizing or "Run backfill" lands in a 32px column. */}
											<DropdownMenuContent align="end" className="w-auto min-w-40">
												<DropdownMenuItem onClick={onBackfill}>Run backfill</DropdownMenuItem>
											</DropdownMenuContent>
										</DropdownMenu>
									)}
									{activeJob && (
										<Button
											variant="outline"
											size="sm"
											disabled={isCancelling || activeJob.cancelRequested}
											onClick={onCancel}
										>
											{activeJob.cancelRequested ? "Stopping after current step…" : "Cancel"}
										</Button>
									)}
								</ButtonGroup>
								{actions && <div className="ml-auto">{actions}</div>}
							</div>
						)}
					</>
				)}
			</CardContent>
		</Card>
	);
}
