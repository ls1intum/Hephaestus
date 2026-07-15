import { formatDistanceToNow } from "date-fns";
import { ExternalLinkIcon, WebhookIcon, ZapOffIcon } from "lucide-react";
import type { ConnectionSyncStatus } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ActiveJobProgress } from "./ActiveJobProgress";
import { LastProcessedEvent } from "./LastProcessedEvent";
import { RateLimitGauge } from "./RateLimitGauge";
import { SyncNowButton } from "./SyncNowButton";
import { asDate } from "./sync-format";

export interface ScmConnectionCardProps {
	provider: "GITHUB" | "GITLAB" | undefined;
	label: string;
	status?: ConnectionSyncStatus;
	isLoading: boolean;
	error?: unknown;
	isConnectionActive: boolean;
	isAppInstallationWorkspace: boolean;
	isTriggering: boolean;
	isCancelling: boolean;
	onRetry: () => void;
	onSync: () => void;
	onBackfill: () => void;
	onCancel: () => void;
}

export function ScmConnectionCard({
	provider,
	label,
	status,
	isLoading,
	error,
	isConnectionActive,
	isAppInstallationWorkspace,
	isTriggering,
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
				<h2 data-slot="card-title" className="text-base leading-snug font-medium">
					Connection
				</h2>
			</CardHeader>
			<CardContent className="space-y-4">
				{error ? (
					<QueryErrorAlert
						error={error}
						title={`We couldn't load the ${label} connection`}
						onRetry={onRetry}
					/>
				) : isLoading ? (
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
										? formatDistanceToNow(asDate(status.lastSuccessfulSyncAt) ?? new Date(), {
												addSuffix: true,
											})
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
											<LastProcessedEvent lastEventAt={status.lastEventProcessedAt} />
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
									<p className="text-muted-foreground text-xs uppercase tracking-wide">Backfill</p>
									<p className="text-sm">
										{status.backfill.state}
										{status.backfill.percent != null ? ` — ${status.backfill.percent}%` : ""}
									</p>
								</div>
							)}
						</div>

						<ActiveJobProgress job={activeJob} />

						{isConnectionActive && (
							<div className="flex flex-wrap items-center gap-2 pt-2">
								<SyncNowButton onClick={onSync} isTriggering={isTriggering} activeJob={activeJob} />
								{provider === "GITHUB" && status.backfill?.state !== "DISABLED" && (
									<SyncNowButton
										label="Backfill"
										onClick={onBackfill}
										isTriggering={isTriggering}
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
