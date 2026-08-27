import { useId } from "react";
import type { OutcomeVector, PracticeTrend, TrendOpportunity } from "@/api/types.gen";
import { cn } from "@/lib/utils";
import { OBSERVATION_OUTCOME_PRESENTATION } from "./observation-outcome";
import {
	formatTrendCoverage,
	formatTrendGap,
	formatTrendProvenance,
	PRACTICE_TREND_PRESENTATION,
} from "./practice-trend-presentation";

const OUTCOME_SEGMENTS = [
	{
		key: "demonstratedStrengths",
		presentation: OBSERVATION_OUTCOME_PRESENTATION.PRESENT_GOOD,
	},
	{ key: "safeAvoidances", presentation: OBSERVATION_OUTCOME_PRESENTATION.ABSENT_GOOD },
	{
		key: "commissionProblems",
		presentation: OBSERVATION_OUTCOME_PRESENTATION.PRESENT_BAD,
	},
	{ key: "omissionGaps", presentation: OBSERVATION_OUTCOME_PRESENTATION.ABSENT_BAD },
] as const;

const count = (outcomes: OutcomeVector, key: (typeof OUTCOME_SEGMENTS)[number]["key"]) =>
	outcomes[key];
const strengths = (outcomes: OutcomeVector) =>
	outcomes.demonstratedStrengths + outcomes.safeAvoidances;
const difficulties = (outcomes: OutcomeVector) =>
	outcomes.commissionProblems + outcomes.omissionGaps;
const applicable = (outcomes: OutcomeVector) => strengths(outcomes) + difficulties(outcomes);

function outcomeSummary(label: string, opportunities: number, outcomes: OutcomeVector): string {
	const positive = strengths(outcomes);
	const negative = difficulties(outcomes);
	const notAssessed = outcomes.notApplicable;
	return `${label} ${opportunities} ${opportunities === 1 ? "review" : "reviews"}: ${positive} ${
		positive === 1 ? "strength" : "strengths"
	}, ${negative} to work on${notAssessed > 0 ? `, ${notAssessed} not assessed` : ""}.`;
}

interface ComparisonBarProps {
	label: string;
	opportunities: number;
	outcomes: OutcomeVector;
}

function ComparisonBar({ label, opportunities, outcomes }: ComparisonBarProps) {
	const total = applicable(outcomes);
	return (
		<div className="grid gap-2 sm:grid-cols-[8rem_1fr_auto] sm:items-center">
			<p className="text-sm font-medium">{label}</p>
			<div className="flex h-3 min-w-0 overflow-hidden rounded-full bg-muted" aria-hidden>
				{total > 0 &&
					OUTCOME_SEGMENTS.map(({ key, presentation }) => {
						const value = count(outcomes, key);
						if (value === 0) return null;
						return (
							<span
								key={key}
								className={presentation.barClassName}
								style={{ width: `${(value / total) * 100}%` }}
							/>
						);
					})}
			</div>
			<p className="whitespace-nowrap text-sm text-muted-foreground">
				{strengths(outcomes)} strengths · {difficulties(outcomes)} to work on
			</p>
			<span className="sr-only">{outcomeSummary(label, opportunities, outcomes)}</span>
		</div>
	);
}

function OpportunityMark({ opportunity }: { opportunity: TrendOpportunity }) {
	const total = Math.max(1, applicable(opportunity.outcomes));
	return (
		<span
			className={cn(
				"flex h-5 w-2 shrink-0 overflow-hidden rounded-full bg-muted",
				opportunity.bundle === "OLDER" && "opacity-30",
			)}
			aria-hidden
		>
			{OUTCOME_SEGMENTS.map(({ key, presentation }) => {
				const value = count(opportunity.outcomes, key);
				if (value === 0) return null;
				return (
					<span
						key={key}
						className={cn("h-full", presentation.barClassName)}
						style={{ width: `${(value / total) * 100}%` }}
					/>
				);
			})}
		</span>
	);
}

