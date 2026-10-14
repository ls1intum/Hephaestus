import { Link } from "@tanstack/react-router";

import type { ScanSummary } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { StatusBadge } from "@/components/common/StatusBadge";
import type { TableSkeletonColumn } from "@/components/common/TableRowsSkeleton";
import { REVIEW_STATUS_DEFS } from "@/components/practice-vocabulary/review-status-defs";
import { TableBody, TableCell, TableHead, TableRow } from "@/components/ui/table";

import { ofTotal, repositoryName, scanStartedBy, shortVersion } from "./scan-display";

export const SCAN_COLUMNS = [
	"Repository",
	"Version",
	"Started by",
	"Status",
	"Practices",
	"Reviewed",
	"Problems",
	"Started",
] as const;

export const SCAN_SKELETON_COLUMNS: readonly TableSkeletonColumn[] = [
	{ width: "w-40" },
	{ width: "w-24" },
	{ width: "w-28" },
	{ width: "w-20" },
	{ width: "w-8", numeric: true },
	{ width: "w-12", numeric: true },
	{ width: "w-8", numeric: true },
	{ width: "w-20" },
];

const NUMERIC_COLUMNS = new Set<string>(["Practices", "Reviewed", "Problems"]);

export function ScanColumnHeads() {
	return SCAN_COLUMNS.map((column) => (
		<TableHead
			key={column}
			scope="col"
			className={NUMERIC_COLUMNS.has(column) ? "text-right" : undefined}
		>
			{column}
		</TableHead>
	));
}

export interface ScanRowsProps {
	workspaceSlug: string;
	scans: readonly ScanSummary[];
}

// Reviewed counts practice-file pairs; listing must finish before a total can be shown.
export function ScanRows({ workspaceSlug, scans }: ScanRowsProps) {
	return (
		<TableBody>
			{scans.map((scan) => (
				<TableRow key={scan.id}>
					<TableCell className="max-w-xs">
						<Link
							to="/w/$workspaceSlug/admin/practices/scans/$scanId"
							params={{ workspaceSlug, scanId: scan.id }}
							className="block truncate font-medium hover:underline"
						>
							{repositoryName(scan.repository)}
						</Link>
					</TableCell>
					<TableCell className="text-sm whitespace-nowrap">
						{scan.ref}
						<span className="font-mono text-xs text-muted-foreground">
							{" · "}
							{shortVersion(scan.itemVersion)}
						</span>
					</TableCell>
					<TableCell className="text-sm">{scanStartedBy(scan)}</TableCell>
					<TableCell>
						<StatusBadge def={REVIEW_STATUS_DEFS[scan.status]} />
					</TableCell>
					<TableCell numeric>{scan.practiceCount}</TableCell>
					<TableCell numeric>
						{ofTotal(scan.counts.reviewed, scan.counts.practiceItemPairs)}
					</TableCell>
					<TableCell numeric>{scan.counts.problems}</TableCell>
					<TableCell className="text-sm text-muted-foreground">
						<RelativeTime value={scan.startedAt ?? scan.createdAt} />
					</TableCell>
				</TableRow>
			))}
		</TableBody>
	);
}
