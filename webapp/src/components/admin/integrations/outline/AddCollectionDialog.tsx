import { useQuery } from "@tanstack/react-query";
import { LibraryIcon, LockIcon } from "lucide-react";
import { useRef, useState } from "react";
import { listOutlineCollectionCandidatesOptions } from "@/api/@tanstack/react-query.gen";
import type { OutlineCollectionCandidate } from "@/api/types.gen";
import { OutlineCollectionIcon } from "@/components/admin/integrations/outline/OutlineCollectionIcon";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Combobox,
	ComboboxEmpty,
	ComboboxItem,
	ComboboxList,
	ComboboxSearchInput,
	useComboboxFilter,
} from "@/components/ui/combobox";
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

/** Outline lets a collection go unnamed; the id is then the only honest thing to call it. */
function labelOf(candidate: OutlineCollectionCandidate) {
	return candidate.name ?? candidate.collectionId;
}

/** Search matches the name, the short url id and the raw id, so any of them finds the row. */
function searchTextOf(candidate: OutlineCollectionCandidate) {
	return `${labelOf(candidate)} ${candidate.urlId ?? ""} ${candidate.collectionId}`;
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
	const { contains } = useComboboxFilter({ sensitivity: "base" });
	// The dialog opens on a loading skeleton, so Base UI's default initial focus resolves (at open,
	// before the picker exists) to the first tabbable control — Cancel — and applies it a frame
	// later, i.e. *after* the reader has focused the search field. That steals focus back to Cancel,
	// and because the inline combobox drops its highlight on blur, keyboard nav dies. We therefore
	// refuse the dialog's initial focus while the input is absent (returning `false` schedules
	// nothing) and hand focus to the search field ourselves via `autoFocus` once it mounts.
	const comboboxRef = useRef<HTMLDivElement>(null);
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
	// The ids stay the source of truth (they are what `submit` posts); the combobox works in
	// candidate objects, so the selection is projected back and forth at this boundary.
	const selectedCandidates = all.filter((candidate) =>
		selectedIds.includes(candidate.collectionId),
	);

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
			<DialogContent
				className="sm:max-w-lg"
				// Focus the search field if it already exists; otherwise refuse to move focus (`false`)
				// rather than falling back to the first tabbable control, which would steal focus to
				// Cancel a frame later. `autoFocus` on the input covers the common load-after-open case.
				initialFocus={() =>
					comboboxRef.current?.querySelector<HTMLElement>('input[role="combobox"]') ?? false
				}
			>
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
						// `inline` renders the list as part of the dialog body rather than in a popup over
						// it — Base UI's dedicated mode for this. It forces the list open (no trigger to
						// reopen from) and, crucially, anchors the floating context to the enclosing
						// `[role="dialog"]`, which is what makes the roving highlight, `aria-activedescendant`
						// and `Combobox.Empty` work without a Positioner/Popup.
						<Combobox
							multiple
							inline
							items={all}
							value={selectedCandidates}
							onValueChange={(next) => setSelectedIds(next.map((c) => c.collectionId))}
							filter={(candidate, query) => contains(candidate, query, searchTextOf)}
							itemToStringLabel={labelOf}
						>
							<div ref={comboboxRef} className="rounded-lg border">
								{/* The picker mounts only after its async candidates load, so this is the first
								chance to focus it; the dialog itself declines initial focus (see initialFocus
								above) to avoid a focus fight that would blur the input and drop the highlight. */}
								<ComboboxSearchInput
									autoFocus
									placeholder="Search collections…"
									disabled={submitting}
									aria-label="Search Outline collections"
								/>
								<ComboboxEmpty>No collections match your search.</ComboboxEmpty>
								<ComboboxList>
									{(candidate: OutlineCollectionCandidate) => {
										const label = labelOf(candidate);
										const checked =
											candidate.alreadyMirrored || selectedIds.includes(candidate.collectionId);
										return (
											<ComboboxItem
												key={candidate.collectionId}
												value={candidate}
												disabled={candidate.alreadyMirrored || submitting}
												className="pr-2"
											>
												{/* The option owns the toggle (click AND keyboard Enter land there), so the
												checkbox is a read-only mirror of the option's selected state: an interactive
												Base UI checkbox re-dispatches its click on a hidden input, which would bubble
												back into the option and toggle a second time. `pointer-events-none` keeps
												every click on the option; out of the tab order because the roving highlight
												lives on the input. */}
												<Checkbox
													checked={checked}
													readOnly
													disabled={candidate.alreadyMirrored || submitting}
													tabIndex={-1}
													aria-label={label}
													className="pointer-events-none"
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
											</ComboboxItem>
										);
									}}
								</ComboboxList>
							</div>
						</Combobox>
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
