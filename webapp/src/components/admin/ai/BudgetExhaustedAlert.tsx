import { TriangleAlert } from "lucide-react";
import type { WorkspaceLlmUsageReport } from "@/api";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

interface BudgetExhaustedAlertProps {
	/**
	 * Whose cap tripped. `"shared"` is the budget the host sets and funds — the workspace admin
	 * can't lift it, only move work off it. `"own"` is the workspace's own cap on its own provider,
	 * which they can raise or remove themselves.
	 */
	scope: "shared" | "own";
	/** The month's verdict for that cap — selects between "reached" and "can't be checked" copy. */
	verdict?: WorkspaceLlmUsageReport["byoBudgetVerdict"];
}

/**
 * Compact budget-paused banner for surfaces outside the usage page. The usage page
 * (`AdminLlmUsagePage`) is where each cap is explained in full, but nothing else in the product
 * said why practice detection or the mentor stopped responding — this fills that gap on the AI
 * setup page (`AgentBindingsPage`), where the affected per-purpose bindings live.
 *
 * <p>The two caps pause independently, so this is rendered once per paused side and never merges
 * them: an exhausted shared-model budget leaves work on the workspace's own provider running, and
 * saying otherwise would send the admin to the wrong person.
 */
export function BudgetExhaustedAlert({ scope, verdict = "EXHAUSTED" }: BudgetExhaustedAlertProps) {
	const unpriced = verdict === "UNVERIFIABLE";
	const own = scope === "own";
	return (
		<Alert variant={own ? "destructive" : "warning"}>
			<TriangleAlert aria-hidden />
			<AlertTitle>
				{own
					? unpriced
						? "Your cap can't be enforced"
						: "Your monthly cap is reached"
					: unpriced
						? "Shared-model spend can't be verified"
						: "Shared-model budget reached"}
			</AlertTitle>
			<AlertDescription>
				{own
					? unpriced
						? "Some calls on your own models have no price set, so spend can't be checked against your cap — work on your own provider is paused. Add a price on this page to resume, or remove the cap under Usage."
						: "Work on your own provider is paused until next month, or until you raise or remove your cap under Usage."
					: unpriced
						? "Some shared-model calls have no price set, so spend can't be checked against your host's budget — work on shared models is paused. Only your host can price a shared model. Work on your own connected provider is not affected."
						: "Work on shared models is paused until next month or until your host raises the budget. Work on your own connected provider is not affected — you can switch a purpose to it below."}
			</AlertDescription>
		</Alert>
	);
}
