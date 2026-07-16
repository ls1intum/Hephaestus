import { ExternalLinkIcon, WebhookIcon, ZapOffIcon } from "lucide-react";
import type { ConnectionSyncStatus } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Button } from "@/components/ui/button";
import { ButtonGroup } from "@/components/ui/button-group";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ActiveJobProgress } from "./ActiveJobProgress";
import { IntegrationCardHeading } from "./IntegrationCardHeading";
import { LastProcessedEvent } from "./LastProcessedEvent";
import { RateLimitGauge } from "./RateLimitGauge";
import { SyncNowButton } from "./SyncNowButton";
import { relativeTime, type SyncTriggerType, stateLabel } from "./sync-format";

export interface ScmConnectionCardProps {
	label: string;
	status?: ConnectionSyncStatus;
	isLoading: boolean;
	error?: unknown;
	isConnectionActive: boolean;
	isAppInstallationWorkspace: boolean;
	/**
	 * Which trigger the admin just pressed, or `null` when none is in flight. Sync and Backfill are
	 * served by one mutation, so a bare `isPending` cannot say which button is starting — spinning both
	 * misreports what the server is doing. The caller discriminates on the in-flight variables and
	 * passes the answer here; each button then keys its pending state off its own type.
	 */
	triggeringType?: SyncTriggerType | null;
	isCancelling: boolean;
	onRetry: () => void;
	onSync: () => void;
	onBackfill: () => void;
	onCancel: () => void;
}

export function ScmConnectionCard({
	label,
	status,
	isLoading,
	error,
	isConnectionActive,
	isAppInstallationWorkspace,
	triggeringType = null,
	isCancelling,
	onRetry,
	onSync,
	onBackfill,
	onCancel,
}: ScmConnectionCardProps) {
	const activeJob = status?.activeJob;

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
					/* Mirrors the loaded layout — the same 2-col metric grid and a button-row block — so
					   resolving swaps text into boxes already the right size instead of shifting the page. */
					<div className="space-y-4">
						<div className="grid gap-4 sm:grid-cols-2">
							{Array.from({ length: 4 }, (_, index) => (
								<div key={index} className="space-y-1">
									<Skeleton className="h-3 w-28" />
									<Skeleton className="h-5 w-40" />
								</div>
							))}
						</div>
						<Skeleton className="h-8 w-56" />
					</div>
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
										? relativeTime(status.lastSuccessfulSyncAt)
										: "Never synced"}
								</p>
							</div>
							<div className="space-y-1">
								<p className="text-muted-foreground text-xs uppercase tracking-wide">
									Webhook activity
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
											<LastProcessedEvent lastEventAt={status.lastEventProcessedAt} hasFieldLabel />
										</span>
									)}
								</div>
							</div>
							<div className="space-y-1">
								<p className="text-muted-foreground text-xs uppercase tracking-wide">Rate limit</p>
								<RateLimitGauge rateLimit={status.rateLimit} />
							</div>
							{status.backfill && (
								<div className="space-y-1">
									{/* The scheduled cycle's state — "Disabled" here means the background cycle is off,
									    not that the Backfill button below (a manual run) is unavailable. */}
									<p className="text-muted-foreground text-xs uppercase tracking-wide">
										Scheduled backfill
									</p>
									<p className="text-sm">
										{stateLabel(status.backfill.state)}
										{status.backfill.percent != null ? ` — ${status.backfill.percent}%` : ""}
									</p>
								</div>
							)}
						</div>

						<ActiveJobProgress job={activeJob} />

						{isConnectionActive && (
							<div className="flex flex-wrap items-center gap-2 pt-2">
								{/* Sync / Backfill / Cancel act on one connection, so they read as one control. */}
								<ButtonGroup>
									<SyncNowButton
										onClick={onSync}
										isTriggering={triggeringType === "RECONCILIATION"}
										isBusy={triggeringType === "BACKFILL"}
										activeJob={activeJob}
									/>
									{status.backfillSupported && (
										<SyncNowButton
											label="Backfill"
											operationLabel="backfill"
											onClick={onBackfill}
											isTriggering={triggeringType === "BACKFILL"}
											isBusy={triggeringType === "RECONCILIATION"}
											activeJob={activeJob}
										/>
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
								{isAppInstallationWorkspace && (
									<Button
										variant="outline"
										size="sm"
										className="ml-auto"
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
								)}
							</div>
						)}
					</>
				)}
			</CardContent>
		</Card>
	);
}
