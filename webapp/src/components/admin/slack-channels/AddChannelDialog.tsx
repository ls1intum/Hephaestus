import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogClose,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

// Client-side format hint only — the server re-validates. Mirrors AdminSlackNotificationSettings.
const SLACK_CHANNEL_ID = /^[CGD][A-Z0-9]{8,}$/;

export interface AddChannelDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	/** Register the channel. Resolves on success (dialog closes), rejects to keep it open. */
	onSubmit: (input: { slackChannelId: string; channelName?: string }) => Promise<void> | void;
}

/**
 * Free-text add-a-channel form. No discovery endpoint exists, so admins paste the raw
 * channel id (same pattern as the notifications card). Remounted per open via a `key` in
 * the parent, so its inputs always start blank without a prop→state effect.
 */
export function AddChannelDialog({ open, onOpenChange, onSubmit }: AddChannelDialogProps) {
	const [channelId, setChannelId] = useState("");
	const [channelName, setChannelName] = useState("");
	const [submitting, setSubmitting] = useState(false);

	const idInvalid = channelId.length > 0 && !SLACK_CHANNEL_ID.test(channelId);
	const canSubmit = SLACK_CHANNEL_ID.test(channelId) && !submitting;

	async function submit() {
		if (!canSubmit) return;
		setSubmitting(true);
		try {
			await onSubmit({
				slackChannelId: channelId,
				channelName: channelName.trim().length > 0 ? channelName.trim() : undefined,
			});
			onOpenChange(false);
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent>
				<DialogHeader>
					<DialogTitle>Add a channel to monitor</DialogTitle>
					<DialogDescription>
						Paste the channel's Slack id. It lands as <strong>Not started</strong> — nothing is read
						until you activate monitoring.
					</DialogDescription>
				</DialogHeader>

				<FieldGroup>
					<Field data-invalid={idInvalid}>
						<FieldLabel htmlFor="add-slack-channel-id">Channel ID</FieldLabel>
						<Input
							id="add-slack-channel-id"
							value={channelId}
							disabled={submitting}
							onChange={(e) => setChannelId(e.target.value.trim())}
							placeholder="C0974LJBPBK"
							autoComplete="off"
							aria-invalid={idInvalid}
						/>
						<FieldDescription>
							Right-click the channel in Slack → <em>View channel details</em> → copy the ID at the
							bottom.
						</FieldDescription>
						{idInvalid && (
							<FieldError>
								Channel IDs start with C / G / D followed by 8+ alphanumerics.
							</FieldError>
						)}
					</Field>

					<Field>
						<FieldLabel htmlFor="add-slack-channel-name">Channel name (optional)</FieldLabel>
						<Input
							id="add-slack-channel-name"
							value={channelName}
							disabled={submitting}
							onChange={(e) => setChannelName(e.target.value)}
							placeholder="e.g. team-standup"
							autoComplete="off"
						/>
						<FieldDescription>Shown in this table so the id stays human-readable.</FieldDescription>
					</Field>
				</FieldGroup>

				<DialogFooter>
					<DialogClose render={<Button variant="outline" disabled={submitting} />}>
						Cancel
					</DialogClose>
					<Button onClick={submit} disabled={!canSubmit}>
						{submitting ? "Adding…" : "Add channel"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
