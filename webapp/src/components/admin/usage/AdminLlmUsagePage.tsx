import { Progress as ProgressRoot } from "@base-ui/react/progress";
import { CircleAlert, CircleDollarSign, TriangleAlert } from "lucide-react";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { formatCostUsd, formatTokens } from "@/components/admin/ai/jobUtils";
import { TableRowsSkeleton } from "@/components/admin/integrations/TableRowsSkeleton";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { Progress, ProgressIndicator, ProgressTrack } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import {
	Table,
	TableBody,
	TableCaption,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { MonthNavigator } from "./MonthNavigator";
import { formatMonthLabel, formatUsageDay, JOB_TYPE_LABELS } from "./usageUtils";

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

type ByJobTypeRows = WorkspaceLlmUsageReport["byJobType"];

/** One entry per by-job-type header column. */
const JOB_TYPE_SKELETON_COLUMNS = ["w-32", "w-16", "w-20", "w-16", "w-12", "w-12"];

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
	const spend = report?.totalCostUsd ?? 0;
	// A $0 cap is a supported state ("paused immediately"), so it reads as 100% used — only an
	// absent cap has no percentage to show.
	const budgetUsedPercent =
		budget != null ? (budget > 0 ? (spend / budget) * 100 : 100) : undefined;
	const hasUsage =
		report != null && (report.byJobType.length > 0 || report.byDay.length > 0 || spend > 0);
	const maxDayCost = Math.max(...(report?.byDay.map((d) => d.costUsd) ?? []), 0);
	const uncostedEvents = report?.uncostedEvents ?? 0;

	return (
		<div className="mx-auto w-full max-w-4xl space-y-6 py-6">
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
							<ByJobTypeTable />
						</CardContent>
					</Card>
				</>
			) : (
				<>
					{report.overBudget && isCurrentMonth && (
						<Alert variant="destructive">
							<TriangleAlert aria-hidden />
							<AlertTitle>Monthly AI budget used up</AlertTitle>
							<AlertDescription>
								Practice detection and mentor turns are paused until next month or until an instance
								admin raises the cap.
							</AlertDescription>
						</Alert>
					)}

					{uncostedEvents > 0 && (
						<Alert variant="warning">
							<CircleAlert aria-hidden />
							<AlertTitle>Some usage has no known cost</AlertTitle>
							<AlertDescription>
								{uncostedEvents === 1 ? "1 call" : `${uncostedEvents.toLocaleString()} calls`} this
								month ran on a model with no price on record, so{" "}
								{uncostedEvents === 1 ? "it counts" : "they count"} as $0 here and the budget cap
								can't see {uncostedEvents === 1 ? "it" : "them"}. Ask an instance admin to add
								pricing for the model.
							</AlertDescription>
						</Alert>
					)}

					<div className="grid gap-4 sm:grid-cols-3">
						<Card>
							<CardHeader>
								<CardDescription>
									{isCurrentMonth ? "Month-to-date spend" : "Month spend"}
								</CardDescription>
								<CardTitle className="text-2xl tabular-nums">{formatCostUsd(spend)}</CardTitle>
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
									<BudgetProgress percent={budgetUsedPercent} overBudget={report.overBudget} />
								</CardContent>
							)}
						</Card>
					</div>

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
									<ByJobTypeTable rows={report.byJobType} />
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
										<ul className="space-y-1.5">
											{report.byDay.map((day) => {
												const label = formatUsageDay(day.day);
												return (
													<li key={String(day.day)} className="flex items-center gap-3 text-sm">
														<span className="w-14 shrink-0 text-muted-foreground">{label}</span>
														<Progress
															className="flex-1"
															value={maxDayCost > 0 ? (day.costUsd / maxDayCost) * 100 : 0}
															aria-label={`Spend on ${label}`}
															getAriaValueText={() =>
																`${formatCostUsd(day.costUsd)} of ${formatCostUsd(maxDayCost)} on the busiest day`
															}
														/>
														<span className="w-20 shrink-0 text-right tabular-nums">
															{formatCostUsd(day.costUsd)}
														</span>
														<span className="w-20 shrink-0 text-right tabular-nums text-muted-foreground">
															{day.events.toLocaleString()} {day.events === 1 ? "event" : "events"}
														</span>
													</li>
												);
											})}
										</ul>
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

interface ByJobTypeTableProps {
	/** Omit while the report is still loading — the header mounts and the body is skeletoned. */
	rows?: ByJobTypeRows;
}

/** Per-job-type cost and token breakdown, sharing one header between loading and loaded states. */
function ByJobTypeTable({ rows }: ByJobTypeTableProps) {
	return (
		<Table containerClassName="rounded-md border">
			<TableCaption className="sr-only">AI spend by job type</TableCaption>
			<TableHeader>
				<TableRow>
					<TableHead scope="col">Job type</TableHead>
					<TableHead scope="col" className="text-right">
						Cost
					</TableHead>
					<TableHead scope="col" className="text-right">
						Input tokens
					</TableHead>
					<TableHead scope="col" className="text-right">
						Output tokens
					</TableHead>
					<TableHead scope="col" className="text-right">
						Calls
					</TableHead>
					<TableHead scope="col" className="text-right">
						Events
					</TableHead>
				</TableRow>
			</TableHeader>
			{rows == null ? (
				<TableRowsSkeleton columns={JOB_TYPE_SKELETON_COLUMNS} rows={3} />
			) : (
				<TableBody>
					{rows.map((row) => (
						<TableRow key={row.jobType}>
							<TableCell className="font-medium">{JOB_TYPE_LABELS[row.jobType]}</TableCell>
							<TableCell className="text-right tabular-nums">
								{formatCostUsd(row.costUsd)}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{formatTokens(row.inputTokens)}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{formatTokens(row.outputTokens)}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{row.totalCalls.toLocaleString()}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{row.events.toLocaleString()}
							</TableCell>
						</TableRow>
					))}
				</TableBody>
			)}
		</Table>
	);
}
