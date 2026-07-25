import { Progress as ProgressRoot } from "@base-ui/react/progress";
import { Link } from "@tanstack/react-router";
import { CircleAlert, CircleDollarSign, TrendingUp } from "lucide-react";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { BudgetExhaustedAlert } from "@/components/admin/ai/BudgetExhaustedAlert";
import { formatCapUsd, formatCostUsd } from "@/components/admin/ai/jobUtils";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { ProgressIndicator, ProgressTrack } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import {
	capConversion,
	type Fx,
	FxAmount,
	type FxConversion,
	FxDisclosure,
	spendConversion,
	spendOfCapConversion,
} from "./fx";
import { LlmUsageByDayTable, LlmUsageByJobTypeTable } from "./LlmUsageBreakdownTables";
import { MonthNavigator } from "./MonthNavigator";
import {
	BUDGET_WARN_PERCENT,
	type BudgetProjection,
	budgetUsedPercent,
	formatDayLabel,
	formatMonthLabel,
	projectBudget,
} from "./usageUtils";

export interface AdminLlmUsagePageProps {
	/** ISO `yyyy-MM` month currently shown. */
	month: string;
	/** Whether `month` is the current calendar month (UTC) — gates every pause/pace banner. */
	isCurrentMonth: boolean;
	/** Slug of the workspace being viewed; used for the in-product links to its AI models page. */
	workspaceSlug: string;
	report?: WorkspaceLlmUsageReport;
	isLoading: boolean;
	/** The thrown request error, if the report failed to load. */
	error: unknown;
	/** Retry the failed report load. */
	onRetry?: () => void;
	onPrevMonth: () => void;
	onNextMonth: () => void;
	/** Open the provider-cap editor. The dialog itself lives in the route container. */
	onEditOwnProviderCap: () => void;
	/** Injected so burn-rate projections are deterministic in tests and stories. */
	now?: Date;
}

