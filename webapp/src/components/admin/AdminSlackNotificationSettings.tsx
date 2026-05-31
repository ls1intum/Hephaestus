import { useMutation } from "@tanstack/react-query";
import { CheckIcon, ExternalLinkIcon, LoaderIcon, SendIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
	initiateMutation,
	sendTestMessageMutation,
	updateNotificationsMutation,
	updateScheduleMutation,
	updateStatus1Mutation,
} from "@/api/@tanstack/react-query.gen";
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

export interface AdminSlackNotificationSettingsProps {
	workspaceSlug: string;
	hasSlackConnection: boolean;
	/** Active Slack connection id; enables the Disconnect affordance when present. */
	slackConnectionId?: number;
	channelId?: string;
	teamLabel?: string;
	enabled: boolean;
	/** Day of week the weekly cycle ends (1=Monday … 7=Sunday). */
	scheduleDay?: number;
	/** Time of day the weekly cycle ends, "HH:mm" (24h). */
	scheduleTime?: string;
	onSaved: () => void;
}

// Client-side format hints only — the server re-validates both on save.
const SLACK_CHANNEL_ID = /^[CGD][A-Z0-9]{8,}$/;
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
	onSaved,
}: AdminSlackNotificationSettingsProps) {
	// State initializes ONCE from props. There is intentionally no prop→state effect:
	// the parent gives this component a `key` derived from the server snapshot, so a
	// post-OAuth refetch produces a fresh key and React remounts us with server truth
	// (https://react.dev/learn/you-might-not-need-an-effect — "resetting all state when
	// a prop changes"). This avoids the dirty-flag effect maze that silently dropped
	// server updates once the admin touched any field.
	const [channelInput, setChannelInput] = useState(channelId ?? "");
	const [teamInput, setTeamInput] = useState(teamLabel ?? "");
	const [enabledInput, setEnabledInput] = useState(enabled);
	const [dayInput, setDayInput] = useState(String(scheduleDay ?? DEFAULT_DAY));
	const [timeInput, setTimeInput] = useState(scheduleTime ?? DEFAULT_TIME);
	const [disconnectOpen, setDisconnectOpen] = useState(false);

	// Pop any OAuth-callback result the /integrations route stashed and surface it.
	// Legitimate effect: it reads-and-clears a one-shot sessionStorage flag on mount.
	useEffect(() => {
		const result = window.sessionStorage.getItem("slack-connect-result");
		if (!result) return;
		const reason = window.sessionStorage.getItem("slack-connect-reason") ?? undefined;
		window.sessionStorage.removeItem("slack-connect-result");
		window.sessionStorage.removeItem("slack-connect-reason");
		if (result === "success") toast.success("Slack workspace connected");
		else toast.error("Slack connection failed", { description: reason });
	}, []);

	const channelInvalid = channelInput.length > 0 && !SLACK_CHANNEL_ID.test(channelInput);
	const channelValid = SLACK_CHANNEL_ID.test(channelInput);
	const timeInvalid = !TIME_24H.test(timeInput);

	const updateSchedule = useMutation(updateScheduleMutation());
	const updateNotifications = useMutation(updateNotificationsMutation());

	// Save schedule (day/time) and notification (channel/team/enabled) together so the admin
	// configures the whole weekly digest in one action.
	const save = useMutation({
		mutationFn: async () => {
			await updateSchedule.mutateAsync({
				path: { workspaceSlug },
				body: { day: Number(dayInput), time: timeInput },
			});
			await updateNotifications.mutateAsync({
				path: { workspaceSlug },
				body: {
					enabled: enabledInput,
					channelId: channelInput.length > 0 ? channelInput : undefined,
					team: teamInput.length > 0 ? teamInput : undefined,
				},
			});
		},
		onSuccess: () => {
			toast.success("Slack notification settings saved");
			onSaved();
		},
		onError: (e) => {
			toast.error("Failed to save Slack notification settings", {
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
			// The probe always returns 200; a falsy `ok` carries the Slack API reason.
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
			// Slack is always a REDIRECT flow. Any other shape is a contract change we want
			// to fail loudly on, not paper over with a wrong success toast.
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
				<h2 className="text-lg font-semibold mb-4">Slack notifications</h2>
				<Card>
					<CardContent className="space-y-4">
						<p className="text-sm text-muted-foreground">
							Post the weekly leaderboard digest to a Slack channel. The bot is installed once per
							workspace via OAuth; you can change the channel and schedule later without
							re-installing.
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
								<div className="flex items-center gap-2 text-sm">
									{/* no semantic success token in the kit */}
									<CheckIcon className="size-4 text-green-600 dark:text-green-400" />
									<span>Slack workspace connected</span>
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

									<Field data-invalid={channelInvalid}>
										<FieldLabel htmlFor="slack-channel">Channel ID</FieldLabel>
										<Input
											id="slack-channel"
											value={channelInput}
											disabled={save.isPending}
											onChange={(e) => setChannelInput(e.target.value.trim())}
											placeholder="C0974LJBPBK"
											autoComplete="off"
											aria-invalid={channelInvalid}
										/>
										<FieldDescription>
											Right-click the channel in Slack → <em>View channel details</em> → copy the ID
											at the bottom. The bot must already be a member (or the channel must be public
											— the bot installed with <code>chat:write.public</code>).
										</FieldDescription>
										{channelInvalid && (
											<FieldError>
												Channel IDs start with C / G / D followed by 8+ alphanumerics.
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
										onClick={() => save.mutate()}
										disabled={save.isPending || channelInvalid || timeInvalid}
									>
										{save.isPending ? "Saving…" : "Save"}
									</Button>
									<Button
										variant="outline"
										onClick={() =>
											test.mutate({ path: { workspaceSlug }, body: { channelId: channelInput } })
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
