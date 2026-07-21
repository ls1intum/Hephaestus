import { TriangleAlert } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

/**
 * Compact budget-reached banner for surfaces outside the usage page. The usage page
 * (`AdminLlmUsagePage`) is where the EXHAUSTED verdict is explained in full, but nothing else in the
 * product said why practice detection or the mentor stopped responding — this fills that gap on the
 * Models tab (`AgentRuntimesPage`), where the affected `AgentConfigCard`s live. Copy matches the
 * usage page's own over-budget banner verbatim (#1368 glossary: "Budget reached" is never softened).
 */
export function BudgetExhaustedAlert() {
	return (
		<Alert variant="destructive">
			<TriangleAlert aria-hidden />
			<AlertTitle>Budget reached</AlertTitle>
			<AlertDescription>
				Practice detection and mentor turns are paused until next month or until an instance admin
				raises the cap.
			</AlertDescription>
		</Alert>
	);
}
