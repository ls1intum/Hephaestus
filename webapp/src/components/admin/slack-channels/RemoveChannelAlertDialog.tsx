import { useState } from "react";
import type { SlackMonitoredChannel } from "@/api/types.gen";
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
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

export interface RemoveChannelAlertDialogProps {
	/** Non-null drives the open state and names the channel being erased. */
	channel: SlackMonitoredChannel | null;
	onOpenChange: (open: boolean) => void;
	/** Confirm removal. Resolves on success (dialog closes), rejects to keep it open. */
	onConfirm: (input: { slackChannelId: string; reason?: string }) => Promise<void> | void;
}

/**
 * Destructive confirmation for a terminal erase. The confirm action stays disabled until the admin
 * types the stable Slack channel ID.
 */
export function RemoveChannelAlertDialog({
	channel,
	onOpenChange,
	onConfirm,
}: RemoveChannelAlertDialogProps) {
	const [confirmText, setConfirmText] = useState("");
	const [reason, setReason] = useState("");
	const [submitting, setSubmitting] = useState(false);

	const label = channel ? (channel.channelName ?? channel.slackChannelId) : "";
	const channelId = channel?.slackChannelId ?? "";
	const matches = confirmText.trim() === channelId;

	async function confirm() {
		if (!channel || !matches) return;
		setSubmitting(true);
		try {
			await onConfirm({
				slackChannelId: channel.slackChannelId,
				reason: reason.trim().length > 0 ? reason.trim() : undefined,
			});
			onOpenChange(false);
		} catch {
			// Rejection = keep the dialog open. The mutation's onError already surfaced the
			// toast, so swallow here rather than let it escape as an unhandled rejection.
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<AlertDialog open={channel != null} onOpenChange={onOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>Remove #{label} and erase its data?</AlertDialogTitle>
					<AlertDialogDescription>
						This permanently deletes <strong>all messages collected</strong> from this channel and
						every practice observation derived from them. This cannot be undone.
					</AlertDialogDescription>
				</AlertDialogHeader>

				<div className="space-y-4">
					<Field>
						<FieldLabel htmlFor="remove-slack-confirm">
							Type the channel ID <span className="font-mono font-medium">{channelId}</span> to
							confirm
						</FieldLabel>
						<Input
							id="remove-slack-confirm"
							value={confirmText}
							disabled={submitting}
							onChange={(e) => setConfirmText(e.target.value)}
							autoComplete="off"
							autoCapitalize="off"
							spellCheck={false}
						/>
					</Field>

					<Field>
						<FieldLabel htmlFor="remove-slack-reason">Reason (optional)</FieldLabel>
						<Textarea
							id="remove-slack-reason"
							value={reason}
							disabled={submitting}
							onChange={(e) => setReason(e.target.value)}
							placeholder="Recorded in the immutable audit trail."
						/>
						<FieldDescription>Kept in the consent history for accountability.</FieldDescription>
					</Field>
				</div>

				<AlertDialogFooter>
					<AlertDialogCancel disabled={submitting}>Cancel</AlertDialogCancel>
					<AlertDialogAction
						variant="destructive"
						disabled={!matches || submitting}
						onClick={confirm}
					>
						{submitting ? "Removing…" : "Remove & erase"}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
}
