import { Progress as ProgressRoot } from "@base-ui/react/progress";
import { ChevronDown, ChevronRight, CircleDollarSign, Info } from "lucide-react";
import type { AdminWorkspaceLlmUsage, WorkspaceLlmUsageReport } from "@/api/types.gen";
import { formatCapUsd, formatCostUsd, MoneyCell } from "@/components/admin/ai/jobUtils";
import { TableRowsSkeleton } from "@/components/admin/integrations/TableRowsSkeleton";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Empty, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { ProgressIndicator, ProgressTrack } from "@/components/ui/progress";
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
import { type Fx, FxDisclosure, FxSpendLine, spendConversion } from "./fx";
import { LlmUsageByDayTable, LlmUsageByJobTypeTable } from "./LlmUsageBreakdownTables";

/**
 * The admin rollup row, widened with the workspace's provider-cap fields.
 *
 * The two caps are different people's money and are never summed: `instanceMonthlyBudgetUsd` is the
 * *instance cap* an instance admin sets over host-funded shared-model spend, while the `ownProvider*` fields
 * below describe the *provider cap* the workspace's own admins set over spend on their own provider.
 * They pause independently.
 *
 * The admin rollup reports both caps with the same names and meanings the per-workspace report
 * uses, so a row renders identically on either surface.
 */
export type AdminWorkspaceLlmUsageRow = AdminWorkspaceLlmUsage;

export interface AdminInstanceLlmUsageTableProps {
	/** Per-workspace month rollups, already sorted by the container (cost desc). */
	rows: AdminWorkspaceLlmUsageRow[];
	/**
	 * The month's display-currency rate, from the report envelope rather than from any row. One month
	 * resolves to exactly one rate, and it applies to a month with no workspaces in it just as much as
	 * to a busy one — which is what reading it off `rows[0]` used to get wrong.
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
	onToggleDetails: (workspace: AdminWorkspaceLlmUsageRow) => void;
	/** Edit the *instance* cap. There is deliberately no counterpart for the provider cap. */
	onEditBudget: (workspace: AdminWorkspaceLlmUsageRow) => void;
}

/** One entry per header column — the trailing action slot promises nothing. */
const SKELETON_COLUMNS = ["w-32", "w-16", "w-24", "w-16", "w-24", "w-28", "w-12", null];

/** Stable target for the toggle's `aria-controls` and the panel's own `id`. */
function detailPanelId(workspaceSlug: string): string {
	return `workspace-usage-details-${workspaceSlug}`;
}

/**
 * Where a cap stops being a background fact and becomes something an admin may want to act on
 * *before* work pauses. Below it the meter is enough; at or above it the row earns a badge.
 */
const NEAR_CAP_PERCENT = 80;

/** One money stream (shared models or the workspace's provider) measured against its own cap. */
interface CapUsage {
	/** The cap in USD, or undefined when this stream is uncapped / not reported. */
	cap?: number;
	spend: number;
	/** Share of the cap consumed, or undefined when uncapped. Can exceed 100. */
	percent?: number;
	/** Whether *this* cap is currently holding work back. */
	paused: boolean;
	/** At or above {@link NEAR_CAP_PERCENT} but not yet paused. */
	nearCap: boolean;
	/** Some usage on this stream has no price set, so the spend shown is a floor. */
	unverifiable: boolean;
}

/**
 * Fold one stream's cap, spend and verdict into what the row renders.
 *
 * `paused` prefers the authoritative server flag when present and otherwise falls back to the
 * verdict, which is the only signal the admin rollup carries today. Either way a past month can
 * never be paused: the verdict compares the *current* cap against a finished month.
 */
function capUsage(input: {
	cap?: number;
	spend: number;
	verdict?: WorkspaceLlmUsageReport["instanceBudgetVerdict"];
	paused?: boolean;
	isCurrentMonth: boolean;
}): CapUsage {
	const { cap, spend, verdict, paused, isCurrentMonth } = input;
	// A $0 cap is a supported state ("paused immediately"), so it reads as 100% used — only an
	// absent cap has no percentage to show.
	const percent = cap == null ? undefined : cap > 0 ? (spend / cap) * 100 : 100;
	const isPaused = isCurrentMonth && (paused ?? verdict === "EXHAUSTED");
	return {
		cap,
		spend,
		percent,
		paused: isPaused,
		nearCap: !isPaused && isCurrentMonth && percent != null && percent >= NEAR_CAP_PERCENT,
		unverifiable: isCurrentMonth && verdict === "UNVERIFIABLE",
	};
}

/**
 * Instance-admin table of every workspace's AI spend for one month (metadata only, no tenant
 * content), against both caps: the instance cap the host funds and the provider cap the workspace's
 * own admins set over spend on their own provider. Pure/presentational — instance-cap edits are
 * raised to the container via `onEditBudget`; the provider cap is read-only here by design.
 */
