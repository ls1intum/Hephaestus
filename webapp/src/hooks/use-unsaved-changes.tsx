import { useBlocker } from "@tanstack/react-router";
import { useState } from "react";
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

export interface UnsavedChangesGuard {
	/** Render this next to the form. It is inert until a navigation is actually blocked. */
	dialog: React.ReactNode;
	/**
	 * Hand this whatever the save dispatch returned. The guard stays down until a promise settles,
	 * so the caller's post-save navigation is not blocked by its own success, and comes back up if
	 * the save rejects.
	 *
	 * A caller whose `onSubmit` returns `void` cannot report failure, so it gets no latch and relies
	 * on `disabled` while the mutation is pending. That is why this is a no-op rather than an error
	 * for a non-promise.
	 */
	track: (submission: unknown) => void;
}

export interface UseUnsavedChangesOptions {
	/** Whether the form holds work the reader has not saved. */
	isDirty: boolean;
	/** Suppress the guard entirely — while a mutation is in flight, or on a read-only form. */
	disabled?: boolean;
	/** Overrides the copy where "page" is the wrong word for the surface. */
	description?: string;
}

/**
 * One unsaved-changes guard for every form in the app.
 *
 * There were two, and they had drifted: one compared with `fast-deep-equal` and the other with
 * `JSON.stringify` (which reports a difference for two equal objects whose keys were inserted in a
 * different order), and only one had the latch that keeps the guard down across a save. Both shipped
 * their own copy of the dialog.
 *
 * The latch is the subtle part. `isPending` drops before the caller navigates, so releasing on it
 * alone races the post-save navigation and prompts the reader to discard the work they just saved.
 *
 * Note what this guard assumes: that a blocked navigation would unmount the form. That is true of a
 * form which owns its route and false of one inside a search-param-driven overlay, where
 * `shouldBlockFn` would fire for navigations that leave the form mounted — offering to discard work
 * that then is not discarded. See the drawer rule in `webapp/AGENTS.md`.
 */
export function useUnsavedChanges({
	isDirty,
	disabled = false,
	description = "Your draft will be lost if you leave this page.",
}: UseUnsavedChangesOptions): UnsavedChangesGuard {
	const [saving, setSaving] = useState(false);
	const guarded = isDirty && !saving;
	const blocker = useBlocker({
		shouldBlockFn: () => guarded,
		enableBeforeUnload: guarded,
		disabled: !guarded || disabled,
		withResolver: true,
	});

	return {
		track: (submission) => {
			if (!(submission instanceof Promise)) return;
			setSaving(true);
			submission.catch(() => setSaving(false));
		},
		dialog: (
			<AlertDialog
				open={blocker.status === "blocked"}
				onOpenChange={(open, eventDetails) => {
					if (!open && eventDetails.reason === "escape-key") blocker.reset?.();
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Discard unsaved changes?</AlertDialogTitle>
						<AlertDialogDescription>{description}</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel onClick={blocker.reset}>Keep editing</AlertDialogCancel>
						<AlertDialogAction variant="destructive" onClick={blocker.proceed}>
							Discard changes
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		),
	};
}
