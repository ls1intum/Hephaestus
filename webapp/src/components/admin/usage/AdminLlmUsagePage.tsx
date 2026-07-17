import { CircleAlert, CircleDollarSign, TriangleAlert } from "lucide-react";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { formatCostUsd, formatTokens } from "@/components/admin/ai/jobUtils";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import {
	Table,
	TableBody,
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
	isError: boolean;
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
	isError,
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
		<div className="container mx-auto py-6 max-w-4xl space-y-6">
			<div className="flex flex-wrap items-center justify-between gap-4">
				<header className="space-y-1">
					<h1 className="text-3xl font-bold">AI usage</h1>
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

			{isError ? (
				<p className="py-8 text-center text-sm text-destructive">
					Failed to load AI usage. Please try again.
				</p>
			) : isLoading || report == null ? (
				<div className="flex items-center justify-center py-12">
					<Spinner />
				</div>
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
									<div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
										<div
											className={
												report.overBudget
													? "h-full rounded-full bg-destructive"
													: "h-full rounded-full bg-primary"
											}
											style={{ width: `${Math.min(budgetUsedPercent, 100)}%` }}
										/>
									</div>
								</CardContent>
							)}
						</Card>
					</div>

					{!hasUsage ? (
						<div className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
							<CircleDollarSign className="size-8" aria-hidden />
							<p className="text-sm">No AI usage in {formatMonthLabel(month)}.</p>
						</div>
					) : (
						<>
							<Card>
								<CardHeader>
									<CardTitle>By job type</CardTitle>
								</CardHeader>
								<CardContent>
									<div className="rounded-md border">
										<Table>
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
											<TableBody>
												{report.byJobType.map((row) => (
													<TableRow key={row.jobType}>
														<TableCell className="font-medium">
															{JOB_TYPE_LABELS[row.jobType]}
														</TableCell>
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
										</Table>
									</div>
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
											{report.byDay.map((day) => (
												<li key={String(day.day)} className="flex items-center gap-3 text-sm">
													<span className="w-14 shrink-0 text-muted-foreground">
														{formatUsageDay(day.day)}
													</span>
													<div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
														<div
															className="h-full rounded-full bg-primary"
															style={{
																width: `${maxDayCost > 0 ? (day.costUsd / maxDayCost) * 100 : 0}%`,
															}}
														/>
													</div>
													<span className="w-20 shrink-0 text-right tabular-nums">
														{formatCostUsd(day.costUsd)}
													</span>
													<span className="w-20 shrink-0 text-right tabular-nums text-muted-foreground">
														{day.events.toLocaleString()} {day.events === 1 ? "event" : "events"}
													</span>
												</li>
											))}
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
