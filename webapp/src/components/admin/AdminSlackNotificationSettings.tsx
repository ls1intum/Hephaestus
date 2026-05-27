import { useMutation } from "@tanstack/react-query";
import { CheckIcon, ExternalLinkIcon, LoaderIcon, SendIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { updateNotificationsMutation } from "@/api/@tanstack/react-query.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { initiateSlackConnection, sendSlackTestMessage } from "@/lib/slackConnectionApi";

export interface AdminSlackNotificationSettingsProps {
	workspaceId: number;
	workspaceSlug: string;
	hasSlackConnection: boolean;
	channelId?: string;
	teamLabel?: string;
	enabled: boolean;
	onSaved: () => void;
}

const SLACK_CHANNEL_ID = /^[CGD][A-Z0-9]{8,}$/;

export function AdminSlackNotificationSettings({
	workspaceId,
	workspaceSlug,
	hasSlackConnection,
	channelId,
	teamLabel,
	enabled,
	onSaved,
}: AdminSlackNotificationSettingsProps) {
	const [channelInput, setChannelInput] = useState(channelId ?? "");
	const [teamInput, setTeamInput] = useState(teamLabel ?? "");
	const [enabledInput, setEnabledInput] = useState(enabled);
	const [isConnecting, setIsConnecting] = useState(false);

	const channelInvalid = channelInput.length > 0 && !SLACK_CHANNEL_ID.test(channelInput);

	const update = useMutation({
		...updateNotificationsMutation(),
		onSuccess: () => {
			toast.success("Slack notification settings saved");
			onSaved();
		},
		onError: (e) => {
			toast.error("Failed to save Slack notification settings", { description: String(e) });
		},
	});

	const test = useMutation({
		mutationFn: () => sendSlackTestMessage(workspaceId),
		onSuccess: (data) => {
			if (data.ok) {
				toast.success("Test message posted to Slack");
			} else {
				toast.error(`Slack rejected the test message: ${data.slackError ?? "unknown error"}`);
			}
		},
		onError: (e) => {
			toast.error("Test message failed", { description: String(e) });
		},
	});

	const connect = async () => {
		setIsConnecting(true);
		try {
			// Persist the originating slug so the OAuth landing route can route back.
			window.sessionStorage.setItem("slack-connect-return-slug", workspaceSlug);
			const initiation = await initiateSlackConnection(workspaceId);
			if (initiation.type === "REDIRECT") {
				window.location.assign(initiation.url);
			} else {
				// Already linked — refresh state.
				toast.success("Slack workspace already linked");
				onSaved();
				setIsConnecting(false);
			}
		} catch (e) {
			toast.error("Could not start Slack OAuth", { description: String(e) });
			setIsConnecting(false);
		}
	};

	return (
		<div className="space-y-6">
			<div>
				<h2 className="text-lg font-semibold mb-4">Slack notifications</h2>
				<Card>
					<CardContent className="space-y-4">
						<p className="text-sm text-muted-foreground">
							Post the weekly leaderboard digest to a Slack channel. The bot is installed once per
							workspace via OAuth; you can change the channel later without re-installing.
						</p>

						{!hasSlackConnection ? (
							<Button onClick={connect} disabled={isConnecting} className="w-full">
								{isConnecting ? (
									<>
										<LoaderIcon className="mr-2 size-4 animate-spin" />
										Redirecting to Slack…
									</>
								) : (
									<>
										Connect Slack workspace
										<ExternalLinkIcon className="ml-2 size-3.5" />
									</>
								)}
							</Button>
						) : (
							<>
								<div className="flex items-center gap-2 text-sm">
									<CheckIcon className="size-4 text-green-600" />
									<span>Slack workspace connected</span>
								</div>

								<div className="flex items-center justify-between gap-4">
									<div>
										<Label htmlFor="slack-enabled" className="font-medium">
											Send weekly digest
										</Label>
										<p className="text-xs text-muted-foreground">
											Posts every Monday at the workspace's leaderboard schedule.
										</p>
									</div>
									<Switch
										id="slack-enabled"
										checked={enabledInput}
										onCheckedChange={setEnabledInput}
									/>
								</div>

								<div className="space-y-2">
									<Label htmlFor="slack-channel">Channel ID</Label>
									<Input
										id="slack-channel"
										value={channelInput}
										onChange={(e) => setChannelInput(e.target.value.trim())}
										placeholder="C0974LJBPBK"
										autoComplete="off"
										aria-invalid={channelInvalid}
									/>
									<p className="text-xs text-muted-foreground">
										Right-click the channel in Slack → <em>View channel details</em> → copy the ID
										at the bottom. The bot must already be a member (or the channel must be public —
										the bot installed with <code>chat:write.public</code>).
									</p>
									{channelInvalid && (
										<p className="text-xs text-destructive">
											Channel IDs start with C / G / D followed by 8+ alphanumerics.
										</p>
									)}
								</div>

								<div className="space-y-2">
									<Label htmlFor="slack-team">Team filter (optional)</Label>
									<Input
										id="slack-team"
										value={teamInput}
										onChange={(e) => setTeamInput(e.target.value)}
										placeholder="e.g. engineering"
										autoComplete="off"
									/>
									<p className="text-xs text-muted-foreground">
										Restrict the leaderboard to a single team. Leave blank to include every
										contributor in the workspace.
									</p>
								</div>

								<div className="flex gap-2 pt-2">
									<Button
										onClick={() =>
											update.mutate({
												path: { workspaceSlug },
												body: {
													enabled: enabledInput,
													channelId: channelInput.length > 0 ? channelInput : undefined,
													team: teamInput.length > 0 ? teamInput : undefined,
												},
											})
										}
										disabled={update.isPending || channelInvalid}
									>
										{update.isPending ? "Saving…" : "Save"}
									</Button>
									<Button
										variant="outline"
										onClick={() => test.mutate()}
										disabled={test.isPending || channelInput.length === 0}
									>
										<SendIcon className="mr-2 size-3.5" />
										{test.isPending ? "Sending…" : "Send test message"}
									</Button>
								</div>
							</>
						)}
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
