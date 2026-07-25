import { BudgetAmountDialog } from "./BudgetAmountDialog";
import type { Fx } from "./fx";

export interface SetByoBudgetDialogProps {
	open: boolean;
	/** The cap in force today, in USD; `null`/`undefined` means the workspace is uncapped. */
	currentCapUsd?: number | null;
	/** The month's display-currency rate, so the field hints in the same currency as the page. */
	fx?: Fx;
	isPending: boolean;
	/** A server-side rejection, rendered as a field error instead of a toast. */
	serverError?: string | null;
	onOpenChange: (open: boolean) => void;
	/** `null` removes the cap; a number (USD, >= 0, 2 decimals) sets it. */
	onSubmit: (monthlyByoLlmBudgetUsd: number | null) => void;
}

/**
 * Workspace-admin dialog for the cap on spend through the workspace's *own* provider — its own
 * money, so unlike the shared-model budget this one is theirs to set, change, and remove. A cap of
 * $0 pauses work on their provider immediately; it never affects work on shared models, which the
 * host pays for and caps separately.
 */
export function SetByoBudgetDialog({
	open,
	currentCapUsd,
	fx,
	isPending,
	serverError,
	onOpenChange,
	onSubmit,
}: SetByoBudgetDialogProps) {
	return (
		<BudgetAmountDialog
			open={open}
			title="Set your provider cap"
			description="What this workspace can spend on its own provider each month. At the cap, work on your provider pauses until the month resets. $0 pauses now."
			fieldLabel="Monthly cap (USD)"
			fieldDescription="Applies only to spend on your own provider. Shared models are not affected."
			currentValueUsd={currentCapUsd ?? null}
			fx={fx}
			isPending={isPending}
			serverError={serverError}
			submitLabel="Save cap"
			removeLabel="Remove cap"
			onOpenChange={onOpenChange}
			onSubmit={onSubmit}
		/>
	);
}
