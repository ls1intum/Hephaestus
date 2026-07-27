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
	title: (subject: T) => ReactNode;
	description: ReactNode | ((subject: T) => ReactNode);
	confirmLabel: string;
	cancelLabel?: string;
	onConfirm: (subject: T) => void;
	/** Clears `subject`. Called on dismissal *and* right after `onConfirm`. */
	onClose: () => void;
}

/**
 * Survives `subject` going `null` so a closing dialog can still render what it was describing —
 * derived during render rather than in an effect, so a new subject lands in the same commit.
 */
function useLastNonNull<T>(subject: T | null): T | null {
	const [shown, setShown] = useState<T | null>(subject);
	if (subject != null && subject !== shown) {
		setShown(subject);
	}
	return shown;
}

/**
 * The shared confirm for destructive row actions.
 *
 * **Confirming closes the dialog, before the request it starts has settled** — the row that owns
 * the request reports the outcome instead. ADR 0027 has the derivation:
 * {@link https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0027-dialog-lifetime-and-where-a-write-outcome-lands.md}
 *
 * {@link AlertDialogAction} is a plain `Button`, not the primitive's `Close`, so closing is this
 * component's job rather than something every caller has to remember.
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
	const shown = useLastNonNull(subject);

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
									// `subject`, not `shown`: `shown` outlives a row the caller has already let go of.
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
