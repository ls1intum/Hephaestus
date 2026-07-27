import { TrendingUp } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { formatCapUsd, formatCostUsd } from "@/lib/money";
import { type Fx, FxAmount, spendConversion, spendOfCapConversion } from "./fx";
import { type BudgetProjection, formatDayLabel } from "./usage-utils";

export interface BudgetPaceAlertProps {
	scope: "provider" | "shared";
	/**
	 * Absent on the workspace's own page ("You've used …"); the workspace name on the instance
	 * console ("Acme has used …"). Only the subject changes — the sentence underneath is one text.
	 */
	subjectName?: string;
	percent: number;
	spendUsd: number;
	capUsd: number | undefined;
	/** `null` when the month is too young or the spend too empty for a pace to mean anything. */
	projection: BudgetProjection | null;
	fx: Fx;
}

/**
 * Warn before the wall: how much of a cap is gone, and when this month's pace would reach it.
 *
 * Both consoles render it, so the projection sentence cannot drift apart between them — the
 * workspace admin watching their own cap and the instance admin watching a workspace's read the
 * same figures, in the same order.
 */
export function BudgetPaceAlert({
	scope,
	subjectName,
	percent,
	spendUsd,
	capUsd,
	projection,
	fx,
}: BudgetPaceAlertProps) {
	const capName = scope === "provider" ? "provider cap" : "shared-model budget";
	// The short noun for the sentence underneath, where the title one line up has already said which
	// of the two purses this is — repeating the full name there reads as a second cap.
	const capNoun = scope === "provider" ? "cap" : "budget";
	return (
		<Alert variant="warning" role="status">
			<TrendingUp aria-hidden />
			<AlertTitle>
				{subjectName != null
					? `${subjectName} has used ${Math.round(percent)}% of its ${capName}`
					: `You've used ${Math.round(percent)}% of your ${capName}`}
			</AlertTitle>
			<AlertDescription>
				<p>
					{formatCostUsd(spendUsd)} of {formatCapUsd(capUsd)}
					<FxAmount conversion={spendOfCapConversion(spendUsd, capUsd, fx)} />.
					{/* Person-neutral, because the same sentence runs under two different subjects: "Acme has
					    used …" on the instance console and "You've used …" on the workspace's own page. */}
					{projection != null &&
						(projection.reachedOn != null ? (
							` At this pace, the ${capNoun} is reached around ${formatDayLabel(projection.reachedOn)}.`
						) : (
							// The month-end figure converts too: one sentence that quotes "$43.90 of $50
							// (≈ €38.59 of €44)" and then a bare "$61.20" makes the reader switch currencies
							// mid-breath.
							<>
								{` At this pace, the month finishes around ${formatCostUsd(projection.projectedMonthEndUsd)}`}
								<FxAmount conversion={spendConversion(projection.projectedMonthEndUsd, fx)} />.
							</>
						))}
				</p>
			</AlertDescription>
		</Alert>
	);
}
