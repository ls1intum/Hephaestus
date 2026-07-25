import { Progress as ProgressRoot } from "@base-ui/react/progress";
import { Link } from "@tanstack/react-router";
import { CircleAlert, CircleDollarSign, TrendingUp, TriangleAlert } from "lucide-react";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { formatCostUsd } from "@/components/admin/ai/jobUtils";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { ProgressIndicator, ProgressTrack } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { LlmUsageByDayTable, LlmUsageByJobTypeTable } from "./LlmUsageBreakdownTables";
import { MonthNavigator } from "./MonthNavigator";
import {
	BUDGET_WARN_PERCENT,
	type BudgetProjection,
	budgetResetDayLabel,
	budgetUsedPercent,
	formatDayLabel,
	formatMonthLabel,
	projectBudget,
} from "./usageUtils";

type BudgetVerdict = WorkspaceLlmUsageReport["byoBudgetVerdict"];

export interface AdminLlmUsagePageProps {
	/** ISO `yyyy-MM` month currently shown. */
	month: string;
	/** Whether `month` is the current calendar month (UTC) — gates every pause/pace banner. */
	isCurrentMonth: boolean;
	/** Slug of the workspace being viewed; used for the in-product links to its models page. */
	workspaceSlug: string;
	report?: WorkspaceLlmUsageReport;
	isLoading: boolean;
	/** The thrown request error, if the report failed to load. */
	error: unknown;
	/** Retry the failed report load. */
	onRetry?: () => void;
	onPrevMonth: () => void;
	onNextMonth: () => void;
	/** Open the own-provider cap editor. The dialog itself lives in the route container. */
	onEditByoCap: () => void;
	/** Injected so burn-rate projections are deterministic in tests and stories. */
	now?: Date;
}

/**
 * Workspace-admin cost control for one month.
 *
 * The workspace lives under two independent caps that are different people's money and are never
 * summed (#1368 glossary rule #2): the *shared-model budget* its host sets and funds, and the
 * *own-provider cap* the workspace sets on its own connected provider. They pause independently,
 * so every banner here names whose cap tripped and routes to whoever can lift it — the workspace
 * admin can act on their own cap, and on the host's they can only move a purpose across.
 *
 * Pure/presentational — the route container owns the query, the selected month, and the dialog.
 */
