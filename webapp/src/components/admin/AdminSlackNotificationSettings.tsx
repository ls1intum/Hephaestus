import { useMutation } from "@tanstack/react-query";
import { CheckIcon, ExternalLinkIcon, LoaderIcon, SendIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
	initiateMutation,
	sendTestMessageMutation,
	updateLeaderboardDigestMutation,
	updateStatus1Mutation,
} from "@/api/@tanstack/react-query.gen";
import type { SlackChannelCandidate } from "@/api/types.gen";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldGroup,
	FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { parseSlackChannelReference } from "@/lib/slack-channel-reference";
import { SlackChannelPicker } from "./slack-channels/SlackChannelPicker";

export interface AdminSlackNotificationSettingsProps {
	workspaceSlug: string;
	hasSlackConnection: boolean;
	slackConnectionId?: number;
	channelId?: string;
	teamLabel?: string;
	enabled: boolean;
	scheduleDay?: number;
	scheduleTime?: string;
	channelCandidates?: SlackChannelCandidate[];
	onSaved: () => void;
}

const TIME_24H = /^([01]\d|2[0-3]):[0-5]\d$/;
const DEFAULT_DAY = 1;
const DEFAULT_TIME = "09:00";

const DAYS = [
	{ value: "1", label: "Monday" },
	{ value: "2", label: "Tuesday" },
	{ value: "3", label: "Wednesday" },
	{ value: "4", label: "Thursday" },
	{ value: "5", label: "Friday" },
	{ value: "6", label: "Saturday" },
	{ value: "7", label: "Sunday" },
];