/**
 * Workspace-admin cost control for one month.
 *
 * The workspace lives under two independent caps that are different people's money and are never
 * summed (#1368 glossary rule #2): the *shared-model budget* its host sets and funds, and the
 * *provider cap* the workspace sets on its own provider. They pause independently, so every banner
 * here names whose cap tripped and routes to whoever can lift it — the workspace admin can act on
 * their own cap, and on the host's they can only move a purpose across.
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
	onEditOwnProviderCap,
	now = new Date(),
}: AdminLlmUsagePageProps) {
	// The confirmed (priced) spend on each side — the figures the two caps compare against. When
	// some usage this month has no price on record, each is a floor, not the full total (see
	// `unpricedEventCount` below).
	const sharedSpend = report?.instanceTotalCostUsd ?? 0;
	const providerSpend = report?.ownProviderTotalCostUsd ?? 0;
	const sharedBudget = report?.instanceMonthlyBudgetUsd;
	const providerCap = report?.ownProviderMonthlyBudgetUsd;
	const sharedPercent = budgetUsedPercent(sharedSpend, sharedBudget);
	const providerPercent = budgetUsedPercent(providerSpend, providerCap);
	const unpricedEventCount = report?.unpricedEventCount ?? 0;
	const providerPaused = isCurrentMonth && (report?.ownProviderPaused ?? false);
	const sharedPaused = isCurrentMonth && (report?.instancePaused ?? false);
	// A cap that is already at the wall is reported by its pause banner; warning "you've used 100%"
	// underneath it would just say the same thing more quietly.
	const providerWarning =
		isCurrentMonth &&
		!providerPaused &&
		providerPercent != null &&
		providerPercent >= BUDGET_WARN_PERCENT;
	const sharedWarning =
		isCurrentMonth &&
		!sharedPaused &&
		sharedPercent != null &&
		sharedPercent >= BUDGET_WARN_PERCENT;
	const hasUsage =
		report != null &&
		(report.byJobType.length > 0 ||
			report.byDay.length > 0 ||
			sharedSpend > 0 ||
			providerSpend > 0);
	// A cap with no visible meter is a trap, so the provider card shows whenever either exists.
	const hasProviderSide = providerCap != null || providerSpend > 0;

	// Display-only conversion, absent unless the instance opted into a display currency. USD is the
	// number this page is really about — the estimates are secondary everywhere they appear, and the
	// caption below the cards is the one place they are explained.
	const fx: Fx = report?.fx;
	// The exact conversion each card headline will render, resolved here so the caption appears if
	// and only if something on the page actually converted — never as a footnote to nothing.
	const sharedTitleFx =
		sharedBudget != null
			? spendOfCapConversion(sharedSpend, sharedBudget, fx)
			: spendConversion(sharedSpend, fx);
	const providerTitleFx =
		providerCap != null
			? spendOfCapConversion(providerSpend, providerCap, fx)
			: spendConversion(providerSpend, fx);
	const providerCapFx = capConversion(providerCap, fx);
	// Every converted figure on the page, not just the cards: the breakdown tables convert their own
	// footer totals, so deriving the caption from the cards alone put converted euros on screen with
	// no rate behind them whenever a card happened not to convert (a $0 budget, say, which is the
	// supported "pause now" state). Derive it from what actually renders.
	const hasConversion =
		sharedTitleFx != null ||
		providerTitleFx != null ||
		providerCapFx != null ||
		spendConversion(sharedSpend, fx) != null ||
		spendConversion(providerSpend, fx) != null;

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<div className="flex flex-wrap items-center justify-between gap-4">
				<header className="space-y-1">
					<div className="flex items-center gap-2">
						<CircleDollarSign className="size-6 text-muted-foreground" aria-hidden />
						<h1 className="text-2xl font-semibold">AI usage</h1>
					</div>
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
						{["shared", "provider"].map((slot) => (
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
					{/* The provider cap comes first when both are paused: it is the one they can act on. */}
					{providerPaused && (
						<BudgetExhaustedAlert
							scope="own"
							verdict={report.ownProviderBudgetVerdict}
							month={month}
							unpricedEventCount={unpricedEventCount}
							context="usage"
							workspaceSlug={workspaceSlug}
							onEditOwnProviderCap={onEditOwnProviderCap}
						/>
					)}
					{sharedPaused && (
						<BudgetExhaustedAlert
							scope="shared"
							verdict={report.instanceBudgetVerdict}
							month={month}
							unpricedEventCount={unpricedEventCount}
							context="usage"
							workspaceSlug={workspaceSlug}
						/>
					)}

					{providerWarning && (
						<BudgetPaceAlert
							scope="provider"
							percent={providerPercent}
							spendUsd={providerSpend}
							capUsd={providerCap}
							projection={projectBudget(providerSpend, providerCap, month, now)}
							fx={fx}
						/>
					)}
					{sharedWarning && (
						<BudgetPaceAlert
							scope="shared"
							percent={sharedPercent}
							spendUsd={sharedSpend}
							capUsd={sharedBudget}
							projection={projectBudget(sharedSpend, sharedBudget, month, now)}
							fx={fx}
						/>
					)}

					{unpricedEventCount > 0 && (
						// The direction of the error is the point: totals under-count, never over-count, so
						// the copy says what is missing and who can add it rather than naming a status.
						<Alert variant="warning" role="status">
							<CircleAlert aria-hidden />
							<AlertTitle>
								{unpricedEventCount === 1
									? "1 call isn't counted in these totals"
									: `${unpricedEventCount.toLocaleString()} calls aren't counted in these totals`}
							</AlertTitle>
							<AlertDescription>
								<p>
									They have no price set, so real spend may be higher. Add prices for your own
									models in <AiModelsLink workspaceSlug={workspaceSlug} />; for shared models, ask
									your host.
								</p>
							</AlertDescription>
						</Alert>
					)}

					<div className="grid gap-4 md:grid-cols-2">
						<SharedBudgetCard
							isCurrentMonth={isCurrentMonth}
							spendUsd={sharedSpend}
							capUsd={sharedBudget}
							percent={sharedPercent}
							paused={sharedPaused}
							workspaceSlug={workspaceSlug}
							titleFx={sharedTitleFx}
						/>
						{hasProviderSide ? (
							<ProviderCapCard
								isCurrentMonth={isCurrentMonth}
								spendUsd={providerSpend}
								capUsd={providerCap}
								percent={providerPercent}
								paused={providerPaused}
								onEditOwnProviderCap={onEditOwnProviderCap}
								titleFx={providerTitleFx}
							/>
						) : (
							<NoProviderCard
								workspaceSlug={workspaceSlug}
								onEditOwnProviderCap={onEditOwnProviderCap}
							/>
						)}
					</div>

					{!hasUsage ? (
						<Empty className="border">
							<EmptyHeader>
								<EmptyMedia variant="icon">
									<CircleDollarSign />
								</EmptyMedia>
								<EmptyTitle>No AI usage in {formatMonthLabel(month)}</EmptyTitle>
								<EmptyDescription>Nothing ran this month.</EmptyDescription>
							</EmptyHeader>
							<EmptyContent>
								<Button
									variant="outline"
									size="sm"
									render={<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }} />}
								>
									Open AI models
								</Button>
							</EmptyContent>
						</Empty>
					) : (
						<>
							<Card>
								<CardHeader>
									<CardTitle>By job type</CardTitle>
								</CardHeader>
								<CardContent>
									<LlmUsageByJobTypeTable rows={report.byJobType} fx={fx} />
								</CardContent>
							</Card>

							<Card>
								<CardHeader>
									<CardTitle>By day</CardTitle>
								</CardHeader>
								<CardContent>
									{report.byDay.length === 0 ? (
										<Empty className="border">
											<EmptyHeader>
												<EmptyMedia variant="icon">
													<CircleDollarSign />
												</EmptyMedia>
												<EmptyTitle>No daily breakdown yet</EmptyTitle>
											</EmptyHeader>
										</Empty>
									) : (
										<LlmUsageByDayTable rows={report.byDay} fx={fx} />
									)}
								</CardContent>
							</Card>
						</>
					)}

					{/* Disclosed once, after every figure it qualifies — never beside each number, where it
					    would bury the numbers it exists to explain, and never above them, where a footnote
					    reads as a preamble to something else. */}
					{hasConversion && <FxDisclosure fx={fx} isCurrentMonth={isCurrentMonth} />}
				</>
			)}
		</div>
	);
}

