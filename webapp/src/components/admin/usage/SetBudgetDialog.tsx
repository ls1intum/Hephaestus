import type { AdminWorkspaceLlmUsage } from "@/api/types.gen";
import { BudgetAmountDialog } from "./BudgetAmountDialog";

export interface SetBudgetDialogProps {
	/** The workspace whose cap is being edited; `null` keeps the dialog closed. */
	workspace: AdminWorkspaceLlmUsage | null;
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
 * that cap is the workspace's own (`SetByoBudgetDialog`).
 *
 * The field, validation, and error rendering come from `BudgetAmountDialog`; only the copy is here.
 */
export function SetBudgetDialog({
	workspace,
	isPending,
	serverError,
	onOpenChange,
	onSubmit,
}: SetBudgetDialogProps) {
	return (
		<BudgetAmountDialog
			open={workspace !== null}
			// Keyed so the input state resets whenever a different workspace is edited.
			resetKey={workspace?.workspaceId}
			title="Set shared-model budget"
			description={
				workspace !== null ? (
					<>
						What <strong>{workspace.displayName}</strong> ({workspace.workspaceSlug}) can spend on
						shared models each month. At the cap, shared-model work pauses until the month resets;
						the workspace's own provider is not affected. $0 pauses now.
					</>
				) : null
			}
			fieldLabel="Monthly budget (USD)"
			fieldDescription="Reaching this amount pauses shared-model work until the month resets."
			submitLabel="Save budget"
			currentValueUsd={workspace?.instanceMonthlyBudgetUsd ?? null}
			// The rollup row carries the month's rate, so the live estimate under the field uses the
			// same rate as the figures the admin is looking at behind the dialog.
			fx={workspace?.fx}
			isPending={isPending}
			serverError={serverError}
			onOpenChange={onOpenChange}
			onSubmit={onSubmit}
		/>
	);
}
