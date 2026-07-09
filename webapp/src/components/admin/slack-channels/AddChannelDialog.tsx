import { LockIcon } from "lucide-react";
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
	const idInvalid = channelReference.length > 0 && parsedReference == null;
	const canSubmit = (selectedCandidate != null || parsedReference != null) && !submitting;

	async function submit() {
		if (submitting) return;
		const input = selectedCandidate
			? {
					slackChannelId: selectedCandidate.slackChannelId,
					channelName: selectedCandidate.channelName,
				}
			: parsedReference
				? {
						slackChannelId: parsedReference.channelId,
						channelName:
							channelName.trim().length > 0 ? channelName.trim() : parsedReference.channelName,
					}
				: null;
		if (!input) return;

		setSubmitting(true);
		try {
			await onSubmit(input);
			onOpenChange(false);
		} catch {
			// Rejection = keep the dialog open. The mutation's onError already surfaced the toast.
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-lg">
				<DialogHeader>
					<DialogTitle>Add a channel to monitor</DialogTitle>
					<DialogDescription>
						Choose a Slack channel. Hephaestus stores the stable channel ID. Nothing is read until
						you activate monitoring and the channel announcement is posted.
					</DialogDescription>
				</DialogHeader>

				<FieldGroup>
					{candidates.length > 0 && (
						<Field>
							<FieldLabel>Available channels</FieldLabel>
							<div className="max-h-64 space-y-2 overflow-y-auto rounded-lg border p-2">
								{candidates.map((candidate) => {
									const disabled = candidate.archived || candidate.consentState === "ACTIVE";
									const selected = selectedCandidate?.slackChannelId === candidate.slackChannelId;
									return (
										<Button
											key={candidate.slackChannelId}
											type="button"
											variant={selected ? "secondary" : "ghost"}
											className="h-auto w-full justify-start gap-3 px-3 py-2 text-left"
											disabled={disabled || submitting}
											aria-pressed={selected}
											onClick={() => {
												setSelectedCandidate(candidate);
												setChannelReference("");
												setChannelName("");
											}}
										>
											<div className="min-w-0 flex-1">
												<div className="flex flex-wrap items-center gap-2">
													<span className="truncate font-medium">#{candidate.channelName}</span>
													{candidate.privateChannel && (
														<LockIcon className="size-3.5" aria-label="Private" />
													)}
													{candidate.consentState && candidate.consentState !== "REVOKED" && (
														<Badge variant="secondary">Already listed</Badge>
													)}
													{candidate.consentState === "REVOKED" && (
														<Badge variant="outline">Revoked</Badge>
													)}
													{candidate.archived && <Badge variant="outline">Archived</Badge>}
												</div>
												<div className="text-muted-foreground font-mono text-xs">
													{candidate.slackChannelId}
												</div>
											</div>
										</Button>
									);
								})}
							</div>
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
								setChannelReference(e.target.value.trim());
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
