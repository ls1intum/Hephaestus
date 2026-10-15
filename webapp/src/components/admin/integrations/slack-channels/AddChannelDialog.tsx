import { useState } from "react";

import type { SlackChannelCandidate } from "@/components/admin/integrations/WorkspaceSlackChannelsSettings";
import { QueryErrorAlert, type QueryErrorAlertProps } from "@/components/common/QueryErrorAlert";
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
import { Skeleton } from "@/components/ui/skeleton";
import { parseSlackChannelReference } from "@/lib/slack-channel-reference";
import { hasText } from "@/lib/text";

import { SlackChannelCombobox } from "./SlackChannelCombobox";
import { SlackChannelPasteField } from "./SlackChannelPasteField";

export interface AddChannelDialogProps {
	open: boolean;
	onOpenChange: (open: boolean) => void;
	candidates: readonly SlackChannelCandidate[];
	/** The channels Slack lists are still loading. */
	candidatesLoading?: boolean;
	/** The channels Slack lists could not be read. The picker says why; a pasted reference still works. */
	candidatesFailure?: Pick<QueryErrorAlertProps, "error" | "onRetry" | "pending">;
	onSubmit: (input: { slackChannelId: string; channelName?: string }) => Promise<void> | void;
}

function candidateDisabledReason(candidate: SlackChannelCandidate): string | undefined {
	if (candidate.archived === true) {
		return "Archived";
	}
	if (candidate.consentState === "ACTIVE") {
		return "Already listed";
	}
	return undefined;
}

export function AddChannelDialog({
	open,
	onOpenChange,
	candidates,
	candidatesLoading = false,
	candidatesFailure,
	onSubmit,
}: AddChannelDialogProps) {
	const [selectedId, setSelectedId] = useState<string | null>(null);
	const [channelReference, setChannelReference] = useState("");
	const [channelName, setChannelName] = useState("");
	const [pasteOpen, setPasteOpen] = useState(false);
	const [submitError, setSubmitError] = useState<string | null>(null);
	const [submitting, setSubmitting] = useState(false);

	const hasCandidates =
		candidatesFailure === undefined && !candidatesLoading && candidates.length > 0;
	// The pick is read from the current list, so a list that can no longer be read offers no pick.
	const selectedCandidate = hasCandidates
		? (candidates.find((c) => c.slackChannelId === selectedId) ?? null)
		: null;
	// Without a list to pick from, pasting is the way in, so it is shown rather than offered.
	const showPaste = pasteOpen || (!hasCandidates && !candidatesLoading);
	const parsedReference = parseSlackChannelReference(channelReference);
	const referenceInvalid = channelReference.trim().length > 0 && parsedReference == null;

	let resolved: { slackChannelId: string; channelName?: string } | null = null;
	if (selectedCandidate) {
		resolved = {
			slackChannelId: selectedCandidate.slackChannelId,
			channelName: selectedCandidate.channelName,
		};
	} else if (parsedReference) {
		resolved = {
			slackChannelId: parsedReference.channelId,
			channelName: channelName.trim().length > 0 ? channelName.trim() : parsedReference.channelName,
		};
	}

	function handleOpenChange(next: boolean) {
		if (!next) {
			setSelectedId(null);
			setChannelReference("");
			setChannelName("");
			setPasteOpen(false);
			setSubmitError(null);
			setSubmitting(false);
		}
		onOpenChange(next);
	}

	async function submit() {
		if (submitting) {
			return;
		}
		if (!resolved) {
			setSubmitError(
				hasCandidates
					? "Select a channel from the list, or paste a channel link or ID."
					: "Paste a Slack channel link or ID to add it.",
			);
			return;
		}

		setSubmitError(null);
		setSubmitting(true);
		try {
			await onSubmit(resolved);
			handleOpenChange(false);
		} catch {
			// Rejection = keep the dialog open. The mutation's onError already surfaced the
			// toast, so swallow here rather than let it escape as an unhandled rejection.
		}
		setSubmitting(false);
	}

	return (
		<Dialog
			open={open}
			onOpenChange={(next, details) => {
				// A pending Add owns the dialog until its response: closing now would clear the draft it
				// is sending, and its answer would then close whatever draft came next.
				if (!next && submitting) {
					details.cancel();
					return;
				}
				handleOpenChange(next);
			}}
		>
			<DialogContent className="sm:max-w-lg" showCloseButton={!submitting}>
				<DialogHeader>
					<DialogTitle>Add a channel to monitor</DialogTitle>
					<DialogDescription>
						Select a Slack channel. Hephaestus stores the stable channel ID. Nothing is read until
						you activate monitoring and the channel announcement is posted.
					</DialogDescription>
				</DialogHeader>

				<form
					onSubmit={(e) => {
						e.preventDefault();
						void submit();
					}}
				>
					<FieldGroup>
						{candidatesFailure !== undefined && (
							<QueryErrorAlert
								title="We could not load the channels from Slack"
								{...candidatesFailure}
							/>
						)}
						{candidatesFailure === undefined && candidatesLoading && (
							<Skeleton className="h-9 w-full" />
						)}
						{hasCandidates && (
							<Field data-invalid={submitError != null}>
								<FieldLabel htmlFor="add-slack-channel">Channel</FieldLabel>
								<SlackChannelCombobox
									id="add-slack-channel"
									aria-label="Search available Slack channels"
									candidates={candidates}
									disabled={submitting}
									invalid={submitError != null}
									selectedChannelId={
										selectedCandidate?.slackChannelId ?? parsedReference?.channelId
									}
									selectedChannelName={
										parsedReference ? channelName.trim() || undefined : undefined
									}
									getDisabledReason={candidateDisabledReason}
									renderBadges={(candidate) =>
										candidate.consentState === "REVOKED" ? (
											<Badge variant="outline">Revoked</Badge>
										) : null
									}
									onSelect={(candidate) => {
										setSelectedId(candidate.slackChannelId);
										setChannelReference("");
										setChannelName("");
										setSubmitError(null);
									}}
								/>
								<FieldDescription>
									Private channels appear here after someone invites Hephaestus to them in Slack.
								</FieldDescription>
								{hasText(submitError) && <FieldError>{submitError}</FieldError>}
							</Field>
						)}

						{showPaste ? (
							<SlackChannelPasteField
								id="add-slack-channel-id"
								value={channelReference}
								disabled={submitting}
								invalid={referenceInvalid}
								description={
									hasCandidates ? "For a channel that is not in the list yet." : undefined
								}
								onChange={(value) => {
									setChannelReference(value);
									setSelectedId(null);
									setSubmitError(null);
								}}
							/>
						) : (
							<Button
								type="button"
								variant="link"
								size="inline"
								className="w-fit text-sm"
								onClick={() => setPasteOpen(true)}
							>
								Paste a channel link or ID instead
							</Button>
						)}

						{!hasCandidates && hasText(submitError) && <FieldError>{submitError}</FieldError>}

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
								<FieldDescription>
									Shown in the table. Slack’s own name is used when you leave this blank.
								</FieldDescription>
							</Field>
						)}
					</FieldGroup>

					<DialogFooter className="mt-6">
						<DialogClose render={<Button type="button" variant="outline" disabled={submitting} />}>
							Cancel
						</DialogClose>
						<Button type="submit" disabled={submitting}>
							{submitting ? "Adding…" : "Add channel"}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}