export function AdminLlmUsagePage({
	month,
	isCurrentMonth,
	workspaceSlug,
	report,
	isLoading,
	error,
	onRetry,
	onPrevMonth,
	onNextMonth,
	onEditByoCap,
	now = new Date(),
}: AdminLlmUsagePageProps) {
	// The confirmed (priced) spend on each side — the figures the two caps compare against. When
	// some usage this month has no price on record, each is a floor, not the full total (see
	// `unpricedEventCount` below).
	const instanceSpend = report?.pricedTotalCostUsd ?? 0;
	const byoSpend = report?.byoTotalCostUsd ?? 0;
	const instanceCap = report?.instanceMonthlyBudgetUsd;
	const byoCap = report?.byoMonthlyBudgetUsd;
	const instancePercent = budgetUsedPercent(instanceSpend, instanceCap);
	const byoPercent = budgetUsedPercent(byoSpend, byoCap);
	const unpricedEventCount = report?.unpricedEventCount ?? 0;
	const byoPaused = isCurrentMonth && (report?.byoPaused ?? false);
	const instancePaused = isCurrentMonth && (report?.instanceFundedPaused ?? false);
	// A cap that is already at the wall is reported by its pause banner; warning "you've used 100%"
	// underneath it would just say the same thing more quietly.
	const byoWarning =
		isCurrentMonth && !byoPaused && byoPercent != null && byoPercent >= BUDGET_WARN_PERCENT;
	const instanceWarning =
		isCurrentMonth &&
		!instancePaused &&
		instancePercent != null &&
		instancePercent >= BUDGET_WARN_PERCENT;
	const hasUsage =
		report != null &&
		(report.byJobType.length > 0 || report.byDay.length > 0 || instanceSpend > 0 || byoSpend > 0);
	// A cap with no visible meter is a trap, so the own-provider card shows whenever either exists.
	const hasByoSide = byoCap != null || byoSpend > 0;

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<div className="flex flex-wrap items-center justify-between gap-4">
				<header className="space-y-1">
					<div className="flex items-center gap-2">
						<CircleDollarSign className="size-6 text-muted-foreground" aria-hidden />
						<h1 className="text-2xl font-semibold">AI usage</h1>
					</div>
					<p className="text-sm text-muted-foreground">
						LLM spend for this workspace, rolled up from the usage ledger (UTC months). Shared
						models are funded and capped by your host; your own connected provider is capped by you.
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
					<div className="grid gap-4 md:grid-cols-2">
						{["shared", "own"].map((slot) => (
							<Card key={slot}>
								<CardHeader>
									<Skeleton className="h-4 w-40" />
									<Skeleton className="h-7 w-28" />
								</CardHeader>
								<CardContent>
									<Skeleton className="h-1.5 w-full" />
								</CardContent>
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
					{/* Own-provider first when both are paused: it is the one they can act on. */}
					{byoPaused && (
						<ByoPauseAlert
							verdict={report.byoBudgetVerdict}
							month={month}
							workspaceSlug={workspaceSlug}
							onEditByoCap={onEditByoCap}
						/>
					)}
					{instancePaused && (
						<InstancePauseAlert
							verdict={report.instanceBudgetVerdict}
							month={month}
							workspaceSlug={workspaceSlug}
						/>
					)}

					{byoWarning && (
						<BudgetPaceAlert
							scope="byo"
							percent={byoPercent}
							spendUsd={byoSpend}
							capUsd={byoCap}
							projection={projectBudget(byoSpend, byoCap, month, now)}
						/>
					)}
					{instanceWarning && (
						<BudgetPaceAlert
							scope="instance"
							percent={instancePercent}
							spendUsd={instanceSpend}
							capUsd={instanceCap}
							projection={projectBudget(instanceSpend, instanceCap, month, now)}
						/>
					)}

					{unpricedEventCount > 0 && (
						// Verbatim framing from the #1368 glossary ("the untrusted monthly total" copy moment):
						// the "at least $X" formulation is the fix — no new vocabulary, direction of error obvious.
						<Alert variant="warning" role="status">
							<CircleAlert aria-hidden />
							<AlertTitle>
								{unpricedEventCount === 1
									? "1 call is not included in the spend totals"
									: `${unpricedEventCount.toLocaleString()} calls are not included in the spend totals`}
							</AlertTitle>
							<AlertDescription>
								Some usage has no price set, so the real totals may be higher. For a model on your
								own provider, add its price in Models. For a shared model, ask your host to add
								pricing.
							</AlertDescription>
						</Alert>
					)}

					<div className="grid gap-4 md:grid-cols-2">
						<SharedBudgetCard
							isCurrentMonth={isCurrentMonth}
							spendUsd={instanceSpend}
							capUsd={instanceCap}
							percent={instancePercent}
							paused={instancePaused}
							workspaceSlug={workspaceSlug}
						/>
						{hasByoSide ? (
							<OwnProviderBudgetCard
								isCurrentMonth={isCurrentMonth}
								spendUsd={byoSpend}
								capUsd={byoCap}
								percent={byoPercent}
								paused={byoPaused}
								onEditByoCap={onEditByoCap}
							/>
						) : (
							<NoOwnProviderCard workspaceSlug={workspaceSlug} onEditByoCap={onEditByoCap} />
						)}
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

interface ByoPauseAlertProps {
	verdict: BudgetVerdict;
	month: string;
	workspaceSlug: string;
	onEditByoCap: () => void;
}

/** The workspace's own cap tripped — the one pause its admin can lift themselves. */
function ByoPauseAlert({ verdict, month, workspaceSlug, onEditByoCap }: ByoPauseAlertProps) {
	if (verdict === "UNVERIFIABLE") {
		return (
			<Alert variant="destructive" role="alert">
				<TriangleAlert aria-hidden />
				<AlertTitle>Your cap can't be enforced</AlertTitle>
				<AlertDescription>
					<p>
						Some calls on your own models have no price set, so spend can't be checked against your
						cap. Add a price on your{" "}
						<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }}>
							models page
						</Link>{" "}
						to resume, or remove the cap.
					</p>
					<Button variant="outline" size="sm" className="mt-2" onClick={onEditByoCap}>
						Change your cap
					</Button>
				</AlertDescription>
			</Alert>
		);
	}
	return (
		<Alert variant="destructive" role="alert">
			<TriangleAlert aria-hidden />
			<AlertTitle>Your monthly cap is reached</AlertTitle>
			<AlertDescription>
				<p>
					Work on your own provider is paused until {budgetResetDayLabel(month)} (UTC), or until you
					raise or remove your cap.
				</p>
				<Button variant="outline" size="sm" className="mt-2" onClick={onEditByoCap}>
					Change your cap
				</Button>
			</AlertDescription>
		</Alert>
	);
}

interface InstancePauseAlertProps {
	verdict: BudgetVerdict;
	month: string;
	workspaceSlug: string;
}

/**
 * The host's shared-model budget tripped. Warning, not destructive: the workspace's own provider
 * keeps running, and nothing here is the workspace admin's to fix — the only move they own is
 * switching a purpose onto their own provider.
 */
function InstancePauseAlert({ verdict, month, workspaceSlug }: InstancePauseAlertProps) {
	if (verdict === "UNVERIFIABLE") {
		return (
			<Alert variant="warning" role="alert">
				<TriangleAlert aria-hidden />
				<AlertTitle>Shared-model spend can't be verified</AlertTitle>
				<AlertDescription>
					<p>
						Some shared-model calls have no price set, so spend can't be checked against your host's
						budget, and work on shared models is paused. Only your host can price a shared model —
						ask them to. Work on your own connected provider is not affected.
					</p>
				</AlertDescription>
			</Alert>
		);
	}
	return (
		<Alert variant="warning" role="alert">
			<TriangleAlert aria-hidden />
			<AlertTitle>Shared-model budget reached</AlertTitle>
			<AlertDescription>
				<p>
					Work on shared models is paused until {budgetResetDayLabel(month)} (UTC) or until your
					host raises the budget. Work on your own connected provider is not affected.
				</p>
				<Button
					variant="outline"
					size="sm"
					className="mt-2"
					render={<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }} />}
				>
					Switch a purpose to your own provider
				</Button>
			</AlertDescription>
		</Alert>
	);
}

