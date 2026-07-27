import { ChevronDown, ChevronRight, CircleDollarSign, Info } from "lucide-react";
import type { AdminWorkspaceLlmUsage, WorkspaceLlmUsageReport } from "@/api/types.gen";
import { MoneyCell } from "@/components/admin/ai/job-utils";
import { TableRowsSkeleton } from "@/components/admin/integrations/TableRowsSkeleton";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import {
	Table,
	TableBody,
	TableCaption,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatCapUsd, formatCostUsd } from "@/lib/money";
import { BudgetPaceAlert } from "./BudgetPaceAlert";
import { CapIsNotMonthScoped } from "./CapIsNotMonthScoped";
import { CAP_STATE_LABELS, CapMeter, type CapState, capState } from "./CapMeter";
import { type Fx, FxDisclosure, FxSpendLine, spendConversion } from "./fx";
import { LlmUsageByDayTable, LlmUsageByJobTypeTable } from "./LlmUsageBreakdownTables";
import { budgetUsedPercent, projectBudget } from "./usage-utils";

export interface AdminInstanceLlmUsageTableProps {
	/**
	 * Per-workspace month rollups, already sorted by the container (cost desc).
	 *
	 * Each row carries both caps, which are different people's money and are never summed:
	 * `instanceMonthlyBudgetUsd` is the *shared-model budget* an instance admin grants over
	 * host-funded spend, and the `ownProvider*` fields are the *provider cap* the workspace's own
	 * admins set over spend on their own provider. They pause independently. The names and meanings
	 * are the per-workspace report's, so a row renders identically on either surface.
	 */
	rows: AdminWorkspaceLlmUsage[];
	/** ISO `yyyy-MM` month shown; the burn-rate projections in the detail panel are scoped to it. */
	month: string;
	/** Injected so those projections are deterministic in tests and stories. */
	now?: Date;
	/**
	 * The month's display-currency rate, from the report envelope rather than from any row. One month
	 * resolves to exactly one rate, and it applies to a month with no workspaces in it — where there
	 * is no `rows[0]` to read it off — just as much as to a busy one.
	 */
	fx?: Fx;
	/**
	 * Whether the shown month is the current calendar month (UTC). The verdicts compare a
	 * workspace's *current* caps against the selected month's spend, so they only describe a real
	 * pause for the current month — past months show a neutral status instead.
	 */
	isCurrentMonth: boolean;
	isLoading: boolean;
	/** The thrown request error, if the rollup failed to load. */
	error: unknown;
	/** Retry the failed rollup load. */
	onRetry?: () => void;
	/** The workspace whose detail row is expanded, or null when all rows are collapsed. */
	expandedWorkspaceSlug: string | null;
	/** Detailed rollup for the expanded workspace. */
	detailReport?: WorkspaceLlmUsageReport;
	isDetailLoading: boolean;
	detailError: unknown;
	/** Retry the expanded workspace report. */
	onRetryDetail?: () => void;
	onToggleDetails: (workspace: AdminWorkspaceLlmUsage) => void;
	/**
	 * Edit the *shared-model budget*. There is deliberately no counterpart for the provider cap.
	 *
	 * Offered on the current month only — see the action cell for why.
	 */
	onEditBudget: (workspace: AdminWorkspaceLlmUsage) => void;
}

/** One entry per header column — the trailing action slot promises nothing. */
const SKELETON_COLUMNS = ["w-32", "w-16", "w-24", "w-16", "w-24", "w-28", "w-12", null];

/** Stable target for the toggle's `aria-controls` and the panel's own `id`. */
function detailPanelId(workspaceSlug: string): string {
	return `workspace-usage-details-${workspaceSlug}`;
}

/** One money stream (shared models or the workspace's provider) measured against its own cap. */
interface CapUsage {
	/** The cap in USD, or undefined when this stream is uncapped / not reported. */
	cap?: number;
	spend: number;
	/** Share of the cap consumed, or undefined when uncapped. Can exceed 100. */
	percent?: number;
	/** Whether *this* cap is currently holding work back. */
	paused: boolean;
	/** Whether this cap is a state worth naming, from the shared {@link capState}. */
	state: CapState;
	/** Some usage on this stream has no price set, so the spend shown is a floor. */
	unverifiable: boolean;
}

/**
 * Fold one stream's cap, spend and verdict into what the row renders.
 *
 * `paused` is the server's own flag, which the rollup carries per stream. A past month can never be
 * paused: the verdict compares the *current* cap against a finished month.
 */
