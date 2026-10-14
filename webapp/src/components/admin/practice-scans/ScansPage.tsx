import { FileSearchIcon, PlusIcon } from "lucide-react";
import type { ReactNode } from "react";

import type { ScanSummary } from "@/api/types.gen";
import { TableRowsSkeleton } from "@/components/admin/integrations/TableRowsSkeleton";
import { EmptyState } from "@/components/common/EmptyState";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { TablePagination } from "@/components/common/TablePagination";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageLayout } from "@/components/layout/PageLayout";
import { buttonVariants } from "@/components/ui/button";
import { Table, TableHeader, TableRow } from "@/components/ui/table";

import { ScanColumnHeads, SCAN_SKELETON_COLUMNS, ScanRows } from "./ScanRows";
import { startScanLevel } from "./scans-search";

export type ScansListState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| {
			status: "ready";
			scans: readonly ScanSummary[];
			page: number;
			totalPages: number;
			onPageChange: (page: number) => void;
	  };

export interface ScansPageProps {
	workspaceSlug: string;
	scans: ScansListState;
	/** The start form’s level, drawn over the list by the route. */
	children?: ReactNode;
}

const LIST_LABEL = "Scans, newest first";

function StartScanLink({ className }: { className?: string }) {
	return (
		<DetailStackLink entry={startScanLevel()} className={className ?? buttonVariants()}>
			<PlusIcon aria-hidden className="size-4" />
			Start scan
		</DetailStackLink>
	);
}

/**
 * The workspace’s scans. A failed load keeps the error and its Retry in place of the table, never a
 * table of zeros: a zero would say a scan reviewed nothing, which nobody knows yet.
 */
export function ScansPage({ workspaceSlug, scans, children }: ScansPageProps) {
	let body: ReactNode;
	if (scans.status === "error") {
		body = (
			<QueryErrorAlert
				error={scans.error}
				title="We could not load scans"
				onRetry={scans.onRetry}
			/>
		);
	} else if (scans.status === "ready" && scans.scans.length === 0) {
		body = (
			<EmptyState
				icon={<FileSearchIcon />}
				title="No scans yet"
				headingLevel={2}
				description="Start a scan to review the files in a repository."
				action={<StartScanLink className={buttonVariants({ variant: "outline" })} />}
			/>
		);
	} else {
		body = (
			<div className="space-y-4">
				<Table bordered aria-label={LIST_LABEL} aria-busy={scans.status === "loading"}>
					<TableHeader>
						<TableRow>
							<ScanColumnHeads />
						</TableRow>
					</TableHeader>
					{scans.status === "loading" ? (
						<TableRowsSkeleton columns={SCAN_SKELETON_COLUMNS} rows={5} />
					) : (
						<ScanRows workspaceSlug={workspaceSlug} scans={scans.scans} />
					)}
				</Table>
				{scans.status === "ready" && (
					<TablePagination
						page={scans.page}
						totalPages={scans.totalPages}
						onPageChange={scans.onPageChange}
					/>
				)}
			</div>
		);
	}
	return (
		<PageLayout>
			<PageHeader
				icon={<FileSearchIcon />}
				title="Scans"
				description="A scan reviews every file of a repository’s default branch against the practices you include in scans. It sends no feedback to anyone."
				actions={<StartScanLink />}
			/>
			{body}
			{children}
		</PageLayout>
	);
}
