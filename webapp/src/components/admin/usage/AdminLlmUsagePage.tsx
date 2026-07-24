import { Progress as ProgressRoot } from "@base-ui/react/progress";
import { CircleAlert, CircleDollarSign, TriangleAlert } from "lucide-react";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { formatCostUsd } from "@/components/admin/ai/jobUtils";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { Progress, ProgressIndicator, ProgressTrack } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { LlmUsageByDayTable, LlmUsageByJobTypeTable } from "./LlmUsageBreakdownTables";
import { MonthNavigator } from "./MonthNavigator";
import { formatMonthLabel } from "./usageUtils";

export interface AdminLlmUsagePageProps {
	/** ISO `yyyy-MM` month currently shown. */
	month: string;
	/** Whether `month` is the current calendar month (UTC) — gates the over-budget banner. */
	isCurrentMonth: boolean;
	report?: WorkspaceLlmUsageReport;
	isLoading: boolean;
	/** The thrown request error, if the report failed to load. */
	error: unknown;
	/** Retry the failed report load. */
	onRetry?: () => void;
	onPrevMonth: () => void;
	onNextMonth: () => void;
}

/**
 * Workspace-admin view of one month of LLM spend: summary stat cards, an over-budget banner for
 * the current month, and by-job-type / by-day breakdowns. Pure/presentational — the route
 * container owns the query and the selected month.
 */
