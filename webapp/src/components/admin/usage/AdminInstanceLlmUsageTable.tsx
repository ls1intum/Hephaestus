import { CircleDollarSign } from "lucide-react";
import type { AdminWorkspaceLlmUsage } from "@/api/types.gen";
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
	onEditBudget: (workspace: AdminWorkspaceLlmUsage) => void;
}

/** One entry per header column — the trailing action slot promises nothing. */
const SKELETON_COLUMNS = ["w-32", "w-16", "w-16", "w-14", "w-12", null];

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
						Spend
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
					{rows.map((row) => (
						<TableRow key={row.workspaceId}>
							<TableCell>
								<div className="font-medium">{row.displayName}</div>
								<div className="font-mono text-xs text-muted-foreground">{row.workspaceSlug}</div>
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{row.verdict === "UNVERIFIABLE"
									? `at least ${formatCostUsd(row.pricedTotalCostUsd)}`
									: formatCostUsd(row.pricedTotalCostUsd)}
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
									<Badge variant="destructive">Over budget</Badge>
								) : row.verdict === "UNVERIFIABLE" ? (
									<Badge variant="outline">Unverified</Badge>
								) : row.monthlyBudgetUsd != null ? (
									<Badge variant="outline">OK</Badge>
								) : (
									<span className="text-muted-foreground">—</span>
								)}
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{row.events.toLocaleString()}
							</TableCell>
							<TableCell className="text-right">
								<Button variant="outline" size="sm" onClick={() => onEditBudget(row)}>
									Set budget
								</Button>
							</TableCell>
						</TableRow>
					))}
				</TableBody>
			)}
		</Table>
	);
}
