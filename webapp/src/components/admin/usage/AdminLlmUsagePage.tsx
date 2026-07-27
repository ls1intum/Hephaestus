import { Link } from "@tanstack/react-router";
import { CircleAlert, CircleDollarSign } from "lucide-react";
import { useId } from "react";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { BudgetExhaustedAlert } from "@/components/admin/ai/BudgetExhaustedAlert";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyContent, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCapUsd, formatCostUsd } from "@/lib/money";
import { BudgetPaceAlert } from "./BudgetPaceAlert";
import { CapIsNotMonthScoped } from "./CapIsNotMonthScoped";
import { CAP_STATE_LABELS, CapMeter, capState } from "./CapMeter";
import {
	type Fx,
	FxAmount,
	type FxConversion,
	FxDisclosure,
	spendConversion,
	spendOfCapConversion,
} from "./fx";
import { LlmUsageByDayTable, LlmUsageByJobTypeTable } from "./LlmUsageBreakdownTables";
import { MonthNavigator } from "./MonthNavigator";
import { budgetUsedPercent, formatMonthLabel, projectBudget } from "./usage-utils";

export interface AdminLlmUsagePageProps {
	/** ISO `yyyy-MM` month currently shown. */
	month: string;
	/** Whether `month` is the current calendar month (UTC) — gates every pause/pace banner. */
	isCurrentMonth: boolean;
	/**
	 * Whether the stepper may move forward. Separate from {@link isCurrentMonth} rather than its
	 * negation: both are false on a month later than this one, and only the route knows the difference.
	 */
	canGoNext: boolean;
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
	/**
	 * Open the provider-cap editor. The dialog itself lives in the route container.
	 *
	 * Reachable on the current month only — see {@link CapIsNotMonthScoped}.
	 */
	onEditOwnProviderCap: () => void;
	/** Injected so burn-rate projections are deterministic in tests and stories. */
	now?: Date;
}

/**
 * Workspace-admin cost control for one month.
 *
 * The workspace lives under two independent caps that are different people's money and are never
 * summed (`docs/contributor/llm-cost-vocabulary.md`, rule 2): the *shared-model budget* its host sets and funds, and the
 * *provider cap* the workspace sets on its own provider. They pause independently, so every banner
 * here names whose cap tripped and routes to whoever can lift it — the workspace admin can act on
 * their own cap, and on the host's they can only move a purpose across.
 *
 * Pure/presentational — the route container owns the query, the selected month, and the dialog.
 */
