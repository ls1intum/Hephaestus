import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { listOutlineCollectionCandidatesOptions } from "@/api/@tanstack/react-query.gen";
import type { OutlineCollectionCandidate } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Dialog,
	DialogClose,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { problemDetailOf } from "@/lib/problem-detail";
import { cn } from "@/lib/utils";

export interface AddCollectionDialogProps {
	workspaceSlug: string;
	open: boolean;
	onOpenChange: (open: boolean) => void;
	/**
	 * Register one collection for mirroring. The dialog registers a multi-selection sequentially;
	 * a rejection stops the run and keeps the dialog open with the failure inline.
	 */
	onRegister: (input: { collectionId: string }) => Promise<void> | void;
}

/**
 * Picker for the Outline collections the API token can see. The candidates call is a live proxy
 * to Outline and doubles as the connectivity probe, so it runs lazily (only while the dialog is
 * open) and its 502/503 ProblemDetail surfaces inline instead of a silent empty list.
 * Already-mirrored collections stay visible but disabled+checked so the picker reflects reality.
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

	const selectable = (candidates ?? []).filter((candidate) => !candidate.alreadyMirrored);
	const canSubmit = selectedIds.length > 0 && !submitting;

	// Reset the form state on close instead of remounting the dialog via a changing `key` —
	// that kills the close animation. The next open always starts from a clean slate.
	function handleOpenChange(next: boolean) {
		if (!next) {
			setSelectedIds([]);
			setSubmitting(false);
			setSubmitError(null);
		}
		onOpenChange(next);
	}

	function toggle(candidate: OutlineCollectionCandidate, checked: boolean) {
		setSelectedIds((current) =>
			checked
				? [...current, candidate.collectionId]
				: current.filter((id) => id !== candidate.collectionId),
		);
	}

	async function submit() {
		if (!canSubmit) return;
		setSubmitting(true);
		setSubmitError(null);
		// Register sequentially: the server verifies each id against a live collections.list and
		// kicks a targeted sync per registration, so a burst of parallel posts buys nothing.
		let remaining = [...selectedIds];
		try {
			for (const collectionId of selectedIds) {
				await onRegister({ collectionId });
				remaining = remaining.filter((id) => id !== collectionId);
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
						<div className="space-y-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
							<p className="text-sm text-destructive" role="alert">
								Could not reach Outline: {problemDetailOf(error)}
							</p>
							<Button type="button" variant="outline" size="sm" onClick={() => refetch()}>
								Retry
							</Button>
						</div>
					) : (candidates?.length ?? 0) === 0 ? (
						<p className="text-muted-foreground text-sm">
							The API token cannot see any collections. Grant the bot user access to a collection in
							Outline first.
						</p>
					) : (
						<ul className="max-h-72 space-y-1 overflow-y-auto" aria-label="Available collections">
							{(candidates ?? []).map((candidate) => {
								const label = candidate.name ?? candidate.collectionId;
								const checkboxId = `add-outline-collection-${candidate.collectionId}`;
								return (
									<li key={candidate.collectionId}>
										<Label
											htmlFor={checkboxId}
											className={cn(
												"flex w-full items-center gap-3 rounded-md px-2 py-2 font-normal",
												candidate.alreadyMirrored
													? "cursor-default opacity-60"
													: "cursor-pointer hover:bg-accent",
											)}
										>
											<Checkbox
												id={checkboxId}
												checked={
													candidate.alreadyMirrored || selectedIds.includes(candidate.collectionId)
												}
												disabled={candidate.alreadyMirrored || submitting}
												onCheckedChange={(checked) => toggle(candidate, checked === true)}
											/>
											{candidate.icon ? (
												<span className="text-base leading-none" aria-hidden>
													{candidate.icon}
												</span>
											) : (
												<span
													className="size-2.5 shrink-0 rounded-full"
													style={{
														backgroundColor: candidate.color ?? "var(--muted-foreground)",
													}}
													aria-hidden
												/>
											)}
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
										</Label>
									</li>
								);
							})}
						</ul>
					)}

					{!isLoading && !error && selectable.length === 0 && (candidates?.length ?? 0) > 0 && (
						<p className="text-muted-foreground mt-2 text-sm">
							Every visible collection is already mirrored.
						</p>
					)}

					{submitError && (
						<p className="mt-3 text-sm text-destructive" role="alert">
							{submitError}
						</p>
					)}

					<DialogFooter className="mt-6">
						<DialogClose render={<Button type="button" variant="outline" disabled={submitting} />}>
							Cancel
						</DialogClose>
						<Button type="submit" disabled={!canSubmit}>
							{submitting
								? "Adding…"
								: `Add ${selectedIds.length > 0 ? `${selectedIds.length} ` : ""}collection${selectedIds.length === 1 ? "" : "s"}`}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}