interface BudgetPaceAlertProps {
	scope: "byo" | "instance";
	percent: number;
	spendUsd: number;
	capUsd: number | undefined;
	/** `null` when the month is too young or the spend too empty for a pace to mean anything. */
	projection: BudgetProjection | null;
}

/** Warn before the wall: how much of a cap is gone, and when this month's pace would reach it. */
function BudgetPaceAlert({ scope, percent, spendUsd, capUsd, projection }: BudgetPaceAlertProps) {
	const isByo = scope === "byo";
	return (
		<Alert variant="warning" role="status">
			<TrendingUp aria-hidden />
			<AlertTitle>
				You've used {Math.round(percent)}% of{" "}
				{isByo ? "your own-provider cap" : "the shared-model budget"}
			</AlertTitle>
			<AlertDescription>
				<p>
					{formatCostUsd(spendUsd)} of {formatCostUsd(capUsd)}
					{isByo ? " you capped yourself." : " your host set."}
					{projection != null &&
						(projection.reachedOn != null
							? ` At this month's pace you'll reach it around ${formatDayLabel(projection.reachedOn)}.`
							: ` At this month's pace you'll finish the month around ${formatCostUsd(projection.projectedMonthEndUsd)}.`)}
				</p>
			</AlertDescription>
		</Alert>
	);
}

interface SharedBudgetCardProps {
	isCurrentMonth: boolean;
	spendUsd: number;
	capUsd: number | undefined;
	percent: number | undefined;
	paused: boolean;
	workspaceSlug: string;
}

/** Read-only by design: only an instance admin can move this number. */
function SharedBudgetCard({
	isCurrentMonth,
	spendUsd,
	capUsd,
	percent,
	paused,
	workspaceSlug,
}: SharedBudgetCardProps) {
	return (
		<Card>
			<CardHeader>
				<CardDescription>Shared models — budget set by your host</CardDescription>
				<CardTitle className="text-2xl tabular-nums">
					{formatCostUsd(spendUsd)}
					{capUsd != null && (
						<span className="text-base font-normal text-muted-foreground">
							{" "}
							of {formatCostUsd(capUsd)}
						</span>
					)}
				</CardTitle>
				<CardDescription>
					{isCurrentMonth ? "Month-to-date spend on shared models." : "Spend on shared models."}
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-3">
				{percent != null ? (
					<BudgetMeter
						percent={percent}
						paused={paused}
						spendUsd={spendUsd}
						capUsd={capUsd}
						label="Shared-model budget used"
					/>
				) : (
					<p className="text-sm text-muted-foreground">
						No budget set — your host isn't capping shared-model spend for this workspace.
					</p>
				)}
				<p className="text-sm text-muted-foreground">
					You can't change this budget; only your host can. What you can change is what runs on it —
					move a purpose to your own connected provider on the{" "}
					<Link
						to="/w/$workspaceSlug/admin/models"
						params={{ workspaceSlug }}
						className="underline underline-offset-4"
					>
						models page
					</Link>
					.
				</p>
			</CardContent>
		</Card>
	);
}