export function AdminSlackNotificationSettings({
	workspaceSlug,
	hasSlackConnection,
	slackConnectionId,
	channelId,
	teamLabel,
	enabled,
	scheduleDay,
	scheduleTime,
	channelCandidates = [],
	onSaved,
}: AdminSlackNotificationSettingsProps) {
	const [channelInput, setChannelInput] = useState(channelId ?? "");
	const [teamInput, setTeamInput] = useState(teamLabel ?? "");
	const [enabledInput, setEnabledInput] = useState(enabled);
	const [dayInput, setDayInput] = useState(String(scheduleDay ?? DEFAULT_DAY));
	const [timeInput, setTimeInput] = useState(scheduleTime ?? DEFAULT_TIME);
	const [disconnectOpen, setDisconnectOpen] = useState(false);

	useEffect(() => {
		const result = window.sessionStorage.getItem("slack-connect-result");
		if (!result) return;
		const reason = window.sessionStorage.getItem("slack-connect-reason") ?? undefined;
		window.sessionStorage.removeItem("slack-connect-result");
		window.sessionStorage.removeItem("slack-connect-reason");
		if (result === "success") toast.success("Slack workspace connected");
		else toast.error("Slack connection failed", { description: reason });
	}, []);

	const parsedChannel = parseSlackChannelReference(channelInput);
	const channelInvalid = channelInput.length > 0 && parsedChannel == null;
	const channelValid = parsedChannel != null;
	const channelRequired = enabledInput && !channelValid && channelInput.length === 0;
	const timeInvalid = !TIME_24H.test(timeInput);
	const selectableDigestChannels = channelCandidates.filter((candidate) => !candidate.archived);

	const save = useMutation({
		...updateLeaderboardDigestMutation(),
		onSuccess: () => {
			toast.success("Slack digest settings saved");
			onSaved();
		},
		onError: (e) => {
			toast.error("Failed to save Slack digest settings", {
				description: e instanceof Error ? e.message : undefined,
			});
		},
	});

	const test = useMutation({
		...sendTestMessageMutation(),
		onSuccess: (data) => {
			if (data.ok) {
				toast.success("Test message posted to Slack", {
					description: data.channelId ? `Channel ${data.channelId}` : undefined,
				});
				return;
			}
			const reason =
				data.slackError === "no_channel_configured"
					? "No channel is configured — enter a channel ID first."
					: (data.slackError ?? "unknown error");
			toast.error(`Slack rejected: ${reason}`);
		},
		onError: (e) => {
			toast.error("Test message failed", {
				description: e instanceof Error ? e.message : undefined,
			});
		},
	});

	const connect = useMutation({
		...initiateMutation(),
		onSuccess: (initiation) => {
			if (initiation.type === "REDIRECT" && initiation.vendorUrl) {
				window.location.assign(initiation.vendorUrl);
				return; // page is unloading
			}
			throw new Error(`Unexpected non-redirect Slack initiation: ${initiation.type}`);
		},
		onError: (e) => {
			toast.error("Could not start Slack OAuth", {
				description: e instanceof Error ? e.message : undefined,
			});
		},
	});

	const disconnect = useMutation({
		...updateStatus1Mutation(),
		onSuccess: () => {
			setDisconnectOpen(false);
			toast.success("Slack disconnected");
			onSaved();
		},
		onError: (e) => {
			toast.error("Failed to disconnect Slack", {
				description: e instanceof Error ? e.message : undefined,
			});
		},
	});

	return (
		<div className="space-y-6">
			<div>
				<h2 className="text-lg font-semibold mb-4">Slack integration</h2>
				<Card>
					<CardContent className="space-y-4">
						<p className="text-sm text-muted-foreground">
							Install Hephaestus in Slack for weekly digests, the DM mentor, App Home privacy
							controls, and optional monitored channels. Channel messages are not stored until an
							admin activates a channel.
						</p>

						{!hasSlackConnection ? (
							<Button
								onClick={() => {
									// Persist the originating slug so the OAuth landing route can route back.
									window.sessionStorage.setItem("slack-connect-return-slug", workspaceSlug);
									connect.mutate({
										path: { workspaceSlug },
										body: { kind: "SLACK", userInput: {} },
									});
								}}
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
								<div className="flex items-center gap-2 rounded-md border bg-muted/40 px-3 py-2 text-sm">
									<CheckIcon className="text-muted-foreground size-4" />
									<span>Slack workspace connected</span>
								</div>

								<div>
									<h3 className="font-medium">Weekly digest</h3>
									<p className="text-muted-foreground text-sm">
										Optional leaderboard summary posted to one Slack channel on a schedule.
									</p>
								</div>

								<FieldGroup>
									<Field orientation="horizontal">
										<FieldContent>
											<FieldLabel htmlFor="slack-enabled" className="font-medium">
												Send weekly digest
											</FieldLabel>
											<FieldDescription>
												Posts on the schedule below; the leaderboard cycle ends at the same moment.
											</FieldDescription>
										</FieldContent>
										<Switch
											id="slack-enabled"
											checked={enabledInput}
											disabled={save.isPending}
											onCheckedChange={setEnabledInput}
										/>
									</Field>

									<div className="grid grid-cols-2 gap-4">
										<Field>
											<FieldLabel htmlFor="slack-day">Day</FieldLabel>
											<Select
												items={DAYS}
												value={dayInput}
												disabled={save.isPending}
												onValueChange={(value) => {
													if (value) setDayInput(value);
												}}
											>
												<SelectTrigger id="slack-day">
													<SelectValue />
												</SelectTrigger>
												<SelectContent>
													{DAYS.map((d) => (
														<SelectItem key={d.value} value={d.value}>
															{d.label}
														</SelectItem>
													))}
												</SelectContent>
											</Select>
										</Field>

										<Field data-invalid={timeInvalid}>
											<FieldLabel htmlFor="slack-time">Time (24h)</FieldLabel>
											<Input
												id="slack-time"
												type="time"
												value={timeInput}
												disabled={save.isPending}
												onChange={(e) => setTimeInput(e.target.value)}
												aria-invalid={timeInvalid}
											/>
											<FieldDescription>
												When the weekly cycle ends and the digest posts (workspace timezone).
											</FieldDescription>
											{timeInvalid && <FieldError>Time must be in HH:mm format.</FieldError>}
										</Field>
									</div>

									<Field data-invalid={channelInvalid || channelRequired}>
										<FieldLabel htmlFor="slack-channel">Digest channel</FieldLabel>
										{selectableDigestChannels.length > 0 && (
											<SlackChannelPicker
												aria-label="Search digest Slack channels"
												candidates={selectableDigestChannels}
												disabled={save.isPending}
												selectedChannelId={parsedChannel?.channelId}
												getDisabledReason={(candidate) =>
													candidate.member ? undefined : "Needs invite"
												}
												onSelect={(candidate) => setChannelInput(candidate.slackChannelId)}
											/>
										)}
										<Input
											id="slack-channel"
											value={channelInput}
											disabled={save.isPending}
											onChange={(e) => setChannelInput(e.target.value)}
											placeholder="https://…slack.com/archives/C0974LJBPBK"
											autoComplete="off"
											aria-invalid={channelInvalid}
										/>
										<FieldDescription>
											Invite Hephaestus to the channel in Slack, then paste a channel URL or stable
											C…/G… ID.
										</FieldDescription>
										{channelRequired && (
											<FieldError>Choose a channel before enabling the weekly digest.</FieldError>
										)}
										{channelInvalid && (
											<FieldError>
												Paste a Slack channel URL, mention, or C…/G… channel ID.
											</FieldError>
										)}
									</Field>

									<Field>
										<FieldLabel htmlFor="slack-team">Team filter (optional)</FieldLabel>
										<Input
											id="slack-team"
											value={teamInput}
											disabled={save.isPending}
											onChange={(e) => setTeamInput(e.target.value)}
											placeholder="e.g. engineering"
											autoComplete="off"
										/>
										<FieldDescription>
											Restrict the leaderboard to a single team. Leave blank to include every
											contributor in the workspace.
										</FieldDescription>
									</Field>
								</FieldGroup>

								<div className="flex gap-2 pt-2">
									<Button
										onClick={() =>
											save.mutate({
												path: { workspaceSlug },
												body: {
													day: Number(dayInput),
													time: timeInput,
													enabled: enabledInput,
													channelId: parsedChannel?.channelId,
													team: teamInput.length > 0 ? teamInput : undefined,
												},
											})
										}
										disabled={save.isPending || channelInvalid || channelRequired || timeInvalid}
									>
										{save.isPending ? "Saving…" : "Save"}
									</Button>
									<Button
										variant="outline"
										onClick={() =>
											test.mutate({
												path: { workspaceSlug },
												body: { channelId: parsedChannel?.channelId },
											})
										}
										disabled={test.isPending || !channelValid}
									>
										<SendIcon className="mr-2 size-3.5" />
										{test.isPending ? "Sending…" : "Send test message"}
									</Button>
								</div>

								{slackConnectionId != null && (
									<div className="flex justify-end border-t pt-4">
										<Button
											variant="outline"
											className="text-destructive"
											onClick={() => setDisconnectOpen(true)}
											disabled={disconnect.isPending}
										>
											{disconnect.isPending ? "Disconnecting…" : "Disconnect Slack…"}
										</Button>
									</div>
								)}
							</>
						)}
					</CardContent>
				</Card>
			</div>

			<AlertDialog open={disconnectOpen} onOpenChange={setDisconnectOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Disconnect Slack?</AlertDialogTitle>
						<AlertDialogDescription>
							The weekly digest will stop posting and the bot will be uninstalled from this
							workspace. You can reconnect later, but you will need to re-authorize via OAuth.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={disconnect.isPending}>Cancel</AlertDialogCancel>
						<AlertDialogAction
							variant="destructive"
							disabled={disconnect.isPending || slackConnectionId == null}
							onClick={() => {
								if (slackConnectionId == null) return;
								disconnect.mutate({
									path: { workspaceSlug, id: slackConnectionId },
									body: { state: "UNINSTALLED" },
								});
							}}
						>
							{disconnect.isPending ? "Disconnecting…" : "Disconnect"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
}