function capUsage(input: {
	cap?: number;
	spend: number;
	verdict?: WorkspaceLlmUsageReport["instanceBudgetVerdict"];
	paused: boolean;
	isCurrentMonth: boolean;
}): CapUsage {
	const { cap, spend, verdict, paused, isCurrentMonth } = input;
	const percent = budgetUsedPercent(spend, cap);
	const isPaused = isCurrentMonth && paused;
	return {
		cap,
		spend,
		percent,
		paused: isPaused,
		state: capState(percent, isPaused, isCurrentMonth),
		unverifiable: isCurrentMonth && verdict === "UNVERIFIABLE",
	};
}

/**
 * Instance-admin table of every workspace's AI spend for one month (metadata only, no tenant
 * content), against both caps: the shared-model budget the host grants and funds, and the provider
 * cap the workspace's own admins set over spend on their own provider. Pure/presentational —
 * budget edits are raised to the container via `onEditBudget`; the provider cap is read-only here
 * by design.
 */
export function AdminInstanceLlmUsageTable({
	rows,
	month,
	now = new Date(),
	fx,
	isCurrentMonth,
	isLoading,
	error,
	onRetry,
	expandedWorkspaceSlug,
	detailReport,
	isDetailLoading,
	detailError,
	onRetryDetail,
	onToggleDetails,
	onEditBudget,
}: AdminInstanceLlmUsageTableProps) {
	if (error != null) {
		return <QueryErrorAlert error={error} title="Couldn't load AI usage" onRetry={onRetry} />;
	}
	if (rows.length === 0 && !isLoading) {
		return (
			<Empty className="border">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<CircleDollarSign />
					</EmptyMedia>
					{/* The rollup left-joins from workspace, so zero rows means zero workspaces. */}
					<EmptyTitle>No workspaces on this instance yet</EmptyTitle>
				</EmptyHeader>
			</Empty>
		);
	}

	// Only one workspace can be expanded at a time, so the detail lives *beside* the table rather than
	// in a `colSpan` row inside it. A nested panel inherits the table's ~1100 px width, and its own two
	// tables then each open a second horizontal scroller inside the first — two-dimensional scrolling
	// to read a breakdown, which is exactly what WCAG 2.2 SC 1.4.10 rules out. Out here it reflows to
	// the page width at any viewport and the `aria-controls` relationship is unchanged.
	const expandedRow = rows.find((row) => row.workspaceSlug === expandedWorkspaceSlug);
	// The caption explains estimates that are actually on screen; with every workspace at $0 there
	// are none, and a footnote about them would be noise. Both spend columns convert, so both count
	// — a page that converted only provider spend still owes the reader its rate.
	const hasConversion = rows.some(
		(row) =>
			spendConversion(row.instanceTotalCostUsd, fx) != null ||
			spendConversion(row.ownProviderTotalCostUsd, fx) != null,
	);

	return (
		<div className="space-y-4">
			{/* Every row loses its "Set budget" button on a closed month. Said once above the table
			    rather than fifty times inside it — but said, because a control that is simply absent
			    explains nothing. */}
			{!isCurrentMonth && <CapIsNotMonthScoped subject="budget" />}
			<Table containerClassName="rounded-md border">
				<TableCaption className="sr-only">
					Per-workspace AI spend for the selected month, most expensive first
				</TableCaption>
				<TableHeader>
					<TableRow>
						<TableHead scope="col">Workspace</TableHead>
						<TableHead scope="col" className="text-right">
							Shared-model spend
						</TableHead>
						{/* Spend sits next to the cap it is measured against, so a row reads left to right as
					    "this much, out of this much" without the admin holding a number in their head. */}
						<TableHead scope="col" className="text-right">
							<HelpHeader help="The monthly cap you set on the spend you pay for.">
								Shared-model budget
							</HelpHeader>
						</TableHead>
						<TableHead scope="col" className="text-right">
							Provider spend
						</TableHead>
						<TableHead scope="col" className="text-right">
							<HelpHeader help="The workspace's own money. Only its admins can change this.">
								Provider cap
							</HelpHeader>
						</TableHead>
						<TableHead scope="col">Status</TableHead>
						<TableHead scope="col" className="text-right">
							Runs
						</TableHead>
						<TableHead scope="col">
							<span className="sr-only">Actions</span>
						</TableHead>
					</TableRow>
				</TableHeader>
				{isLoading ? (
					<TableRowsSkeleton columns={SKELETON_COLUMNS} rows={5} />
				) : (
					<TableBody>
						{rows.map((row) => {
							const isExpanded = expandedWorkspaceSlug === row.workspaceSlug;
							const shared = capUsage({
								cap: row.instanceMonthlyBudgetUsd,
								spend: row.instanceTotalCostUsd,
								verdict: row.instanceBudgetVerdict,
								paused: row.instancePaused,
								isCurrentMonth,
							});
							const provider = capUsage({
								cap: row.ownProviderMonthlyBudgetUsd,
								spend: row.ownProviderTotalCostUsd,
								verdict: row.ownProviderBudgetVerdict,
								paused: row.ownProviderPaused,
								isCurrentMonth,
							});
							return (
								<TableRow key={row.workspaceSlug}>
									<TableCell>
										<div className="font-medium">{row.displayName}</div>
										<div className="font-mono text-xs text-muted-foreground">
											{row.workspaceSlug}
										</div>
									</TableCell>
									{/* Both spend columns carry the estimate, on a second line where it costs no
									    column width. Converting one and not the other would read as "these two are
									    different in kind" — they are the same physical quantity, differently funded.
									    The cap columns stay USD-only: a cap is a number someone typed in USD. */}
									<TableCell className="text-right tabular-nums">
										<MoneyCell>{formatCostUsd(row.instanceTotalCostUsd)}</MoneyCell>
										<FxSpendLine usd={row.instanceTotalCostUsd} fx={fx} />
									</TableCell>
									<CapCell usage={shared} label="Shared-model budget" workspace={row.displayName} />
									<TableCell className="text-right tabular-nums">
										<MoneyCell>{formatCostUsd(row.ownProviderTotalCostUsd)}</MoneyCell>
										<FxSpendLine usd={row.ownProviderTotalCostUsd} fx={fx} />
									</TableCell>
									<CapCell usage={provider} label="Provider cap" workspace={row.displayName} />
									<TableCell>
										<StatusCell
											shared={shared}
											provider={provider}
											isCurrentMonth={isCurrentMonth}
										/>
									</TableCell>
									<TableCell className="text-right tabular-nums">
										{row.events.toLocaleString()}
									</TableCell>
									<TableCell>
										<div className="flex justify-end gap-2">
											<Button
												variant="outline"
												size="sm"
												aria-expanded={isExpanded}
												// The detail panel only exists while expanded, so pointing at it
												// beforehand would be a dangling IDREF.
												aria-controls={isExpanded ? detailPanelId(row.workspaceSlug) : undefined}
												aria-label={`${isExpanded ? "Hide" : "View"} usage details for ${row.displayName}`}
												onClick={() => onToggleDetails(row)}
											>
												{isExpanded ? <ChevronDown aria-hidden /> : <ChevronRight aria-hidden />}
												Details
											</Button>
											{/* Only the shared-model budget gets an edit control — the provider cap is the
											    workspace's own money and its own admins' call. The visible label stays
											    short for the column; the accessible name says which workspace it edits and
											    which of the two purses, since every row's button reads the same otherwise
											    — and it opens with the visible label, so speech control ("click Set
											    budget") still matches it (WCAG 2.2 SC 2.5.3 Label in Name).
											    Current month only: a budget is not month-scoped, so editing one from a
											    closed month would quietly change what runs today while the reader is
											    looking at history. Why it is gone is said once above the table. */}
											{isCurrentMonth && (
												<Button
													variant="outline"
													size="sm"
													aria-label={`Set budget for ${row.displayName} (shared models)`}
													onClick={() => onEditBudget(row)}
												>
													Set budget
												</Button>
											)}
										</div>
									</TableCell>
								</TableRow>
							);
						})}
					</TableBody>
				)}
			</Table>

			{expandedRow != null && (
				<WorkspaceUsageDetails
					workspace={expandedRow}
					report={detailReport}
					isLoading={isDetailLoading}
					error={detailError}
					onRetry={onRetryDetail}
					fx={fx}
					month={month}
					now={now}
					isCurrentMonth={isCurrentMonth}
				/>
			)}

			{/* Last, after every figure it qualifies — a footnote that precedes its numbers is read as a
			    preamble to something else. */}
			{hasConversion && <FxDisclosure fx={fx} isCurrentMonth={isCurrentMonth} />}
		</div>
	);
}

