import { ExternalLink } from "lucide-react";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";

export interface SlackConnectionSectionProps {
	isConnected: boolean;
	slackUserId?: string | null;
	slackEnabled: boolean;
	linkUrl?: string | null;
	onDisconnect: () => void;
	onSync: () => void;
	isLoading?: boolean;
}

export function SlackConnectionSection({
	isConnected,
	slackUserId: _slackUserId,
	slackEnabled,
	linkUrl,
	onDisconnect,
	onSync,
	isLoading = false,
}: SlackConnectionSectionProps) {
	if (!slackEnabled && !isLoading) return null;

	const pending = Boolean(isLoading);

	const handleConnect = () => {
		if (!linkUrl) return;
		window.open(linkUrl, "_blank", "noopener,noreferrer");
	};

	return (
		<section className="space-y-4" aria-labelledby="slack-heading">
			<div className="space-y-1">
				<h2 id="slack-heading" className="text-xl font-semibold">
					Slack Connection
				</h2>
				<p className="text-sm text-muted-foreground">
					Link your Slack account to receive @-mentions in notifications.
				</p>
			</div>

			<div className="flex items-start justify-between gap-6 py-4">
				<div className="space-y-1 flex-1">
					<h3 className="text-base font-medium">
						{isConnected ? "Connected" : "Not connected"}
					</h3>
					<p className="text-sm text-muted-foreground leading-relaxed">
						{isConnected
							? "Your Slack and GitHub accounts are linked. You will be @-mentioned in leaderboard notifications."
							: "Connect your Slack account to be @-mentioned in notifications."}
					</p>
				</div>

				{isConnected ? (
					<AlertDialog>
						<AlertDialogTrigger asChild>
							<Button variant="outline" disabled={pending} className="mt-1">
								Disconnect
							</Button>
						</AlertDialogTrigger>
						<AlertDialogContent>
							<AlertDialogHeader>
								<AlertDialogTitle>Disconnect Slack?</AlertDialogTitle>
								<AlertDialogDescription>
									You will no longer receive @-mentions in Slack notifications.
									You can reconnect at any time.
								</AlertDialogDescription>
							</AlertDialogHeader>
							<AlertDialogFooter>
								<AlertDialogCancel>Cancel</AlertDialogCancel>
								<AlertDialogAction onClick={onDisconnect}>
									Disconnect
								</AlertDialogAction>
							</AlertDialogFooter>
						</AlertDialogContent>
					</AlertDialog>
				) : (
					<div className="flex gap-2">
						<Button onClick={handleConnect} disabled={!linkUrl}>
							Link Slack Account
							<ExternalLink className="ml-1 h-3 w-3" />
						</Button>
						<Button variant="outline" onClick={onSync} disabled={pending}>
							{pending ? "Checking…" : "I've Linked My Account"}
						</Button>
					</div>
				)}
			</div>
		</section>
	);
}