export function AdminLlmUsagePage({
	month,
	isCurrentMonth,
	canGoNext,
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
	// underneath it would just say the same thing more quietly. `capState` is the one place that
	// decides this, shared with the instance console.
	const providerWarning =
		capState(providerPercent, providerPaused, isCurrentMonth) === "near" ? providerPercent : null;
	const sharedWarning =
		capState(sharedPercent, sharedPaused, isCurrentMonth) === "near" ? sharedPercent : null;
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
	// Every converted figure on the page, not just the cards: the breakdown tables convert their own
	// footer totals, and a card can convert nothing (a $0 budget, say — the supported "pause now"
	// state). Only figures that actually render count, so the "EUR amounts are estimates…" footnote
	// never appears under a page with no estimates on it.
	const hasConversion =
		sharedTitleFx != null ||
		providerTitleFx != null ||
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
					canGoNext={canGoNext}
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
							<CardTitle>By run type</CardTitle>
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

					{providerWarning != null && (
						<BudgetPaceAlert
							scope="provider"
							percent={providerWarning}
							spendUsd={providerSpend}
							capUsd={providerCap}
							projection={projectBudget(providerSpend, providerCap, month, now)}
							fx={fx}
						/>
					)}
					{sharedWarning != null && (
						<BudgetPaceAlert
							scope="shared"
							percent={sharedWarning}
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
									? "1 run isn't counted in these totals"
									: `${unpricedEventCount.toLocaleString()} runs aren't counted in these totals`}
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
								isCurrentMonth={isCurrentMonth}
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
									<CardTitle>By run type</CardTitle>
								</CardHeader>
								<CardContent>
									<LlmUsageByJobTypeTable report={report} fx={fx} />
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
										<LlmUsageByDayTable report={report} fx={fx} />
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

interface SharedBudgetCardProps {
	isCurrentMonth: boolean;
	spendUsd: number;
	capUsd: number | undefined;
	percent: number | undefined;
	paused: boolean;
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
	titleFx,
}: SharedBudgetCardProps) {
	const labelId = useId();
	return (
		// A named region, so the card is reachable in the accessible tree — "which purse is this
		// figure in" is the whole question this page answers, and the answer is the card's own label.
		<Card role="region" aria-labelledby={labelId}>
			<CardHeader>
				<CardDescription id={labelId}>
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
				{percent != null && capUsd != null && (
					<CapMeterWithCaption
						percent={percent}
						paused={paused}
						isCurrentMonth={isCurrentMonth}
						spendUsd={spendUsd}
						capUsd={capUsd}
						label="Shared-model budget used"
					/>
				)}
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
	const labelId = useId();
	return (
		<Card role="region" aria-labelledby={labelId}>
			<CardHeader>
				<CardDescription id={labelId}>
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
				{/* Says which cap bounds the figure above and who owns it, in the same shape as the
				    shared-model card's line — the two purses are only told apart by these two sentences. */}
				<CardDescription>
					{capUsd != null
						? "Provider cap · set by you, billed by your provider"
						: "No provider cap set · billed to you by your provider"}
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-3">
				{percent != null && capUsd != null && (
					<CapMeterWithCaption
						percent={percent}
						paused={paused}
						isCurrentMonth={isCurrentMonth}
						spendUsd={spendUsd}
						capUsd={capUsd}
						label="Your provider cap used"
					/>
				)}
				{isCurrentMonth ? (
					<Button variant="outline" size="sm" onClick={onEditOwnProviderCap}>
						{capUsd != null ? "Change cap" : "Set cap"}
					</Button>
				) : (
					<CapIsNotMonthScoped subject="cap" />
				)}
			</CardContent>
		</Card>
	);
}

interface NoProviderCardProps {
	isCurrentMonth: boolean;
	workspaceSlug: string;
	onEditOwnProviderCap: () => void;
}

/** Quiet call-to-action: no provider cap and nothing has run on a provider of their own. */
function NoProviderCard({
	isCurrentMonth,
	workspaceSlug,
	onEditOwnProviderCap,
}: NoProviderCardProps) {
	const labelId = useId();
	return (
		<Card role="region" aria-labelledby={labelId}>
			<CardHeader>
				<CardDescription id={labelId}>Your provider spend</CardDescription>
				{/* Zero money is `$0` on every card on this page — a second vocabulary for it would read
				    as a different kind of number. */}
				<CardTitle className="text-2xl tabular-nums">{formatCostUsd(0)}</CardTitle>
				<CardDescription>
					No provider cap set · nothing has run on a provider of your own
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-3">
				<p className="text-sm text-muted-foreground">
					Connect your own provider in <AiModelsLink workspaceSlug={workspaceSlug} /> to bill AI
					work to your own account.
				</p>
				{isCurrentMonth ? (
					<Button variant="outline" size="sm" onClick={onEditOwnProviderCap}>
						Set cap
					</Button>
				) : (
					<CapIsNotMonthScoped subject="cap" />
				)}
			</CardContent>
		</Card>
	);
}

interface CapMeterWithCaptionProps {
	percent: number;
	paused: boolean;
	isCurrentMonth: boolean;
	spendUsd: number;
	capUsd: number;
	label: string;
}

/**
 * The shared meter plus this page's caption. The card has the width for a percentage in prose, so
 * the amounts stay in the headline above and are not repeated here; the state word comes from
 * {@link CAP_STATE_LABELS} so it cannot drift from the instance console's cell.
 */
function CapMeterWithCaption({
	percent,
	paused,
	isCurrentMonth,
	spendUsd,
	capUsd,
	label,
}: CapMeterWithCaptionProps) {
	const state = capState(percent, paused, isCurrentMonth);
	return (
		<div className="space-y-1.5">
			<CapMeter
				percent={percent}
				paused={paused}
				spendUsd={spendUsd}
				capUsd={capUsd}
				label={label}
			/>
			<p className="text-sm text-muted-foreground tabular-nums">
				{Math.round(percent)}% used{state != null && ` · ${CAP_STATE_LABELS[state]}`}
			</p>
		</div>
	);
}
