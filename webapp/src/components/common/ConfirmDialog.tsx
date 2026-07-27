import { type ReactNode, useState } from "react";
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

export interface ConfirmDialogProps<T> {
	/** The row awaiting confirmation; `null` closes the dialog. The dialog never owns this state. */
	subject: T | null;
	/** Named after the row, always: "Delete “GPT-4”?" is the whole reason this is a modal. */
	title: (subject: T) => ReactNode;
	/** What confirming does, and what it cannot undo. A function when it has to read the row. */
	description: ReactNode | ((subject: T) => ReactNode);
	/** The destructive verb, in the reader's words: "Delete", "Disconnect provider", "Turn off". */
	confirmLabel: string;
	/** Overridden where "Cancel" is not the opposite of the verb — "Keep active", say. */
	cancelLabel?: string;
	onConfirm: (subject: T) => void;
	/** Clears `subject`. Called on dismissal *and* right after `onConfirm`. */
	onClose: () => void;
}

/**
 * The shared confirm for destructive row actions.
 *
 * **Confirming closes the dialog, before the request it starts has settled** — the row that owns
 * the request reports the outcome instead. ADR 0027 has the derivation:
 * {@link https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0027-dialog-lifetime-and-where-a-write-outcome-lands.md}
 *
 * {@link AlertDialogAction} is a plain `Button`, not `AlertDialogPrimitive.Close`, so closing is the
 * consumer's job — the one detail each caller would otherwise have to remember for itself.
 */
export function ConfirmDialog<T>({
	subject,
	title,
	description,
	confirmLabel,
	cancelLabel = "Cancel",
	onConfirm,
	onClose,
}: ConfirmDialogProps<T>) {
	// The popup outlives `subject` by one exit animation. Holding the last row it described keeps the
	// title from blanking to `Delete “”?` on the way out — the standard derive-state-from-props form,
	// not an effect, so it is applied in the same render the new subject arrives in.
	const [shown, setShown] = useState<T | null>(subject);
	if (subject != null && subject !== shown) {
		setShown(subject);
	}

	return (
		<AlertDialog
			open={subject != null}
			onOpenChange={(open) => {
				if (!open) onClose();
			}}
		>
			<AlertDialogContent>
				{shown != null && (
					<>
						<AlertDialogHeader>
							<AlertDialogTitle>{title(shown)}</AlertDialogTitle>
							<AlertDialogDescription>
								{typeof description === "function" ? description(shown) : description}
							</AlertDialogDescription>
						</AlertDialogHeader>
						<AlertDialogFooter>
							<AlertDialogCancel>{cancelLabel}</AlertDialogCancel>
							<AlertDialogAction
								variant="destructive"
								onClick={() => {
									// `subject`, not `shown`: the popup is still on screen through its exit animation,
									// and confirming a row the caller has already let go of would act on the wrong one.
									if (subject == null) return;
									onConfirm(subject);
									onClose();
								}}
							>
								{confirmLabel}
							</AlertDialogAction>
						</AlertDialogFooter>
					</>
				)}
			</AlertDialogContent>
		</AlertDialog>
	);
}
