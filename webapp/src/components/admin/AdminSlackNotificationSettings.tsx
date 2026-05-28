import { useMutation } from "@tanstack/react-query";
import { CheckIcon, ExternalLinkIcon, LoaderIcon, SendIcon } from "lucide-react";
import { useEffect, useState } from "react";
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

	// Pop any OAuth-callback result the /integrations route stashed and surface it.
	useEffect(() => {
		const result = window.sessionStorage.getItem("slack-connect-result");
		if (!result) return;
		const reason = window.sessionStorage.getItem("slack-connect-reason") ?? undefined;
		window.sessionStorage.removeItem("slack-connect-result");
		window.sessionStorage.removeItem("slack-connect-reason");
		if (result === "success") toast.success("Slack workspace connected");
		else toast.error("Slack connection failed", { description: reason });
	}, []);
	// Re-sync form state when props change (e.g. parent refetches after OAuth completion)
	// — but ONLY when the user hasn't typed anything yet, so we don't clobber in-flight edits.
	const [dirty, setDirty] = useState(false);
	useEffect(() => {
		if (!dirty) setChannelInput(channelId ?? "");
	}, [channelId, dirty]);
	useEffect(() => {
		if (!dirty) setTeamInput(teamLabel ?? "");
	}, [teamLabel, dirty]);
	useEffect(() => {
		if (!dirty) setEnabledInput(enabled);
	}, [enabled, dirty]);

	const channelInvalid = channelInput.length > 0 && !SLACK_CHANNEL_ID.test(channelInput);

	const update = useMutation({
		...updateNotificationsMutation(),
		onSuccess: () => {
			toast.success("Slack notification settings saved");
			setDirty(false);
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

	const connect = useMutation({
		mutationFn: () => {
			// Persist the originating slug so the OAuth landing route can route back.
			window.sessionStorage.setItem("slack-connect-return-slug", workspaceSlug);
			return initiateSlackConnection(workspaceId);
		},
		onSuccess: (initiation) => {
			if (initiation.type === "redirect") {
				window.location.assign(initiation.vendorUrl);
				return; // page is unloading
			}
			// type === "linked" — no OAuth needed (e.g. PAT flow). Slack never reaches here today.
			toast.success("Slack workspace already linked");
			onSaved();
		},
		onError: (e) => {
			toast.error("Could not start Slack OAuth", { description: String(e) });
		},
	});

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
							<Button
								onClick={() => connect.mutate()}
								disabled={connect.isPending}
								className="w-full"
							>
								{connect.isPending ? (
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
										onCheckedChange={(value) => {
											setEnabledInput(value);
											setDirty(true);
										}}
									/>
								</div>

								<div className="space-y-2">
									<Label htmlFor="slack-channel">Channel ID</Label>
									<Input
										id="slack-channel"
										value={channelInput}
										onChange={(e) => {
											setChannelInput(e.target.value.trim());
											setDirty(true);
										}}
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
										onChange={(e) => {
											setTeamInput(e.target.value);
											setDirty(true);
										}}
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
