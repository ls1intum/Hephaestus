import { useQuery } from "@tanstack/react-query";
import { LibraryIcon, LockIcon } from "lucide-react";
import { useState } from "react";
import { listOutlineCollectionCandidatesOptions } from "@/api/@tanstack/react-query.gen";
import type { OutlineCollectionCandidate } from "@/api/types.gen";
import { OutlineCollectionIcon } from "@/components/admin/outline/OutlineCollectionIcon";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Command,
	CommandEmpty,
	CommandGroup,
	CommandInput,
	CommandItem,
	CommandList,
} from "@/components/ui/command";
import {
	Dialog,
	DialogClose,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { problemDetailOf } from "@/lib/problem-detail";

export interface AddCollectionDialogProps {
	workspaceSlug: string;
	open: boolean;
	onOpenChange: (open: boolean) => void;
	/** Register one collection; a rejection stops the sequential run and keeps the dialog open with the failure inline. */
	onRegister: (input: { collectionId: string }) => Promise<void> | void;
}

/**
 * Searchable picker for the Outline collections the API token can see. The candidates call is a live
 * proxy to Outline and doubles as the connectivity probe, so it stays lazy (only while the dialog is
 * open) and its 502/503 surfaces inline instead of a silent empty list.
 */
export function AddCollectionDialog({
	workspaceSlug,
	open,
	onOpenChange,
	onRegister,
}: AddCollectionDialogProps) {
	const [selectedIds, setSelectedIds] = useState<readonly string[]>([]);
	const [submitting, setSubmitting] = useState(false);
	const [submitError, setSubmitError] = useState<string | null>(null);
	/** How far the sequential registration run has got — drives the live progress line. */
	const [registered, setRegistered] = useState(0);

	const {
		data: candidates,
		isLoading,
		error,
		refetch,
	} = useQuery({
		...listOutlineCollectionCandidatesOptions({ path: { workspaceSlug } }),
		// Re-probe on every open (staleTime 0); no retry so a connectivity failure surfaces immediately.
		enabled: open,
		staleTime: 0,
		retry: false,
	});

	const all = candidates ?? [];
	const selectable = all.filter((candidate) => !candidate.alreadyMirrored);
	const canSubmit = selectedIds.length > 0 && !submitting;

	// Reset the form state on close instead of remounting the dialog via a changing `key` —
	// that kills the close animation. The next open always starts from a clean slate.
	function handleOpenChange(next: boolean) {
		if (!next) {
			setSelectedIds([]);
			setSubmitting(false);
			setSubmitError(null);
			setRegistered(0);
		}
		onOpenChange(next);
	}

	function toggle(candidate: OutlineCollectionCandidate) {
		if (candidate.alreadyMirrored || submitting) return;
		setSelectedIds((current) =>
			current.includes(candidate.collectionId)
				? current.filter((id) => id !== candidate.collectionId)
				: [...current, candidate.collectionId],
		);
	}

	async function submit() {
		if (!canSubmit) return;
		setSubmitting(true);
		setSubmitError(null);
		setRegistered(0);
		// Register sequentially: the server verifies each id against a live collections.list and
		// kicks a targeted sync per registration, so a burst of parallel posts buys nothing.
		let remaining = [...selectedIds];
		try {
			for (const collectionId of selectedIds) {
				await onRegister({ collectionId });
				remaining = remaining.filter((id) => id !== collectionId);
				setRegistered((done) => done + 1);
			}
			handleOpenChange(false);
		} catch (e) {
			// Keep the dialog open with the already-registered ids cleared so a retry only
			// re-submits what actually failed. The mutation's own onError surfaced the toast;
			// this inline copy anchors the failure to the dialog.
			setSelectedIds(remaining);
			setSubmitError(problemDetailOf(e));
		} finally {
			setSubmitting(false);
		}
	}

	const total = selectedIds.length;

	return (
		<Dialog open={open} onOpenChange={handleOpenChange}>
			<DialogContent className="sm:max-w-lg">
				<DialogHeader>
					<DialogTitle>Add collections to mirror</DialogTitle>
					<DialogDescription>
						Pick the Outline collections whose documents Hephaestus should mirror. Only the
						collections you select here are read.
					</DialogDescription>
				</DialogHeader>

				<form
					onSubmit={(e) => {
						e.preventDefault();
						submit();
					}}
				>
					{isLoading ? (
						<div className="space-y-2">
							<Skeleton className="h-9 w-full" />
							<Skeleton className="h-9 w-full" />
							<Skeleton className="h-9 w-full" />
						</div>
					) : error ? (
						<QueryErrorAlert
							error={error}
							title="Could not reach Outline"
							onRetry={() => {
								refetch();
							}}
						/>
					) : all.length === 0 ? (
						<Empty className="border">
							<EmptyHeader>
								<EmptyMedia variant="icon">
									<LockIcon />
								</EmptyMedia>
								<EmptyTitle>This token cannot see any collections</EmptyTitle>
								<EmptyDescription>
									Outline only returns the collections its API key's user is a member of. In
									Outline, open the collection, choose <strong>Members</strong>, and add the bot
									user that owns this key — then reopen this dialog.
								</EmptyDescription>
							</EmptyHeader>
						</Empty>
					) : selectable.length === 0 ? (
						<Empty className="border">
							<EmptyHeader>
								<EmptyMedia variant="icon">
									<LibraryIcon />
								</EmptyMedia>
								<EmptyTitle>Every visible collection is already mirrored</EmptyTitle>
								<EmptyDescription>
									Grant the bot user access to another collection in Outline to mirror more.
								</EmptyDescription>
							</EmptyHeader>
						</Empty>
					) : (
						// cmdk renders its own visually-hidden <label> and points the input's aria-labelledby
						// at it, so the accessible name has to come from `label`, not aria-label.
						<Command className="rounded-lg border" label="Search Outline collections">
							<CommandInput placeholder="Search collections…" disabled={submitting} />
							<CommandList>
								<CommandEmpty>No collections match your search.</CommandEmpty>
								<CommandGroup>
									{all.map((candidate) => {
										const label = candidate.name ?? candidate.collectionId;
										const checked =
											candidate.alreadyMirrored || selectedIds.includes(candidate.collectionId);
										return (
											<CommandItem
												key={candidate.collectionId}
												value={`${label} ${candidate.urlId ?? ""} ${candidate.collectionId}`}
												disabled={candidate.alreadyMirrored || submitting}
												onSelect={() => toggle(candidate)}
											>
												{/* The command item owns the toggle (click AND keyboard Enter land there),
												so the checkbox is read-only: an interactive Base UI checkbox re-dispatches
												its click on a hidden input, which bubbles back into the item and would
												toggle the selection a second time. Out of the tab order for the same
												reason — the roving focus lives on the list. */}
												<Checkbox
													checked={checked}
													readOnly
													disabled={candidate.alreadyMirrored || submitting}
													tabIndex={-1}
													aria-label={label}
												/>
												<OutlineCollectionIcon icon={candidate.icon} color={candidate.color} />
												<span className="min-w-0 flex-1">
													<span className="block truncate text-sm font-medium">{label}</span>
													{candidate.urlId && (
														<span className="text-muted-foreground block truncate font-mono text-xs">
															{candidate.urlId}
														</span>
													)}
												</span>
												{candidate.alreadyMirrored && (
													<Badge variant="outline">Already mirrored</Badge>
												)}
											</CommandItem>
										);
									})}
								</CommandGroup>
							</CommandList>
						</Command>
					)}

					{/* One live region for the whole run: progress while it advances, the failure when it
					stops. Screen-reader users hear "Adding 2 of 5…" without the button label thrashing. */}
					<div aria-live="polite" className="mt-3 min-h-5 text-sm">
						{submitting && total > 0 && (
							<p className="text-muted-foreground">
								Adding {Math.min(registered + 1, total)} of {total}…
							</p>
						)}
						{!submitting && submitError && <p className="text-destructive">{submitError}</p>}
					</div>

					<DialogFooter className="mt-3">
						<DialogClose render={<Button type="button" variant="outline" disabled={submitting} />}>
							Cancel
						</DialogClose>
						<Button type="submit" disabled={!canSubmit}>
							{submitting && <Spinner />}
							{submitting
								? "Adding…"
								: `Add ${total > 0 ? `${total} ` : ""}collection${total === 1 ? "" : "s"}`}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}
