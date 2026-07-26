import { BudgetAmountDialog } from "./BudgetAmountDialog";
import type { Fx } from "./fx";

export interface SetOwnProviderBudgetDialogProps {
	open: boolean;
	/** The cap in force today, in USD; `null`/`undefined` means the workspace is uncapped. */
	currentCapUsd?: number | null;
	fx?: Fx;
	/** Passed straight through to the shared editor; see its `isCurrentMonth`. */
	isCurrentMonth?: boolean;
	isPending: boolean;
	serverError?: string | null;
	onOpenChange: (open: boolean) => void;
	/** `null` removes the cap; a number (USD, >= 0, 2 decimals) sets it. */
	onSubmit: (monthlyBudgetUsd: number | null) => void;
}

/**
 * Workspace-admin dialog for the cap on spend through the workspace's *own* provider — its own
 * money, so unlike the shared-model budget this one is theirs to set and remove. $0 pauses work on
 * their provider immediately; shared models are capped separately by the host.
 */
export function SetOwnProviderBudgetDialog({
	open,
	currentCapUsd,
	fx,
	isCurrentMonth,
	isPending,
	serverError,
	onOpenChange,
	onSubmit,
}: SetOwnProviderBudgetDialogProps) {
	return (
		<BudgetAmountDialog
			open={open}
			title="Set your provider cap"
			description="What this workspace can spend on its own provider each month. At the cap, work on your provider pauses until the month resets. $0 pauses now."
			fieldLabel="Monthly cap (USD)"
			currentValueUsd={currentCapUsd ?? null}
			fx={fx}
			isCurrentMonth={isCurrentMonth}
			isPending={isPending}
			serverError={serverError}
			submitLabel="Save cap"
			removeLabel="Remove cap"
			onOpenChange={onOpenChange}
			onSubmit={onSubmit}
		/>
	);
}