export function AdminInstanceLlmUsageTable({
	rows,
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
	// in a `colSpan` row inside it. Nested in the table it inherited the table's ~1100 px width and
	// its own two tables each opened a second horizontal scroller inside the first — two-dimensional
	// scrolling to read a breakdown, which is exactly what WCAG 2.2 SC 1.4.10 rules out. Out here it
	// reflows to the page width at any viewport and the `aria-controls` relationship is unchanged.
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
							<HelpHeader help="Spend you pay for. Yours to set.">Instance cap</HelpHeader>
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
							Events
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
									<CapCell usage={shared} label="Instance cap" workspace={row.displayName} />
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
											{/* Only the instance cap gets an edit control — the provider cap is the
											    workspace's own money and its own admins' call. */}
											<Button variant="outline" size="sm" onClick={() => onEditBudget(row)}>
												Set instance cap
											</Button>
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
				/>
			)}

			{/* Last, after every figure it qualifies — a footnote that precedes its numbers is read as a
			    preamble to something else. */}
			{hasConversion && <FxDisclosure fx={fx} isCurrentMonth={isCurrentMonth} />}
		</div>
	);
}

interface WorkspaceUsageDetailsProps {
	workspace: AdminWorkspaceLlmUsageRow;
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
}

/**
 * The expanded workspace's breakdowns, rendered under the table rather than inside it.
 *
 * It names the workspace in its own heading because it is no longer visually attached to the row
 * that opened it — the `aria-expanded`/`aria-controls` pair on that row's toggle is what still ties
 * the two together for assistive tech.
 *
 * The two breakdown tables stack until `xl`: side by side they would each be too narrow to avoid a
 * horizontal scroller of their own, and two scrollers on one screen is the thing that made this
 * unusable on a phone.
 */
function WorkspaceUsageDetails({
	workspace,
	report,
	isLoading,
	error,
	onRetry,
	fx,
}: WorkspaceUsageDetailsProps) {
	const panelId = detailPanelId(workspace.workspaceSlug);
	return (
		<section
			id={panelId}
			aria-labelledby={`${panelId}-heading`}
			className="space-y-4 rounded-md border bg-muted/20 p-4"
		>
			<h3 id={`${panelId}-heading`} className="font-medium">
				Usage details · {workspace.displayName}
			</h3>
			{error != null ? (
				<QueryErrorAlert
					error={error}
					title={`Couldn't load usage details for ${workspace.displayName}`}
					onRetry={onRetry}
				/>
			) : (
				<div className="grid gap-4 xl:grid-cols-2">
					<section aria-labelledby={`${panelId}-job-type`} className="min-w-0 space-y-2">
						<h4 id={`${panelId}-job-type`} className="font-medium">
							By job type
						</h4>
						<LlmUsageByJobTypeTable rows={isLoading ? undefined : report?.byJobType} fx={fx} />
					</section>
					<section aria-labelledby={`${panelId}-day`} className="min-w-0 space-y-2">
						<h4 id={`${panelId}-day`} className="font-medium">
							By day
						</h4>
						<LlmUsageByDayTable rows={isLoading ? undefined : report?.byDay} fx={fx} />
					</section>
				</div>
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
 */
function HelpHeader({ children, help }: HelpHeaderProps) {
	return (
		<Tooltip>
			<TooltipTrigger className="inline-flex cursor-help items-center gap-1 font-medium">
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
	// The tone never carries the state alone — the line underneath says it in words (WCAG SC 1.4.1).
	const state = usage.paused ? "Paused" : usage.nearCap ? "Near cap" : null;
	const tone = usage.paused ? "bg-destructive" : usage.nearCap ? "bg-warning" : "bg-primary";

	return (
		<TableCell className="text-right">
			<div className="ml-auto flex w-24 flex-col items-end gap-1">
				<span className="tabular-nums">
					<MoneyCell>{formatCapUsd(usage.cap)}</MoneyCell>
				</span>
				<ProgressRoot.Root
					value={Math.min(percent, 100)}
					className="flex w-full"
					aria-label={`${label} used by ${workspace}`}
					getAriaValueText={() =>
						`${formatCostUsd(usage.spend)} of ${formatCapUsd(usage.cap)} used, ${rounded}%`
					}
				>
					<ProgressTrack className="h-1.5 rounded-full">
						<ProgressIndicator className={tone} />
					</ProgressTrack>
				</ProgressRoot.Root>
				<span className="text-xs text-muted-foreground tabular-nums">
					<MoneyCell>{formatCostUsd(usage.spend)}</MoneyCell> · {rounded}%
					{state != null && ` · ${state}`}
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
	// cannot answer — so each cap contributes its own badge and they stack.
	const badges: StatusBadge[] = [];
	if (shared.paused) {
		badges.push({ key: "paused-instance", label: "Paused · instance cap", variant: "destructive" });
	}
	if (provider.paused) {
		badges.push({ key: "paused-provider", label: "Paused · provider cap", variant: "destructive" });
	}
	if (shared.nearCap) {
		badges.push({ key: "near-instance", label: "Near cap · instance cap", variant: "warning" });
	}
	if (provider.nearCap) {
		badges.push({ key: "near-provider", label: "Near cap · provider cap", variant: "warning" });
	}
	const noPriceSet = shared.unverifiable || provider.unverifiable;

	if (badges.length === 0 && !noPriceSet) {
		return shared.cap != null || provider.cap != null ? (
			<Badge variant="outline">Within budget</Badge>
		) : (
			<span className="text-muted-foreground">—</span>
		);
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
				<span className="text-warning text-xs">Some calls have no price set</span>
			)}
		</div>
	);
}