interface OwnProviderBudgetCardProps {
	isCurrentMonth: boolean;
	spendUsd: number;
	capUsd: number | undefined;
	percent: number | undefined;
	paused: boolean;
	onEditByoCap: () => void;
}

/** The workspace's own money — set, change, and remove all live here. */
function OwnProviderBudgetCard({
	isCurrentMonth,
	spendUsd,
	capUsd,
	percent,
	paused,
	onEditByoCap,
}: OwnProviderBudgetCardProps) {
	return (
		<Card>
			<CardHeader>
				<CardDescription>Your own provider — your cap</CardDescription>
				<CardTitle className="text-2xl tabular-nums">
					{formatCostUsd(spendUsd)}
					{capUsd != null && (
						<span className="text-base font-normal text-muted-foreground">
							{" "}
							of {formatCostUsd(capUsd)}
						</span>
					)}
				</CardTitle>
				<CardDescription>
					{isCurrentMonth
						? "Month-to-date spend on your own connected provider."
						: "Spend on your own connected provider."}{" "}
					Never counted toward your host's budget.
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-3">
				{percent != null ? (
					<BudgetMeter
						percent={percent}
						paused={paused}
						spendUsd={spendUsd}
						capUsd={capUsd}
						label="Your own-provider cap used"
					/>
				) : (
					<p className="text-sm text-muted-foreground">
						No cap — this workspace's own-provider spend is unlimited.
					</p>
				)}
				<Button variant="outline" size="sm" onClick={onEditByoCap}>
					{capUsd != null ? "Change cap" : "Set a cap"}
				</Button>
			</CardContent>
		</Card>
	);
}

interface NoOwnProviderCardProps {
	workspaceSlug: string;
	onEditByoCap: () => void;
}

/** Quiet call-to-action: no own-provider cap and nothing has run on one. */
function NoOwnProviderCard({ workspaceSlug, onEditByoCap }: NoOwnProviderCardProps) {
	return (
		<Card>
			<CardHeader>
				<CardDescription>Your own provider — your cap</CardDescription>
				<CardTitle className="text-2xl tabular-nums">No spend</CardTitle>
				<CardDescription>
					Nothing has run on a provider of your own this month. Connect one on the{" "}
					<Link
						to="/w/$workspaceSlug/admin/models"
						params={{ workspaceSlug }}
						className="underline underline-offset-4"
					>
						models page
					</Link>{" "}
					and you can cap what it spends yourself — separate from your host's budget.
				</CardDescription>
			</CardHeader>
			<CardContent>
				<Button variant="outline" size="sm" onClick={onEditByoCap}>
					Set a cap
				</Button>
			</CardContent>
		</Card>
	);
}

interface BudgetMeterProps {
	percent: number;
	paused: boolean;
	spendUsd: number;
	capUsd: number | undefined;
	/** Distinct per meter, so a screen reader never has to guess which cap it is on. */
	label: string;
}

/**
 * One cap's consumption. Three tones, because two hid the approach: normal, amber from
 * {@link BUDGET_WARN_PERCENT} — the same threshold that raises the pace warning — and destructive
 * once the cap is reached or the pause is live.
 */
function BudgetMeter({ percent, paused, spendUsd, capUsd, label }: BudgetMeterProps) {
	const value = Math.min(Math.max(percent, 0), 100);
	const rounded = Math.round(percent);
	const valueText = `${rounded}% used — ${formatCostUsd(spendUsd)} of ${formatCostUsd(capUsd)}`;
	const tone =
		paused || percent >= 100
			? "bg-destructive"
			: percent >= BUDGET_WARN_PERCENT
				? "bg-warning"
				: "bg-primary";

	return (
		<div className="space-y-1.5">
			<ProgressRoot.Root
				value={value}
				className="flex w-full"
				aria-label={label}
				getAriaValueText={() => valueText}
			>
				<ProgressTrack className="h-1.5 rounded-full">
					<ProgressIndicator className={tone} />
				</ProgressTrack>
			</ProgressRoot.Root>
			<p className="text-sm text-muted-foreground tabular-nums">{rounded}% used</p>
		</div>
	);
}
