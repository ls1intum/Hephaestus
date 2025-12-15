import { CheckCircle2, Slack } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";

interface AdminSlackSettingsProps {
	workspaceSlug: string;
	hasSlackToken: boolean;
}

export function AdminSlackSettings({
	workspaceSlug,
	hasSlackToken,
}: AdminSlackSettingsProps) {
	const handleConnect = () => {
		window.location.href = `/api/workspaces/${workspaceSlug}/slack/install`;
	};

	return (
		<Card>
			<CardHeader>
				<CardTitle>Slack Integration</CardTitle>
				<CardDescription>
					Connect this workspace to a Slack team to enable Hephaestus bot
					features.
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-4">
				<div className="flex items-center justify-between border p-4 rounded-lg bg-card/50">
					<div className="flex items-center gap-4">
						<div
							className={`p-2 rounded-full ${
								hasSlackToken
									? "bg-green-100 text-green-600 dark:bg-green-900/30"
									: "bg-muted text-muted-foreground"
							}`}
						>
							{hasSlackToken ? (
								<CheckCircle2 className="h-6 w-6" />
							) : (
								<Slack className="h-6 w-6" />
							)}
						</div>
						<div>
							<h3 className="font-medium">
								{hasSlackToken ? "Connected to Slack" : "Not Connected"}
							</h3>
							<p className="text-muted-foreground text-sm">
								{hasSlackToken
									? "The bot is installed and ready to send notifications."
									: "Install the Hephaestus bot to your Slack workspace."}
							</p>
						</div>
					</div>

					<Button
						variant={hasSlackToken ? "outline" : "default"}
						onClick={handleConnect}
						className={
							!hasSlackToken
								? "bg-[#4A154B] hover:bg-[#361139] text-white border-0"
								: ""
						}
					>
						{hasSlackToken ? "Reconnect" : "Connect Workspace"}
					</Button>
				</div>
			</CardContent>
		</Card>
	);
}