function OpportunityStrip({ opportunities }: { opportunities: TrendOpportunity[] }) {
	if (opportunities.length === 0) return null;
	const previousCount = opportunities.filter((item) => item.bundle === "PREVIOUS").length;
	const currentCount = opportunities.filter((item) => item.bundle === "CURRENT").length;
	return (
		<div className="grid gap-1.5">
			<div
				role="img"
				aria-label={`${opportunities.length} evidence opportunities, ordered oldest to newest: ${previousCount} earlier and ${currentCount} latest opportunities.`}
				className="flex min-h-7 items-center gap-2 overflow-hidden"
			>
				{opportunities.map((opportunity, index) => {
					const beginsCurrent =
						opportunity.bundle === "CURRENT" &&
						index > 0 &&
						opportunities[index - 1]?.bundle !== "CURRENT";
					return (
						<span
							key={`${opportunity.workKind}-${opportunity.reviewedWorkId}-${opportunity.index}`}
							className={cn("flex items-center", beginsCurrent && "ml-1 border-l pl-3")}
						>
							<OpportunityMark opportunity={opportunity} />
						</span>
					);
				})}
				<span className="ml-auto text-xs text-muted-foreground" aria-hidden>
					oldest → newest
				</span>
			</div>
			<p className="text-xs text-muted-foreground">Spacing shows sequence, not elapsed time.</p>
		</div>
	);
}

export interface PracticeTrendPanelProps {
	trend: PracticeTrend;
	className?: string;
}

export function PracticeTrendPanel({ trend, className }: PracticeTrendPanelProps) {
	const headingId = useId();
	const presentation = PRACTICE_TREND_PRESENTATION[trend.direction];
	const insufficient = trend.direction === "INSUFFICIENT_EVIDENCE";
	const coverage = formatTrendCoverage(trend.support);
	const comparisonLabel =
		trend.currentOutcomes && trend.previousOutcomes
			? `${outcomeSummary(
					"Latest",
					trend.support.currentOpportunities,
					trend.currentOutcomes,
				)} ${outcomeSummary(
					"Earlier",
					trend.support.previousOpportunities,
					trend.previousOutcomes,
				)}`
			: undefined;

	return (
		<section
			aria-labelledby={headingId}
			className={cn("grid gap-4 rounded-xl border bg-card p-4", className)}
		>
			<div className="flex flex-wrap items-start justify-between gap-3">
				<div className={"flex items-start gap-2"}>
					<presentation.Icon
						className={cn(
							"mt-0.5 size-5 shrink-0",
							presentation.tone === "positive" && "text-success",
							presentation.tone === "negative" && "text-destructive",
							presentation.tone === "neutral" && "text-muted-foreground",
							presentation.tone === "muted" && "text-muted-foreground/80",
						)}
						aria-hidden
					/>
					<div>
						<h2 id={headingId} className="text-base font-semibold">
							Recent direction
						</h2>
						<p className="text-sm text-muted-foreground">{presentation.label}</p>
					</div>
				</div>
			</div>

			{insufficient ? (
				<p className="text-sm">{formatTrendGap(trend.support)}</p>
			) : (
				<>
					{trend.direction === "UNCERTAIN" && (
						<p className="text-sm text-muted-foreground">
							The available evidence does not support one clear recent direction yet.
						</p>
					)}
					{trend.currentOutcomes && trend.previousOutcomes && (
						<div role="img" aria-label={comparisonLabel} className="grid gap-3">
							<ComparisonBar
								label="Earlier"
								opportunities={trend.support.previousOpportunities}
								outcomes={trend.previousOutcomes}
							/>
							<ComparisonBar
								label="Latest"
								opportunities={trend.support.currentOpportunities}
								outcomes={trend.currentOutcomes}
							/>
						</div>
					)}
				</>
			)}

			<OpportunityStrip opportunities={trend.opportunities} />

			<div className="grid gap-1 border-t pt-3 text-sm text-muted-foreground">
				<p>{formatTrendProvenance(trend.support)}</p>
				{coverage && <p>{coverage}</p>}
				<p>This describes recent evidence, not your overall ability.</p>
			</div>
		</section>
	);
}
