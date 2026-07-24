import { ChevronDown, ChevronRight, CircleDollarSign } from "lucide-react";
import { Fragment } from "react";
import type { AdminWorkspaceLlmUsage, WorkspaceLlmUsageReport } from "@/api/types.gen";
import { formatCostUsd } from "@/components/admin/ai/jobUtils";
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
import { LlmUsageByDayTable, LlmUsageByJobTypeTable } from "./LlmUsageBreakdownTables";

export interface AdminInstanceLlmUsageTableProps {
	/** Per-workspace month rollups, already sorted by the container (cost desc). */
	rows: AdminWorkspaceLlmUsage[];
	/**
	 * Whether the shown month is the current calendar month (UTC). `verdict` compares the
	 * workspace's *current* cap against the selected month's spend, so it only describes a real
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
	onToggleDetails: (workspace: AdminWorkspaceLlmUsage) => void;
	onEditBudget: (workspace: AdminWorkspaceLlmUsage) => void;
}

/** One entry per header column — the trailing action slot promises nothing. */
const SKELETON_COLUMNS = ["w-32", "w-16", "w-16", "w-16", "w-14", "w-12", null];

/**
 * Instance-admin table of every workspace's LLM spend for one month (metadata only, no tenant
 * content). Pure/presentational — budget edits are raised to the container via `onEditBudget`.
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
					<TableHead scope="col" className="text-right">
						Workspace-owned
					</TableHead>
					<TableHead scope="col" className="text-right">
						Budget cap
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
									<TableCell className="text-right tabular-nums">
										{formatCostUsd(row.byoTotalCostUsd)}
									</TableCell>
									<TableCell className="text-right tabular-nums">
										{row.monthlyBudgetUsd != null ? (
											formatCostUsd(row.monthlyBudgetUsd)
										) : (
											<span className="text-muted-foreground">No cap</span>
										)}
									</TableCell>
									<TableCell>
										{!isCurrentMonth ? (
											<span className="text-muted-foreground">—</span>
										) : row.verdict === "EXHAUSTED" ? (
											<Badge variant="destructive">Budget reached</Badge>
										) : row.verdict === "UNVERIFIABLE" ? (
											// Not a badge — the #1368 glossary treats "unverifiable" as a warning line, never a
											// status word (there's no "Unverified" state name to badge).
											<span className="text-warning text-xs">Some usage has no price set</span>
										) : row.monthlyBudgetUsd != null ? (
											<Badge variant="outline">OK</Badge>
										) : (
											<span className="text-muted-foreground">—</span>
										)}
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
											<Button variant="outline" size="sm" onClick={() => onEditBudget(row)}>
												Set budget
											</Button>
										</div>
									</TableCell>
								</TableRow>
								{isExpanded && (
									<TableRow id={detailId} className="hover:bg-transparent">
										<TableCell colSpan={7} className="whitespace-normal bg-muted/20 p-4">
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