export function AdminLlmUsagePage({
	month,
	isCurrentMonth,
	report,
	isLoading,
	error,
	onRetry,
	onPrevMonth,
	onNextMonth,
}: AdminLlmUsagePageProps) {
	const budget = report?.monthlyBudgetUsd;
	// The confirmed (priced) spend — the figure the budget cap compares against. When some usage this
	// month has no price on record, it's a floor, not the full total (see `unpricedEventCount` below).
	const instanceFundedSpend = report?.pricedTotalCostUsd ?? 0;
	// A $0 cap is a supported state ("paused immediately"), so it reads as 100% used — only an
	// absent cap has no percentage to show.
	const budgetUsedPercent =
		budget != null ? (budget > 0 ? (instanceFundedSpend / budget) * 100 : 100) : undefined;
	const hasUsage =
		report != null &&
		(report.byJobType.length > 0 ||
			report.byDay.length > 0 ||
			instanceFundedSpend > 0 ||
			report.byoTotalCostUsd > 0);
	const unpricedEventCount = report?.unpricedEventCount ?? 0;
	const overBudget = report?.verdict === "EXHAUSTED";

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<div className="flex flex-wrap items-center justify-between gap-4">
				<header className="space-y-1">
					<div className="flex items-center gap-2">
						<CircleDollarSign className="size-6 text-muted-foreground" aria-hidden />
						<h1 className="text-2xl font-semibold">AI usage</h1>
					</div>
					<p className="text-sm text-muted-foreground">
						LLM spend for this workspace, rolled up from the usage ledger (UTC months).
					</p>
				</header>
				<MonthNavigator
					month={month}
					canGoNext={!isCurrentMonth}
					onPrevMonth={onPrevMonth}
					onNextMonth={onNextMonth}
				/>
			</div>

			{error != null ? (
				<QueryErrorAlert error={error} title="Couldn't load AI usage" onRetry={onRetry} />
			) : isLoading || report == null ? (
				// Skeleton the real card grid and table shell rather than blanking the page, so nothing
				// jumps when the report lands.
				<>
					<div className="grid gap-4 sm:grid-cols-3">
						{["spend", "cap", "used"].map((slot) => (
							<Card key={slot}>
								<CardHeader>
									<Skeleton className="h-4 w-28" />
									<Skeleton className="h-7 w-24" />
								</CardHeader>
							</Card>
						))}
					</div>
					<Card>
						<CardHeader>
							<CardTitle>By job type</CardTitle>
						</CardHeader>
						<CardContent>
							<LlmUsageByJobTypeTable />
						</CardContent>
					</Card>
				</>
			) : (
				<>
					{overBudget && isCurrentMonth && (
						<Alert variant="destructive">
							<TriangleAlert aria-hidden />
							<AlertTitle>Budget reached</AlertTitle>
							<AlertDescription>
								Practice detection and mentor turns are paused until next month or until an instance
								admin raises the cap.
							</AlertDescription>
						</Alert>
					)}

					{unpricedEventCount > 0 && (
						// Verbatim framing from the #1368 glossary ("the untrusted monthly total" copy moment):
						// the "at least $X" formulation is the fix — no new vocabulary, direction of error obvious.
						<Alert variant="warning">
							<CircleAlert aria-hidden />
							<AlertTitle>
								{unpricedEventCount === 1
									? "1 call is not included in the spend totals"
									: `${unpricedEventCount.toLocaleString()} calls are not included in the spend totals`}
							</AlertTitle>
							<AlertDescription>
								Some usage has no price set, so the real totals may be higher. For a workspace-owned
								model, add its price in Models. For a shared model, ask an instance admin to add
								pricing.
							</AlertDescription>
						</Alert>
					)}

					<div className="grid gap-4 sm:grid-cols-3">
						<Card>
							<CardHeader>
								<CardDescription>
									{isCurrentMonth ? "Month-to-date instance-funded spend" : "Instance-funded spend"}
								</CardDescription>
								<CardTitle className="text-2xl tabular-nums">
									{formatCostUsd(instanceFundedSpend)}
								</CardTitle>
							</CardHeader>
						</Card>
						<Card>
							<CardHeader>
								<CardDescription>Budget cap</CardDescription>
								<CardTitle className="text-2xl tabular-nums">
									{budget != null ? formatCostUsd(budget) : "No cap"}
								</CardTitle>
							</CardHeader>
						</Card>
						<Card>
							<CardHeader>
								<CardDescription>Budget used</CardDescription>
								<CardTitle className="text-2xl tabular-nums">
									{budgetUsedPercent != null ? `${Math.round(budgetUsedPercent)}%` : "—"}
								</CardTitle>
							</CardHeader>
							{budgetUsedPercent != null && (
								<CardContent>
									<BudgetProgress percent={budgetUsedPercent} overBudget={overBudget} />
								</CardContent>
							)}
						</Card>
					</div>

					{report.byoTotalCostUsd > 0 && (
						// Deliberately its own card, outside the budget grid above: your-provider spend is
						// never counted toward the monthly budget and must never be summed with it
						// (#1368 glossary rule #2).
						<Card>
							<CardHeader>
								<CardDescription>Workspace-owned spend</CardDescription>
								<CardTitle className="text-2xl tabular-nums">
									{formatCostUsd(report.byoTotalCostUsd)}
								</CardTitle>
								<CardDescription>
									Spend on this workspace's own connected provider this month. Not counted toward
									the budget above.
								</CardDescription>
							</CardHeader>
						</Card>
					)}

					{!hasUsage ? (
						<Empty className="border border-dashed">
							<EmptyHeader>
								<EmptyMedia variant="icon">
									<CircleDollarSign />
								</EmptyMedia>
								<EmptyTitle>No AI usage in {formatMonthLabel(month)}</EmptyTitle>
							</EmptyHeader>
						</Empty>
					) : (
						<>
							<Card>
								<CardHeader>
									<CardTitle>By job type</CardTitle>
								</CardHeader>
								<CardContent>
									<LlmUsageByJobTypeTable rows={report.byJobType} />
								</CardContent>
							</Card>

							<Card>
								<CardHeader>
									<CardTitle>By day</CardTitle>
								</CardHeader>
								<CardContent>
									{report.byDay.length === 0 ? (
										<p className="py-4 text-center text-sm text-muted-foreground">
											No daily breakdown for this month.
										</p>
									) : (
										<LlmUsageByDayTable rows={report.byDay} />
									)}
								</CardContent>
							</Card>
						</>
					)}
				</>
			)}
		</div>
	);
}

interface BudgetProgressProps {
	percent: number;
	overBudget: boolean;
}

/**
 * The month's budget consumption. Over budget the bar goes destructive, which needs the compound
 * form so the indicator can be tinted; under budget the plain `Progress` is enough.
 */
function BudgetProgress({ percent, overBudget }: BudgetProgressProps) {
	const value = Math.min(percent, 100);
	const valueText = `${Math.round(percent)}% of the monthly budget used`;

	if (overBudget) {
		return (
			<ProgressRoot.Root
				value={value}
				className="flex w-full"
				aria-label="Budget used"
				getAriaValueText={() => valueText}
			>
				<ProgressTrack className="h-1.5 rounded-full">
					<ProgressIndicator className="bg-destructive" />
				</ProgressTrack>
			</ProgressRoot.Root>
		);
	}

	return (
		<Progress
			value={value}
			className="w-full"
			aria-label="Budget used"
			getAriaValueText={() => valueText}
		/>
	);
}
