import type { ConnectionSyncStatus } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { ButtonGroup } from "@/components/ui/button-group";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { ActiveJobProgress } from "./ActiveJobProgress";
import { IntegrationCardHeading } from "./IntegrationCardHeading";
import { LastProcessedEvent } from "./LastProcessedEvent";
import { SyncNowButton } from "./SyncNowButton";
import { relativeTime } from "./sync-format";

export interface SlackSyncStatusCardProps {
	status: ConnectionSyncStatus;
	isConnectionActive: boolean;
	isTriggering: boolean;
	isCancelling: boolean;
	onSync: () => void;
	onCancel: () => void;
}

export function SlackSyncStatusCard({
	status,
	isConnectionActive,
	isTriggering,
	isCancelling,
	onSync,
	onCancel,
}: SlackSyncStatusCardProps) {
	return (
		<Card>
			<CardHeader>
				<IntegrationCardHeading>Sync status</IntegrationCardHeading>
			</CardHeader>
			<CardContent className="space-y-4">
				<div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
					<span>
						{status.lastSuccessfulSyncAt
							? `Last synced ${relativeTime(status.lastSuccessfulSyncAt)}`
							: isConnectionActive && !status.activeJob
								? "No channels activated yet"
								: "Never synced"}
					</span>
					<LastProcessedEvent lastEventAt={status.lastEventProcessedAt} />
				</div>

				{/* The detail page is where an admin comes to watch a run; showing progress on the overview
				    card but not here inverted that. Determinate whenever the server knows the total. */}
				<ActiveJobProgress job={status.activeJob} />

				{isConnectionActive && (
					<ButtonGroup>
						<SyncNowButton
							onClick={onSync}
							isTriggering={isTriggering}
							activeJob={status.activeJob}
						/>
						{status.activeJob && (
							<Button
								variant="outline"
								size="sm"
								disabled={isCancelling || status.activeJob.cancelRequested}
								onClick={onCancel}
							>
								{status.activeJob.cancelRequested ? "Stopping after current step…" : "Cancel"}
							</Button>
						)}
					</ButtonGroup>
				)}
			</CardContent>
		</Card>
	);
}
