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
 * Instance-admin dialog to set or remove a workspace's monthly budget for spend the *host* pays
 * for (shared models). A cap of $0 pauses shared-model work immediately; removing the cap leaves
 * that spend uncapped. The workspace's own-provider spend is separate money and is never affected
 * — that cap is the workspace's own (`SetByoBudgetDialog`).
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
			title="Set monthly AI budget"
			description={
				workspace !== null ? (
					<>
						Shared-model budget for <strong>{workspace.displayName}</strong> (
						{workspace.workspaceSlug}). When the month's spend on shared models reaches the cap,
						work on them pauses until next month. A cap of $0 pauses immediately.
					</>
				) : null
			}
			fieldLabel="Monthly budget (USD)"
			fieldDescription="Spend above this amount pauses work on shared models until the next UTC month."
			currentValueUsd={workspace?.instanceMonthlyBudgetUsd ?? null}
			isPending={isPending}
			serverError={serverError}
			onOpenChange={onOpenChange}
			onSubmit={onSubmit}
		/>
	);
}
