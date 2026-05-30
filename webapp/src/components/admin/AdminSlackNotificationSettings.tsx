import { useMutation } from "@tanstack/react-query";
import { CheckIcon, ExternalLinkIcon, LoaderIcon, SendIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
	initiateMutation,
	sendTestMessageMutation,
	updateNotificationsMutation,
	updateScheduleMutation,
} from "@/api/@tanstack/react-query.gen";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
	channelId,
	teamLabel,
	enabled,
	scheduleDay,
	scheduleTime,
	onSaved,
}: AdminSlackNotificationSettingsProps) {
	const [channelInput, setChannelInput] = useState(channelId ?? "");
	const [teamInput, setTeamInput] = useState(teamLabel ?? "");
	const [enabledInput, setEnabledInput] = useState(enabled);
	const [dayInput, setDayInput] = useState(String(scheduleDay ?? DEFAULT_DAY));
	const [timeInput, setTimeInput] = useState(scheduleTime ?? DEFAULT_TIME);

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
	useEffect(() => {
		if (!dirty) setDayInput(String(scheduleDay ?? DEFAULT_DAY));
	}, [scheduleDay, dirty]);
	useEffect(() => {
		if (!dirty) setTimeInput(scheduleTime ?? DEFAULT_TIME);
	}, [scheduleTime, dirty]);

	const channelInvalid = channelInput.length > 0 && !SLACK_CHANNEL_ID.test(channelInput);
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
			setDirty(false);
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
				toast.success("Test message posted to Slack");
			} else {
				toast.error(`Slack rejected the test message: ${data.slackError ?? "unknown error"}`);
			}
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
			// type === "LINKED" — no OAuth needed (e.g. PAT flow). Slack never reaches here today.
			toast.success("Slack workspace already linked");
			onSaved();
		},
		onError: (e) => {
			toast.error("Could not start Slack OAuth", {
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
									<CheckIcon className="size-4 text-green-600" />
									<span>Slack workspace connected</span>
								</div>

								<div className="flex items-center justify-between gap-4">
									<div>
										<Label htmlFor="slack-enabled" className="font-medium">
											Send weekly digest
										</Label>
										<p className="text-xs text-muted-foreground">
											Posts on the schedule below; the leaderboard cycle ends at the same moment.
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

								<div className="grid grid-cols-2 gap-4">
									<div className="space-y-2">
										<Label htmlFor="slack-day">Day</Label>
										<Select
											items={DAYS}
											value={dayInput}
											onValueChange={(value) => {
												if (value) {
													setDayInput(value);
													setDirty(true);
												}
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
									</div>
									<div className="space-y-2">
										<Label htmlFor="slack-time">Time (24h)</Label>
										<Input
											id="slack-time"
											type="time"
											value={timeInput}
											onChange={(e) => {
												setTimeInput(e.target.value);
												setDirty(true);
											}}
											aria-invalid={timeInvalid}
										/>
										{timeInvalid && (
											<p className="text-xs text-destructive">Time must be in HH:mm format.</p>
										)}
									</div>
								</div>
								<p className="text-xs text-muted-foreground">
									When the weekly leaderboard cycle ends and the digest is posted (workspace
									timezone).
								</p>

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
										onClick={() => save.mutate()}
										disabled={save.isPending || channelInvalid || timeInvalid}
									>
										{save.isPending ? "Saving…" : "Save"}
									</Button>
									<Button
										variant="outline"
										onClick={() => test.mutate({ path: { workspaceSlug } })}
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
