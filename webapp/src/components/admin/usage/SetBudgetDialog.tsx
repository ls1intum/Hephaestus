import type { AdminWorkspaceLlmUsage } from "@/api/types.gen";
import { BudgetAmountDialog } from "./BudgetAmountDialog";
import type { Fx } from "./fx";

export interface SetBudgetDialogProps {
	/** The workspace whose cap is being edited; `null` keeps the dialog closed. */
	workspace: AdminWorkspaceLlmUsage | null;
	/**
	 * The month's display-currency rate, from the report envelope. Passed in rather than read off the
	 * row so the live estimate under the field uses the same rate as the table behind the dialog.
	 */
	fx?: Fx;
	isPending: boolean;
	/** A server-side rejection, rendered as a field error instead of a toast. */
	serverError?: string | null;
	onOpenChange: (open: boolean) => void;
	/** `null` removes the cap; a number (USD, >= 0, 2 decimals) sets it. */
	onSubmit: (monthlyLlmBudgetUsd: number | null) => void;
}

/**
 * Instance-admin dialog to set or remove a workspace's monthly shared-model budget — the spend the
 * *host* pays for. A budget of $0 pauses shared-model work immediately; removing it leaves that
 * spend uncapped. Spend on the workspace's own provider is separate money and is never affected —
 * that cap is the workspace's own (`SetOwnProviderBudgetDialog`).
 *
 * The field, validation, and error rendering come from `BudgetAmountDialog`; only the copy is here.
 */
export function SetBudgetDialog({
	workspace,
	fx,
	isPending,
	serverError,
	onOpenChange,
	onSubmit,
}: SetBudgetDialogProps) {
	return (
		<BudgetAmountDialog
			open={workspace !== null}
			// Keyed so the input state resets whenever a different workspace is edited.
			resetKey={workspace?.workspaceSlug}
			title="Set shared-model budget"
			description={
				workspace !== null ? (
					<>
						What <strong>{workspace.displayName}</strong> ({workspace.workspaceSlug}) can spend on
						shared models each month. When the budget is reached, shared-model work pauses until the
						month resets; the workspace's own provider is not affected. $0 pauses now.
					</>
				) : null
			}
			fieldLabel="Monthly budget (USD)"
			submitLabel="Save budget"
			// Otherwise this falls through to the shared dialog's "Remove cap" — and one click path
			// would name this number four different ways.
			removeLabel="Remove budget"
			currentValueUsd={workspace?.instanceMonthlyBudgetUsd ?? null}
			fx={fx}
			isPending={isPending}
			serverError={serverError}
			onOpenChange={onOpenChange}
			onSubmit={onSubmit}
		/>
	);
}
