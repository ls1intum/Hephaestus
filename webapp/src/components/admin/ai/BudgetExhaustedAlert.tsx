import { Link } from "@tanstack/react-router";
import { TriangleAlert } from "lucide-react";
import type { WorkspaceLlmUsageReport } from "@/api";
import { budgetResetDayLabel, currentMonthUtc } from "@/components/admin/usage/usageUtils";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

type BudgetVerdict = WorkspaceLlmUsageReport["ownProviderBudgetVerdict"];

interface BudgetExhaustedAlertProps {
	/**
	 * Whose cap tripped. `"shared"` is the shared-model budget the host sets and pays for — the
	 * workspace admin can't lift it, only move work off it. `"own"` is the workspace's own provider
	 * cap, which they can raise or remove themselves.
	 */
	scope: "shared" | "own";
	/** The month's verdict for that cap — selects between "reached" and "can't be checked" copy. */
	verdict?: BudgetVerdict;
	/**
	 * ISO `yyyy-MM` month the pause belongs to. Defaults to the current UTC month, which is the only
	 * month a live pause can be in — it exists so the banner can name the day the pause lifts by
	 * itself instead of gesturing at "next month".
	 */
	month?: string;
	/** Runs with no price on record this month; names the count instead of saying "some". */
	unpricedEventCount?: number;
	/**
	 * Which surface is rendering the banner. It selects the action offered — never a word of the
	 * sentence: each remedy is a link from the page that doesn't own it, and nothing from the page
	 * that does.
	 */
	context: "usage" | "models";
	workspaceSlug: string;
	/** Opens the provider-cap editor. Used by `context="usage"`, where that cap is edited. */
	onEditOwnProviderCap?: () => void;
}

/**
 * The one owner of the four pause banners: provider cap reached / unenforceable, shared-model budget
 * reached / unverifiable. The usage page and the AI models page both render it, so the sentences
 * cannot drift apart between them.
 *
 * <p>The two caps pause independently, so this is rendered once per paused side and never merges
 * them: a spent shared-model budget leaves work on the workspace's own provider running, and saying
 * otherwise would send the admin to the wrong person.
 */
export function BudgetExhaustedAlert({
	scope,
	verdict = "EXHAUSTED",
	month = currentMonthUtc(),
	unpricedEventCount,
	context,
	workspaceSlug,
	onEditOwnProviderCap,
}: BudgetExhaustedAlertProps) {
	const noPriceSet = verdict === "UNVERIFIABLE";
	const own = scope === "own";
	const resetDay = budgetResetDayLabel(month);
	return (
		<Alert variant={own ? "destructive" : "warning"} role="alert">
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
				<p>
					{own
						? noPriceSet
							? `${unpricedSubject(scope, unpricedEventCount)} no price, so the cap can't be checked and your provider is paused. Add a price to resume, or remove the cap.`
							: `Paused until ${resetDay} (UTC), or until you raise or remove the cap.`
						: noPriceSet
							? `${unpricedSubject(scope, unpricedEventCount)} no price, so the budget can't be checked and shared models are paused. Only your host can price them.`
							: `Paused until ${resetDay} (UTC), or until your host raises the budget. Practice detection and Mentor can keep running on your own models.`}
				</p>
				<PauseAction
					scope={scope}
					noPriceSet={noPriceSet}
					context={context}
					workspaceSlug={workspaceSlug}
					onEditOwnProviderCap={onEditOwnProviderCap}
				/>
			</AlertDescription>
		</Alert>
	);
}

/**
 * "1 run on your models has" / "3 shared-model runs have" / "Some runs on your models have".
 *
 * The count is `unpricedEventCount` — runs, not the model calls a run may make several of.
 */
function unpricedSubject(scope: "shared" | "own", count: number | undefined): string {
	const own = scope === "own";
	if (count === 1) {
		return own ? "1 run on your models has" : "1 shared-model run has";
	}
	if (count != null && count > 1) {
		const n = count.toLocaleString();
		return own ? `${n} runs on your models have` : `${n} shared-model runs have`;
	}
	return own ? "Some runs on your models have" : "Some shared-model runs have";
}

interface PauseActionProps {
	scope: "shared" | "own";
	noPriceSet: boolean;
	context: "usage" | "models";
	workspaceSlug: string;
	onEditOwnProviderCap?: () => void;
}

/**
 * The remedy the current page can't perform itself. Prices are edited on AI models and caps on AI
 * usage, so each surface links to the other one and offers nothing that is already on screen.
 */
function PauseAction({
	scope,
	noPriceSet,
	context,
	workspaceSlug,
	onEditOwnProviderCap,
}: PauseActionProps) {
	if (scope === "own") {
		if (context === "models") {
			return (
				<Button
					variant="outline"
					size="sm"
					className="mt-2"
					render={<Link to="/w/$workspaceSlug/admin/usage" params={{ workspaceSlug }} />}
				>
					Adjust cap
				</Button>
			);
		}
		return (
			<div className="mt-2 flex flex-wrap gap-2">
				{noPriceSet && (
					<Button
						variant="outline"
						size="sm"
						render={<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }} />}
					>
						Open AI models
					</Button>
				)}
				<Button variant="outline" size="sm" onClick={onEditOwnProviderCap}>
					Adjust cap
				</Button>
			</div>
		);
	}
	// Nothing the workspace admin can do about a shared model's missing price, and on the AI models
	// page the purposes they could switch are already below the banner.
	if (noPriceSet || context === "models") {
		return null;
	}
	return (
		<Button
			variant="outline"
			size="sm"
			className="mt-2"
			render={<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }} />}
		>
			Open AI models
		</Button>
	);
}