interface WorkspaceUsageDetailsProps {
	workspace: AdminWorkspaceLlmUsage;
	report?: WorkspaceLlmUsageReport;
	isLoading: boolean;
	error: unknown;
	onRetry?: () => void;
	/**
	 * The table's rate, handed down rather than read off the detail report. Both responses describe
	 * the same month and so resolve to the same rate — but nothing enforces that, and two rates on
	 * one screen is a bug nobody would spot. One page, one rate, one disclosure.
	 */
	fx: Fx;
	month: string;
	now: Date;
	isCurrentMonth: boolean;
}

/**
 * The expanded workspace's breakdowns, rendered under the table rather than inside it.
 *
 * It names the workspace in its own heading, since it sits outside the row that opened it — the
 * `aria-expanded`/`aria-controls` pair on that row's toggle is what ties the two together for
 * assistive tech.
 *
 * The two breakdown tables stack until `xl`: side by side they would each be too narrow to avoid a
 * horizontal scroller of their own, and two scrollers to read one number on one screen is
 * two-dimensional scrolling (WCAG 2.2 SC 1.4.10).
 */
function WorkspaceUsageDetails({
	workspace,
	report,
	isLoading,
	error,
	onRetry,
	fx,
	month,
	now,
	isCurrentMonth,
}: WorkspaceUsageDetailsProps) {
	const panelId = detailPanelId(workspace.workspaceSlug);
	// The projection the rollup row can't carry: a cap at 84% is only alarming once you know the
	// month's pace reaches it. Read off the detail report rather than the row, so the figures under
	// the alert are the same ones the breakdowns below it add up to.
	const paces =
		report == null
			? []
			: (
					[
						{
							scope: "shared" as const,
							spend: report.instanceTotalCostUsd,
							cap: report.instanceMonthlyBudgetUsd,
							paused: report.instancePaused,
						},
						{
							scope: "provider" as const,
							spend: report.ownProviderTotalCostUsd,
							cap: report.ownProviderMonthlyBudgetUsd,
							paused: report.ownProviderPaused,
						},
					] as const
				).flatMap((stream) => {
					const percent = budgetUsedPercent(stream.spend, stream.cap);
					if (percent == null || capState(percent, stream.paused, isCurrentMonth) !== "near") {
						return [];
					}
					return [{ ...stream, percent }];
				});
	return (
		<section
			id={panelId}
			aria-labelledby={`${panelId}-heading`}
			className="space-y-4 rounded-md border bg-muted/20 p-4"
		>
			{/* One level below the page's own `h1`: `CardTitle` is a `<div>`, so nothing between them
			    contributes to the outline and an `h3` here would skip a level (WCAG SC 1.3.1). */}
			<h2 id={`${panelId}-heading`} className="font-medium">
				Usage details · {workspace.displayName}
			</h2>
			{error != null ? (
				<QueryErrorAlert
					error={error}
					title={`Couldn't load usage details for ${workspace.displayName}`}
					onRetry={onRetry}
				/>
			) : (
				<>
					{paces.map((pace) => (
						<BudgetPaceAlert
							key={pace.scope}
							scope={pace.scope}
							subjectName={workspace.displayName}
							percent={pace.percent}
							spendUsd={pace.spend}
							capUsd={pace.cap}
							projection={projectBudget(pace.spend, pace.cap, month, now)}
							fx={fx}
						/>
					))}
					<div className="grid gap-4 xl:grid-cols-2">
						<section aria-labelledby={`${panelId}-run-type`} className="min-w-0 space-y-2">
							<h3 id={`${panelId}-run-type`} className="font-medium">
								By run type
							</h3>
							<LlmUsageByJobTypeTable report={isLoading ? undefined : report} fx={fx} />
						</section>
						<section aria-labelledby={`${panelId}-day`} className="min-w-0 space-y-2">
							<h3 id={`${panelId}-day`} className="font-medium">
								By day
							</h3>
							<LlmUsageByDayTable report={isLoading ? undefined : report} fx={fx} />
						</section>
					</div>
				</>
			)}
		</section>
	);
}

