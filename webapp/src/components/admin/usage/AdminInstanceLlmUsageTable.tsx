import { Progress as ProgressRoot } from "@base-ui/react/progress";
import { ChevronDown, ChevronRight, CircleDollarSign, Info } from "lucide-react";
import { Fragment } from "react";
import type { AdminWorkspaceLlmUsage, WorkspaceLlmUsageReport } from "@/api/types.gen";
import { formatCostUsd } from "@/components/admin/ai/jobUtils";
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
import { LlmUsageByDayTable, LlmUsageByJobTypeTable } from "./LlmUsageBreakdownTables";

/**
 * The admin rollup row, widened with the workspace's own-provider (BYO) cap fields.
 *
 * The two caps are different people's money and are never summed: `instanceMonthlyBudgetUsd` is the
 * *instance* cap an instance admin sets over host-funded shared-model spend, while the BYO fields
 * below describe the cap the *workspace's own* admins set over spend on their own connected
 * provider. They pause independently.
 *
 * The admin rollup reports both caps with the same names and meanings the per-workspace report
 * uses, so a row renders identically on either surface.
 */
export type AdminWorkspaceLlmUsageRow = AdminWorkspaceLlmUsage;

export interface AdminInstanceLlmUsageTableProps {
	/** Per-workspace month rollups, already sorted by the container (cost desc). */
	rows: AdminWorkspaceLlmUsageRow[];
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
	expandedWorkspaceId: number | null;
	/** Detailed rollup for the expanded workspace. */
	detailReport?: WorkspaceLlmUsageReport;
	isDetailLoading: boolean;
	detailError: unknown;
	/** Retry the expanded workspace report. */
	onRetryDetail?: () => void;
	onToggleDetails: (workspace: AdminWorkspaceLlmUsageRow) => void;
	/** Edit the *instance* cap. There is deliberately no counterpart for the self cap. */
	onEditBudget: (workspace: AdminWorkspaceLlmUsageRow) => void;
}

/** One entry per header column — the trailing action slot promises nothing. */
const SKELETON_COLUMNS = ["w-32", "w-16", "w-24", "w-16", "w-24", "w-28", "w-12", null];

const COLUMN_COUNT = SKELETON_COLUMNS.length;

/**
 * Where a cap stops being a background fact and becomes something an admin may want to act on
 * *before* work pauses. Below it the meter is enough; at or above it the row earns a badge.
 */
const NEAR_CAP_PERCENT = 80;

/** One money stream (host-funded or workspace-owned) measured against its own cap. */
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
 * Instance-admin table of every workspace's LLM spend for one month (metadata only, no tenant
 * content), against both caps: the instance cap the host funds and the self cap the workspace's
 * own admins set over their own provider. Pure/presentational — instance-cap edits are raised to
 * the container via `onEditBudget`; the self cap is read-only here by design.
 */
