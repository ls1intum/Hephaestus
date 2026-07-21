import { TriangleAlert } from "lucide-react";
import type { WorkspaceLlmUsageReport } from "@/api";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

interface BudgetExhaustedAlertProps {
	/**
	 * The month's budget verdict — selects the copy. Defaults to "EXHAUSTED" (the cap-reached case)
	 * for backward compatibility with callers that only ever render this alert when they already know
	 * the cap was reached.
	 */
	verdict?: WorkspaceLlmUsageReport["verdict"];
}

/**
 * Compact budget-paused banner for surfaces outside the usage page. The usage page
 * (`AdminLlmUsagePage`) is where the verdict is explained in full, but nothing else in the product
 * said why practice detection or the mentor stopped responding — this fills that gap on the Models
 * tab (`AgentRuntimesPage`), where the affected `AgentConfigCard`s live. Copy matches the usage
 * page's own over-budget banner verbatim (#1368 glossary: "Budget reached" is never softened).
 *
 * <p>Rendered whenever the server reports `usagePaused` (#1368 fix wave) — not just on
 * `verdict=EXHAUSTED`. Under this server's BLOCK unpriced-usage policy, a `verdict=UNVERIFIABLE`
 * month also pauses new work; the webapp has no way to know the instance's policy itself, so it
 * trusts the server's `usagePaused` flag and only picks a wording variant here.
 */
export function BudgetExhaustedAlert({ verdict = "EXHAUSTED" }: BudgetExhaustedAlertProps) {
	const unpriced = verdict === "UNVERIFIABLE";
	return (
		<Alert variant="destructive">
			<TriangleAlert aria-hidden />
			<AlertTitle>{unpriced ? "Spending can't be verified" : "Budget reached"}</AlertTitle>
			<AlertDescription>
				{unpriced
					? "Some usage has no price set, so spending can't be verified — new AI work is paused by this server's policy."
					: "Practice detection and mentor turns are paused until next month or until an instance admin raises the cap."}
			</AlertDescription>
		</Alert>
	);
}
