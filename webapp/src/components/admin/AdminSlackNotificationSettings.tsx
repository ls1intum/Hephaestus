import { useMutation } from "@tanstack/react-query";
import { CheckIcon, ExternalLinkIcon, LoaderIcon, SendIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
	initiateMutation,
	sendSlackTestMessageMutation,
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
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

export interface AdminSlackNotificationSettingsProps {
	workspaceSlug: string;
	hasSlackConnection: boolean;
	slackConnectionId?: number;
	channelId?: string;
	onSaved: () => void;
}

// Client-side format hint only — the server re-validates.
const SLACK_CHANNEL_ID = /^[CGD][A-Z0-9]{8,}$/;

export function AdminSlackNotificationSettings({
	workspaceSlug,
	hasSlackConnection,
	slackConnectionId,
	channelId,
	onSaved,
}: AdminSlackNotificationSettingsProps) {
	const [channelInput, setChannelInput] = useState(channelId ?? "");
	const [disconnectOpen, setDisconnectOpen] = useState(false);

	const channelInvalid = channelInput.length > 0 && !SLACK_CHANNEL_ID.test(channelInput);

	useEffect(() => {
		const result = window.sessionStorage.getItem("slack-connect-result");
		if (!result) return;

		const reason = window.sessionStorage.getItem("slack-connect-reason");
		window.sessionStorage.removeItem("slack-connect-result");
		window.sessionStorage.removeItem("slack-connect-reason");

		if (result === "success") {
			toast.success("Slack connected");
			onSaved();
			return;
		}

		toast.error("Slack connection failed", {
			description: reason ?? undefined,
		});
	}, [onSaved]);

	const test = useMutation({
		...sendSlackTestMessageMutation(),
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
				<h2 className="text-lg font-semibold mb-4">Slack connection</h2>
				<Card>
					<CardContent className="space-y-4">
						<p className="text-sm text-muted-foreground">
							Connect a Slack workspace so Hephaestus can post to it. The bot is installed once per
							workspace via OAuth.
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
									<CheckIcon className="size-4 text-provider-done-foreground" />
									<span>Slack workspace connected</span>
								</div>

								<Field data-invalid={channelInvalid}>
									<FieldLabel htmlFor="slack-channel">
										Channel ID for this test message (optional)
									</FieldLabel>
									<Input
										id="slack-channel"
										value={channelInput}
										onChange={(e) => setChannelInput(e.target.value.trim())}
										placeholder="C0974LJBPBK"
										autoComplete="off"
										aria-invalid={channelInvalid}
									/>
									<FieldDescription>
										Leave blank to use the server-configured channel, or paste a channel ID from
										Slack. The bot must already be a member (or the channel must be public).
									</FieldDescription>
									{channelInvalid && (
										<FieldError>
											Channel IDs start with C / G / D followed by 8+ alphanumerics.
										</FieldError>
									)}
								</Field>

								<div className="flex gap-2 pt-2">
									<Button
										variant="outline"
										onClick={() =>
											test.mutate({
												path: { workspaceSlug },
												body: { channelId: channelInput || undefined },
											})
										}
										disabled={test.isPending || channelInvalid}
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
							The bot will be uninstalled from this workspace. You can reconnect later, but you will
							need to re-authorize via OAuth.
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