/** One name for the AI config page, and one link to it, wherever this page sends someone there. */
function AiModelsLink({ workspaceSlug }: { workspaceSlug: string }) {
	return (
		<Link
			to="/w/$workspaceSlug/admin/models"
			params={{ workspaceSlug }}
			className="underline underline-offset-4"
		>
			AI models
		</Link>
	);
}

interface BudgetPaceAlertProps {
	scope: "provider" | "shared";
	percent: number;
	spendUsd: number;
	capUsd: number | undefined;
	/** `null` when the month is too young or the spend too empty for a pace to mean anything. */
	projection: BudgetProjection | null;
	fx: Fx;
}

/** Warn before the wall: how much of a cap is gone, and when this month's pace would reach it. */
function BudgetPaceAlert({
	scope,
	percent,
	spendUsd,
	capUsd,
	projection,
	fx,
}: BudgetPaceAlertProps) {
	const isProvider = scope === "provider";
	return (
		<Alert variant="warning" role="status">
			<TrendingUp aria-hidden />
			<AlertTitle>
				You've used {Math.round(percent)}% of your{" "}
				{isProvider ? "provider cap" : "shared-model budget"}
			</AlertTitle>
			<AlertDescription>
				<p>
					{formatCostUsd(spendUsd)} of {formatCapUsd(capUsd)}
					<FxAmount conversion={spendOfCapConversion(spendUsd, capUsd, fx)} />.
					{projection != null &&
						(projection.reachedOn != null ? (
							` At this pace you'll hit it around ${formatDayLabel(projection.reachedOn)}.`
						) : (
							// The month-end figure converts too: one sentence that quotes "$43.90 of $50
							// (≈ €38.59 of €44)" and then a bare "$61.20" makes the reader switch currencies
							// mid-breath.
							<>
								{` At this pace you'll finish the month around ${formatCostUsd(projection.projectedMonthEndUsd)}`}
								<FxAmount conversion={spendConversion(projection.projectedMonthEndUsd, fx)} />.
							</>
						))}
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
	/** The estimate that trails the headline, or `null` when there is nothing to convert. */
	titleFx: FxConversion | null;
}

/** Read-only by design: only the host can move this number. */
function SharedBudgetCard({
	isCurrentMonth,
	spendUsd,
	capUsd,
	percent,
	paused,
	workspaceSlug,
	titleFx,
}: SharedBudgetCardProps) {
	return (
		<Card>
			<CardHeader>
				<CardDescription>
					{isCurrentMonth ? "Shared-model spend so far" : "Shared-model spend"}
				</CardDescription>
				<CardTitle className="text-2xl tabular-nums">
					{formatCostUsd(spendUsd)}
					{capUsd != null ? (
						<span className="text-base font-normal text-muted-foreground">
							{" "}
							of {formatCapUsd(capUsd)}
							<FxAmount conversion={titleFx} />
						</span>
					) : (
						// Without a cap there is no muted "of …" tail to hang the estimate on, so the wrapper
						// only exists when there is actually an estimate to put in it.
						titleFx != null && (
							<span className="text-base font-normal text-muted-foreground">
								<FxAmount conversion={titleFx} />
							</span>
						)
					)}
				</CardTitle>
				<CardDescription>
					{capUsd != null
						? "Shared-model budget · set by your host"
						: "No shared-model budget set by your host"}
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-3">
				{/* Whose budget this is has already been said once, in the description above. */}
				{percent != null && (
					<BudgetMeter
						percent={percent}
						paused={paused}
						spendUsd={spendUsd}
						capUsd={capUsd}
						label="Shared-model budget used"
					/>
				)}
				<p className="text-sm text-muted-foreground">
					Move a purpose to your own provider in <AiModelsLink workspaceSlug={workspaceSlug} />.
				</p>
			</CardContent>
		</Card>
	);
}

interface ProviderCapCardProps {
	isCurrentMonth: boolean;
	spendUsd: number;
	capUsd: number | undefined;
	percent: number | undefined;
	paused: boolean;
	onEditOwnProviderCap: () => void;
	/** The estimate that trails the headline, or `null` when there is nothing to convert. */
	titleFx: FxConversion | null;
}

/** The workspace's own money: set, change, and remove all live here. */
function ProviderCapCard({
	isCurrentMonth,
	spendUsd,
	capUsd,
	percent,
	paused,
	onEditOwnProviderCap,
	titleFx,
}: ProviderCapCardProps) {
	return (
		<Card>
			<CardHeader>
				<CardDescription>
					{isCurrentMonth ? "Your provider spend so far" : "Your provider spend"}
				</CardDescription>
				<CardTitle className="text-2xl tabular-nums">
					{formatCostUsd(spendUsd)}
					{capUsd != null ? (
						<span className="text-base font-normal text-muted-foreground">
							{" "}
							of {formatCapUsd(capUsd)}
							<FxAmount conversion={titleFx} />
						</span>
					) : (
						titleFx != null && (
							<span className="text-base font-normal text-muted-foreground">
								<FxAmount conversion={titleFx} />
							</span>
						)
					)}
				</CardTitle>
				<CardDescription>Billed to your own provider key.</CardDescription>
			</CardHeader>
			<CardContent className="space-y-3">
				{percent != null && (
					<BudgetMeter
						percent={percent}
						paused={paused}
						spendUsd={spendUsd}
						capUsd={capUsd}
						label="Your provider cap used"
					/>
				)}
				{/* A set cap is already the "of $50" in the headline and the meter under it; only its
				    absence still needs saying. */}
				{capUsd == null && <p className="text-sm text-muted-foreground">No cap set.</p>}
				<Button variant="outline" size="sm" onClick={onEditOwnProviderCap}>
					{capUsd != null ? "Change cap" : "Set cap"}
				</Button>
			</CardContent>
		</Card>
	);
}

interface NoProviderCardProps {
	workspaceSlug: string;
	onEditOwnProviderCap: () => void;
}

/** Quiet call-to-action: no provider cap and nothing has run on a provider of their own. */
function NoProviderCard({ workspaceSlug, onEditOwnProviderCap }: NoProviderCardProps) {
	return (
		<Card>
			<CardHeader>
				<CardDescription>Your provider spend</CardDescription>
				<CardTitle className="text-2xl tabular-nums">No spend</CardTitle>
				<CardDescription>
					Nothing ran on a provider of your own this month. Connect one in{" "}
					<AiModelsLink workspaceSlug={workspaceSlug} /> to cap what it spends yourself.
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-3">
				<p className="text-sm text-muted-foreground">No cap set.</p>
				<Button variant="outline" size="sm" onClick={onEditOwnProviderCap}>
					Set cap
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
 * once the cap is reached or the pause is live. The tone never carries the state alone: the line
 * underneath names it in words too (WCAG SC 1.4.1).
 */
function BudgetMeter({ percent, paused, spendUsd, capUsd, label }: BudgetMeterProps) {
	const value = Math.min(Math.max(percent, 0), 100);
	const rounded = Math.round(percent);
	const state = paused ? "Paused" : percent >= BUDGET_WARN_PERCENT ? "Near cap" : null;
	// Comma, not an em-dash: screen readers render an em-dash inconsistently — some spell it out.
	const valueText = `${rounded}% used, ${formatCostUsd(spendUsd)} of ${formatCapUsd(capUsd)}`;
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
			<p className="text-sm text-muted-foreground tabular-nums">
				{rounded}% used{state != null && ` · ${state}`}
			</p>
		</div>
	);
}
