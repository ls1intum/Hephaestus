import { Link } from "@tanstack/react-router";
import { FileSearchIcon } from "lucide-react";
import type { ReactNode } from "react";

import type { ScanSummary } from "@/api/types.gen";
import { EmptyState } from "@/components/common/EmptyState";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { TablePagination } from "@/components/common/TablePagination";
import { TableRowsSkeleton } from "@/components/common/TableRowsSkeleton";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageLayout } from "@/components/layout/PageLayout";
import { buttonVariants } from "@/components/ui/button";
import { Table, TableHeader, TableRow } from "@/components/ui/table";

import type { RefreshFailure } from "@/runtime/tanstack-query/infinite-list";

import { ScanColumnHeads, SCAN_SKELETON_COLUMNS, ScanRows } from "./ScanRows";

export type ScansListState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| {
			status: "ready";
			scans: readonly ScanSummary[];
			refreshFailure?: RefreshFailure;
			page: number;
			totalPages: number;
			onPageChange: (page: number) => void;
	  };

export interface ScansPageProps {
	workspaceSlug: string;
	scans: ScansListState;
}

const LIST_LABEL = "Scans, newest first";

// A failed read supplies no counts; show the error rather than zero-valued scan rows.
export function ScansPage({ workspaceSlug, scans }: ScansPageProps) {
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
				description="Scans appear here after a repository schedule starts one. Set up a repository schedule in review settings."
				action={
					<Link
						to="/w/$workspaceSlug/admin/practices/review"
						params={{ workspaceSlug }}
						className={buttonVariants({ variant: "outline" })}
					>
						Open review settings
					</Link>
				}
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
			/>
			{scans.status === "ready" && scans.refreshFailure !== undefined && (
				<QueryErrorAlert title="Scans may be out of date" {...scans.refreshFailure} />
			)}
			{body}
		</PageLayout>
	);
}
