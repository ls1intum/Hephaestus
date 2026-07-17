import { CircleDollarSign } from "lucide-react";
import type { AdminWorkspaceLlmUsage } from "@/api/types.gen";
import { formatCostUsd } from "@/components/admin/ai/jobUtils";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";

export interface AdminInstanceLlmUsageTableProps {
	/** Per-workspace month rollups, already sorted by the container (cost desc). */
	rows: AdminWorkspaceLlmUsage[];
	/**
	 * Whether the shown month is the current calendar month (UTC). `overBudget` compares the
	 * workspace's *current* cap against the selected month's spend, so it only describes a real
	 * pause for the current month — past months show a neutral status instead.
	 */
	isCurrentMonth: boolean;
	isLoading: boolean;
	isError: boolean;
	onEditBudget: (workspace: AdminWorkspaceLlmUsage) => void;
}

/**
 * Instance-admin table of every workspace's LLM spend for one month (metadata only, no tenant
 * content). Pure/presentational — budget edits are raised to the container via `onEditBudget`.
 */
export function AdminInstanceLlmUsageTable({
	rows,
	isCurrentMonth,
	isLoading,
	isError,
	onEditBudget,
}: AdminInstanceLlmUsageTableProps) {
	if (isError) {
		return (
			<p className="py-8 text-center text-sm text-destructive">
				Failed to load LLM usage. Please try again.
			</p>
		);
	}
	if (isLoading) {
		return (
			<div className="flex items-center justify-center py-12">
				<Spinner />
			</div>
		);
	}
	if (rows.length === 0) {
		return (
			<div className="flex flex-col items-center gap-2 py-12 text-center text-muted-foreground">
				<CircleDollarSign className="size-8" aria-hidden />
				{/* The rollup left-joins from workspace, so zero rows means zero workspaces. */}
				<p className="text-sm">No workspaces on this instance yet.</p>
			</div>
		);
	}

	return (
		<div className="rounded-md border">
			<Table>
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
				<TableBody>
					{rows.map((row) => (
						<TableRow key={row.workspaceId}>
							<TableCell>
								<div className="font-medium">{row.displayName}</div>
								<div className="font-mono text-xs text-muted-foreground">{row.workspaceSlug}</div>
							</TableCell>
							<TableCell className="text-right tabular-nums">
								{formatCostUsd(row.costUsd)}
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
								) : row.overBudget ? (
									<Badge variant="destructive">Over budget</Badge>
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
			</Table>
		</div>
	);
}