interface HelpHeaderProps {
	children: string;
	/** Why this column exists — the two caps are easy to confuse, so each says whose money it is. */
	help: string;
}

/**
 * A column header that explains itself on hover/focus. The header text is the trigger rather than a
 * separate icon button, so the column's accessible name stays exactly the visible label.
 *
 * `min-h-6` is 24 px: the trigger is an interactive target, and at the header's ~20 px line height
 * it would otherwise conform to WCAG 2.2 SC 2.5.8 only through the Spacing exception — which holds
 * today but depends on nothing here, so a row of narrower columns would silently break it.
 */
function HelpHeader({ children, help }: HelpHeaderProps) {
	return (
		<Tooltip>
			<TooltipTrigger className="inline-flex min-h-6 cursor-help items-center gap-1 font-medium">
				{children}
				<Info className="size-3 text-muted-foreground" aria-hidden />
			</TooltipTrigger>
			<TooltipContent>{help}</TooltipContent>
		</Tooltip>
	);
}

interface CapCellProps {
	usage: CapUsage;
	/** Which cap this is, for the meter's accessible name. */
	label: string;
	workspace: string;
}

/**
 * A cap as an amount *and* a fill: "how close is this workspace" is the question an admin actually
 * has, and a binary in-budget pill can only answer it once the trouble has already arrived.
 */
