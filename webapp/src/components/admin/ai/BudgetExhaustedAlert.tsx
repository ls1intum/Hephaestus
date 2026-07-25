import { TriangleAlert } from "lucide-react";
import type { WorkspaceLlmUsageReport } from "@/api";
import { budgetResetDayLabel, currentMonthUtc } from "@/components/admin/usage/usageUtils";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

interface BudgetExhaustedAlertProps {
	/**
	 * Whose cap tripped. `"shared"` is the shared-model budget the host sets and pays for — the
	 * workspace admin can't lift it, only move work off it. `"own"` is the workspace's own provider
	 * cap, which they can raise or remove themselves.
	 */
	scope: "shared" | "own";
	/** The month's verdict for that cap — selects between "reached" and "can't be checked" copy. */
	verdict?: WorkspaceLlmUsageReport["byoBudgetVerdict"];
	/**
	 * ISO `yyyy-MM` month the pause belongs to. Defaults to the current UTC month, which is the only
	 * month a live pause can be in — it exists so the banner can name the day the pause lifts by
	 * itself instead of gesturing at "next month".
	 */
	month?: string;
}

/**
 * Compact paused banner for surfaces outside the usage page. The usage page (`AdminLlmUsagePage`)
 * is where each cap is explained in full, but nothing else in the product said why practice
 * detection or the mentor stopped responding — this fills that gap on the AI models page
 * (`AgentBindingsPage`), where the affected per-purpose bindings live.
 *
 * <p>The two caps pause independently, so this is rendered once per paused side and never merges
 * them: a spent shared-model budget leaves work on the workspace's own provider running, and saying
 * otherwise would send the admin to the wrong person.
 */
export function BudgetExhaustedAlert({
	scope,
	verdict = "EXHAUSTED",
	month = currentMonthUtc(),
}: BudgetExhaustedAlertProps) {
	const noPriceSet = verdict === "UNVERIFIABLE";
	const own = scope === "own";
	const resetDay = budgetResetDayLabel(month);
	return (
		<Alert variant={own ? "destructive" : "warning"}>
			<TriangleAlert aria-hidden />
			<AlertTitle>
				{own
					? noPriceSet
						? "Your cap can't be enforced"
						: "Your provider cap is reached"
					: noPriceSet
						? "Shared-model spend can't be verified"
						: "Shared-model budget reached"}
			</AlertTitle>
			<AlertDescription>
				{own
					? noPriceSet
						? "Some calls on your models have no price set, so spend can't be checked against your cap — work on your own provider is paused. Add a price on this page to resume, or remove the cap in AI usage."
						: `Work on your own provider is paused until ${resetDay} (UTC), or until you raise or remove your cap in AI usage.`
					: noPriceSet
						? "Some shared-model calls have no price set, so spend can't be checked against the shared-model budget — work on shared models is paused. Only your host can price a shared model. Work on your own provider is not affected."
						: `Work on shared models is paused until ${resetDay} (UTC) or until your host raises the budget. Work on your own provider is not affected — you can switch a purpose to it below.`}
			</AlertDescription>
		</Alert>
	);
}
