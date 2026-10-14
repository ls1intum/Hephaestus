import { cn } from "cn";
import type { OutcomeVector } from "@/api/types.gen";
import { type Outcome, OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import { Skeleton } from "@/components/ui/skeleton";

import { NO_RESULTS_YET } from "./across-workspace-copy";

/** The wire's counts in the registry's order, each with the outcome it counts. */
const COUNTS = [
	["met", "MET"],
	["notMet", "NOT_MET"],
	["doesNotApply", "DOES_NOT_APPLY"],
	["couldNotDetermine", "COULD_NOT_DETERMINE"],
] as const satisfies readonly (readonly [keyof OutcomeVector, Outcome])[];

export interface OutcomeCountsProps {
	outcomes: OutcomeVector;
	className?: string;
}

/**
 * How many results came out each way, every outcome with its registry icon and word, zeros
 * included so rows line up. With no result at all it says so once instead of four zeros.
 */
export function OutcomeCounts({ outcomes, className }: OutcomeCountsProps) {
	if (COUNTS.every(([key]) => outcomes[key] === 0)) {
		return <p className={cn("text-sm text-muted-foreground", className)}>{NO_RESULTS_YET}</p>;
	}
	return (
		<ul className={cn("flex flex-wrap gap-x-4 gap-y-1 text-sm", className)}>
			{COUNTS.map(([key, outcome]) => {
				const { icon: Icon, label } = OUTCOME_DEFS[outcome];
				const n = outcomes[key];
				return (
					<li
						key={key}
						className={cn(
							"inline-flex items-center gap-1.5 whitespace-nowrap",
							n === 0 && "text-muted-foreground",
						)}
					>
						<Icon aria-hidden className="size-3.5 shrink-0 text-muted-foreground" />
						<span>
							{label} <span className="font-medium tabular-nums">{n}</span>
						</span>
					</li>
				);
			})}
		</ul>
	);
}

/** One row of counts, in the width the loaded counts take. */
export function OutcomeCountsSkeleton() {
	return <Skeleton className="h-4 w-72 max-w-full" />;
}
