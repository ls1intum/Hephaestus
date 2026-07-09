import { useState } from "react";
import type { SlackChannelCandidate } from "@/components/admin/AdminSlackChannelsSettings";
import { Badge } from "@/components/ui/badge";
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
import { parseSlackChannelReference } from "@/lib/slack-channel-reference";
import { SlackChannelPicker } from "./SlackChannelPicker";

export interface AddChannelDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	candidates?: SlackChannelCandidate[];
	/** Register the channel. Resolves on success (dialog closes), rejects to keep it open. */
	onSubmit: (input: { slackChannelId: string; channelName?: string }) => Promise<void> | void;
}

export function AddChannelDialog({
	open,
	onOpenChange,
	candidates = [],
	onSubmit,
}: AddChannelDialogProps) {
	const [channelReference, setChannelReference] = useState("");
	const [channelName, setChannelName] = useState("");
	const [selectedCandidate, setSelectedCandidate] = useState<SlackChannelCandidate | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const parsedReference = parseSlackChannelReference(channelReference);
	const idInvalid = channelReference.trim().length > 0 && parsedReference == null;
	const canSubmit = (selectedCandidate != null || parsedReference != null) && !submitting;

	// Reset the form fields on close instead of remounting the dialog via a changing `key` —
	// that killed the close animation. The next open (of the same, single Add-channel dialog
	// instance) always starts from a clean slate.
	function handleOpenChange(next: boolean) {
		if (!next) {
			setChannelReference("");
			setChannelName("");
			setSelectedCandidate(null);
			setSubmitting(false);
		}
		onOpenChange(next);
	}

	async function submit() {
		if (submitting || !canSubmit) return;
		const trimmedName = channelName.trim();
		const input = selectedCandidate
			? {
					slackChannelId: selectedCandidate.slackChannelId,
					channelName: selectedCandidate.channelName,
				}
			: parsedReference
				? {
						slackChannelId: parsedReference.channelId,
						channelName: trimmedName.length > 0 ? trimmedName : parsedReference.channelName,
					}
				: null;
		if (!input) return;

		setSubmitting(true);
		try {
			await onSubmit(input);
			handleOpenChange(false);
		} catch {
			// Rejection = keep the dialog open. The mutation's onError already surfaced the toast.
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Dialog open={open} onOpenChange={handleOpenChange}>
			<DialogContent className="sm:max-w-lg">
				<DialogHeader>
					<DialogTitle>Add a channel to monitor</DialogTitle>
					<DialogDescription>
						Choose a Slack channel. Hephaestus stores the stable channel ID. Nothing is read until
						you activate monitoring and the channel announcement is posted.
					</DialogDescription>
				</DialogHeader>

				<form
					onSubmit={(e) => {
						e.preventDefault();
						submit();
					}}
				>
					<FieldGroup>
						{candidates.length > 0 && (
							<Field>
								<FieldLabel>Available channels</FieldLabel>
								<SlackChannelPicker
									aria-label="Search available Slack channels"
									candidates={candidates}
									disabled={submitting}
									selectedChannelId={selectedCandidate?.slackChannelId}
									getDisabledReason={(candidate) =>
										candidate.archived
											? "Archived"
											: candidate.consentState === "ACTIVE"
												? "Already listed"
												: undefined
									}
									renderBadges={(candidate) =>
										candidate.consentState === "REVOKED" ? (
											<Badge variant="outline">Revoked</Badge>
										) : null
									}
									onSelect={(candidate) => {
										setSelectedCandidate(candidate);
										setChannelReference("");
										setChannelName("");
									}}
								/>
								<FieldDescription>
									Private channels appear here after someone invites Hephaestus to them in Slack.
								</FieldDescription>
							</Field>
						)}

						<Field data-invalid={idInvalid}>
							<FieldLabel htmlFor="add-slack-channel-id">Paste channel link or ID</FieldLabel>
							<Input
								id="add-slack-channel-id"
								value={channelReference}
								disabled={submitting}
								onChange={(e) => {
									setChannelReference(e.target.value);
									setSelectedCandidate(null);
								}}
								placeholder="https://…slack.com/archives/C0974LJBPBK"
								autoComplete="off"
								aria-invalid={idInvalid}
							/>
							<FieldDescription>
								Use this when the channel is not in the list yet. For private channels, invite
								Hephaestus in Slack first.
							</FieldDescription>
							{idInvalid && (
								<FieldError>Paste a Slack channel URL, mention, or C…/G… channel ID.</FieldError>
							)}
						</Field>

						{parsedReference && !selectedCandidate && (
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
							</Field>
						)}
					</FieldGroup>

					<DialogFooter className="mt-6">
						<DialogClose render={<Button type="button" variant="outline" disabled={submitting} />}>
							Cancel
						</DialogClose>
						<Button type="submit" disabled={!canSubmit}>
							{submitting ? "Adding…" : "Add channel"}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}
