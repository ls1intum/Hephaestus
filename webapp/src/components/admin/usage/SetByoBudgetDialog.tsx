import { BudgetAmountDialog } from "./BudgetAmountDialog";

export interface SetByoBudgetDialogProps {
	open: boolean;
	/** The cap in force today, in USD; `null`/`undefined` means the workspace is uncapped. */
	currentCapUsd?: number | null;
	isPending: boolean;
	/** A server-side rejection, rendered as a field error instead of a toast. */
	serverError?: string | null;
	onOpenChange: (open: boolean) => void;
	/** `null` removes the cap; a number (USD, >= 0, 2 decimals) sets it. */
	onSubmit: (monthlyByoLlmBudgetUsd: number | null) => void;
}

/**
 * Workspace-admin dialog for the cap on the workspace's *own* provider spend — its own money, so
 * unlike the host's shared-model budget this one is theirs to set, change, and remove. A cap of $0
 * pauses own-provider work immediately; it never affects work on shared models, which the host
 * funds and caps separately.
 */
export function SetByoBudgetDialog({
	open,
	currentCapUsd,
	isPending,
	serverError,
	onOpenChange,
	onSubmit,
}: SetByoBudgetDialogProps) {
	return (
		<BudgetAmountDialog
			open={open}
			title={currentCapUsd != null ? "Change your monthly cap" : "Set your monthly cap"}
			description="Your monthly limit on what this workspace spends through its own connected provider. When the month's spend reaches it, work on your own provider pauses until the next UTC month — work on shared models is not affected. A cap of $0 pauses immediately."
			fieldLabel="Monthly cap (USD)"
			fieldDescription="Applies only to spend on your own provider. Leave the cap off to stay uncapped."
			currentValueUsd={currentCapUsd ?? null}
			isPending={isPending}
			serverError={serverError}
			submitLabel="Save cap"
			removeLabel="Remove cap"
			onOpenChange={onOpenChange}
			onSubmit={onSubmit}
		/>
	);
}