export function AdminInstanceLlmUsageTable({
	rows,
	isCurrentMonth,
	isLoading,
	error,
	onRetry,
	expandedWorkspaceId,
	detailReport,
	isDetailLoading,
	detailError,
	onRetryDetail,
	onToggleDetails,
	onEditBudget,
}: AdminInstanceLlmUsageTableProps) {
	if (error != null) {
		return <QueryErrorAlert error={error} title="Couldn't load LLM usage" onRetry={onRetry} />;
	}
	if (rows.length === 0 && !isLoading) {
		return (
			<Empty className="border border-dashed">
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

	return (
		<Table containerClassName="rounded-md border">
			<TableCaption className="sr-only">
				Per-workspace AI spend for the selected month, most expensive first
			</TableCaption>
			<TableHeader>
				<TableRow>
					<TableHead scope="col">Workspace</TableHead>
					<TableHead scope="col" className="text-right">
						Instance-funded
					</TableHead>
					{/* Spend sits next to the cap it is measured against, so a row reads left to right as
					    "this much, out of this much" without the admin holding a number in their head. */}
					<TableHead scope="col" className="text-right">
						<HelpHeader help="Monthly cap on spend the host pays for, on shared instance models. You set it.">
							Instance cap
						</HelpHeader>
					</TableHead>
					<TableHead scope="col" className="text-right">
						Workspace-owned
					</TableHead>
					<TableHead scope="col" className="text-right">
						<HelpHeader help="The workspace's own cap on spend through its own connected provider — their money, so only their admins can change it. Read-only here.">
							Self cap
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
						const isExpanded = expandedWorkspaceId === row.workspaceId;
						const detailId = `workspace-usage-details-${row.workspaceId}`;
						const instance = capUsage({
							cap: row.instanceMonthlyBudgetUsd,
							spend: row.pricedTotalCostUsd,
							verdict: row.instanceBudgetVerdict,
							paused: row.instanceFundedPaused,
							isCurrentMonth,
						});
						const self = capUsage({
							cap: row.byoMonthlyBudgetUsd,
							spend: row.byoTotalCostUsd,
							verdict: row.byoBudgetVerdict,
							paused: row.byoPaused,
							isCurrentMonth,
						});
						return (
							<Fragment key={row.workspaceId}>
								<TableRow>
									<TableCell>
										<div className="font-medium">{row.displayName}</div>
										<div className="font-mono text-xs text-muted-foreground">
											{row.workspaceSlug}
										</div>
									</TableCell>
									<TableCell className="text-right tabular-nums">
										{formatCostUsd(row.pricedTotalCostUsd)}
									</TableCell>
									<CapCell usage={instance} label="Instance cap" workspace={row.displayName} />
									<TableCell className="text-right tabular-nums">
										{formatCostUsd(row.byoTotalCostUsd)}
									</TableCell>
									<CapCell usage={self} label="Self cap" workspace={row.displayName} />
									<TableCell>
										<StatusCell instance={instance} self={self} isCurrentMonth={isCurrentMonth} />
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
												aria-controls={detailId}
												aria-label={`${isExpanded ? "Hide" : "View"} usage details for ${row.displayName}`}
												onClick={() => onToggleDetails(row)}
											>
												{isExpanded ? <ChevronDown aria-hidden /> : <ChevronRight aria-hidden />}
												Details
											</Button>
											{/* Only the instance cap gets an edit control — the self cap is the
											    workspace's own money and its own admins' call. */}
											<Button variant="outline" size="sm" onClick={() => onEditBudget(row)}>
												Set instance cap
											</Button>
										</div>
									</TableCell>
								</TableRow>
								{isExpanded && (
									<TableRow id={detailId} className="hover:bg-transparent">
										<TableCell colSpan={COLUMN_COUNT} className="whitespace-normal bg-muted/20 p-4">
											{detailError != null ? (
												<QueryErrorAlert
													error={detailError}
													title={`Couldn't load usage details for ${row.displayName}`}
													onRetry={onRetryDetail}
												/>
											) : (
												<div className="grid gap-4 xl:grid-cols-2">
													<section aria-labelledby={`${detailId}-job-type`} className="space-y-2">
														<h3 id={`${detailId}-job-type`} className="font-medium">
															By job type
														</h3>
														<LlmUsageByJobTypeTable
															rows={isDetailLoading ? undefined : detailReport?.byJobType}
														/>
													</section>
													<section aria-labelledby={`${detailId}-day`} className="space-y-2">
														<h3 id={`${detailId}-day`} className="font-medium">
															By day
														</h3>
														<LlmUsageByDayTable
															rows={isDetailLoading ? undefined : detailReport?.byDay}
														/>
													</section>
												</div>
											)}
										</TableCell>
									</TableRow>
								)}
							</Fragment>
						);
					})}
				</TableBody>
			)}
		</Table>
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
	const tone = usage.paused ? "bg-destructive" : usage.nearCap ? "bg-warning" : "bg-primary";

	return (
		<TableCell className="text-right">
			<div className="ml-auto flex w-24 flex-col items-end gap-1">
				<span className="tabular-nums">{formatCostUsd(usage.cap)}</span>
				<ProgressRoot.Root
					value={Math.min(percent, 100)}
					className="flex w-full"
					aria-label={`${label} used by ${workspace}`}
					getAriaValueText={() =>
						`${formatCostUsd(usage.spend)} of ${formatCostUsd(usage.cap)} used, ${rounded}%`
					}
				>
					<ProgressTrack className="h-1.5 rounded-full">
						<ProgressIndicator className={tone} />
					</ProgressTrack>
				</ProgressRoot.Root>
				<span className="text-xs text-muted-foreground tabular-nums">
					{formatCostUsd(usage.spend)} · {rounded}%
				</span>
			</div>
		</TableCell>
	);
}

interface StatusCellProps {
	instance: CapUsage;
	self: CapUsage;
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
function StatusCell({ instance, self, isCurrentMonth }: StatusCellProps) {
	if (!isCurrentMonth) {
		return <span className="text-muted-foreground">—</span>;
	}

	// Both caps can be spent at once, and an admin who only sees "Paused" fields a ticket they
	// cannot answer — so each cap contributes its own badge and they stack.
	const badges: StatusBadge[] = [];
	if (instance.paused) {
		badges.push({ key: "paused-instance", label: "Paused — instance cap", variant: "destructive" });
	}
	if (self.paused) {
		badges.push({ key: "paused-self", label: "Paused — self cap", variant: "destructive" });
	}
	if (instance.nearCap) {
		badges.push({ key: "near-instance", label: "Near cap — instance", variant: "warning" });
	}
	if (self.nearCap) {
		badges.push({ key: "near-self", label: "Near cap — self", variant: "warning" });
	}
	const unverifiable = instance.unverifiable || self.unverifiable;

	if (badges.length === 0 && !unverifiable) {
		return instance.cap != null || self.cap != null ? (
			<Badge variant="outline">OK</Badge>
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
			{unverifiable && (
				// Not a badge — the #1368 glossary treats "unverifiable" as a warning line, never a
				// status word (there's no "Unverified" state name to badge).
				<span className="text-warning text-xs">Some usage has no price set</span>
			)}
		</div>
	);
}