function CapCell({ usage, label, workspace }: CapCellProps) {
	if (usage.cap == null) {
		return (
			<TableCell className="text-right">
				<span className="text-muted-foreground">—</span>
			</TableCell>
		);
	}

	const percent = usage.percent ?? 0;
	const rounded = Math.round(percent);

	return (
		<TableCell className="text-right">
			<div className="ml-auto flex w-24 flex-col items-end gap-1">
				<span className="tabular-nums">
					<MoneyCell>{formatCapUsd(usage.cap)}</MoneyCell>
				</span>
				<CapMeter
					percent={percent}
					paused={usage.paused}
					spendUsd={usage.spend}
					capUsd={usage.cap}
					label={`${label} used by ${workspace}`}
				/>
				{/* The tone never carries the state alone — this line says it in words (WCAG SC 1.4.1),
				    in the same words the workspace's own console uses. */}
				<span className="text-xs text-muted-foreground tabular-nums">
					<MoneyCell>{formatCostUsd(usage.spend)}</MoneyCell> · {rounded}%
					{usage.state != null && ` · ${CAP_STATE_LABELS[usage.state]}`}
				</span>
			</div>
		</TableCell>
	);
}

interface StatusCellProps {
	shared: CapUsage;
	provider: CapUsage;
	isCurrentMonth: boolean;
}

interface StatusBadge {
	key: string;
	label: string;
	variant: "destructive" | "warning";
}

/**
 * Which cap, if any, is currently holding the workspace back. Naming the cap is the point: the
 * admin can only raise one of the two, and the other one is not theirs to touch.
 */
function StatusCell({ shared, provider, isCurrentMonth }: StatusCellProps) {
	if (!isCurrentMonth) {
		return <span className="text-muted-foreground">—</span>;
	}

	// Both caps can be spent at once, and an admin who only sees "Paused" fields a ticket they
	// cannot answer — so each cap contributes its own badge and they stack. Each names the money
	// stream it belongs to, in the same words as the two spend columns.
	// The state word comes from {@link CAP_STATE_LABELS}, the same constant the cap cells and the
	// workspace's own console read, so the two consoles cannot drift into synonyms.
	const badges: StatusBadge[] = [];
	if (shared.state === "paused") {
		badges.push({
			key: "paused-shared",
			label: `${CAP_STATE_LABELS.paused} · shared models`,
			variant: "destructive",
		});
	}
	if (provider.state === "paused") {
		badges.push({
			key: "paused-provider",
			label: `${CAP_STATE_LABELS.paused} · own provider`,
			variant: "destructive",
		});
	}
	if (shared.state === "near") {
		badges.push({
			key: "near-shared",
			label: `${CAP_STATE_LABELS.near} · shared models`,
			variant: "warning",
		});
	}
	if (provider.state === "near") {
		badges.push({
			key: "near-provider",
			label: `${CAP_STATE_LABELS.near} · own provider`,
			variant: "warning",
		});
	}
	const noPriceSet = shared.unverifiable || provider.unverifiable;

	// Nothing to report is not a state: a workspace inside both caps is already fully described by
	// the two cap cells beside this one, which show the amounts.
	if (badges.length === 0 && !noPriceSet) {
		return <span className="text-muted-foreground">—</span>;
	}

	return (
		<div className="flex flex-col items-start gap-1">
			{badges.map((badge) => (
				<Badge key={badge.key} variant={badge.variant}>
					{badge.label}
				</Badge>
			))}
			{noPriceSet && (
				// Not a badge — "can't be verified" is a sentence about the data, never a state name.
				<span className="text-warning text-xs">Some runs have no price set</span>
			)}
		</div>
	);
}
