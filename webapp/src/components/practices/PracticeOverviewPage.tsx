import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import {
	getDeveloperPracticeReportOptions,
	listPracticeHealthOptions,
	listPracticeReportsOptions,
} from "@/api/@tanstack/react-query.gen";
import { AreaMatrix, AreaMatrixSkeleton } from "@/components/practices/AreaMatrix";
import { DrillDownSheet } from "@/components/practices/DrillDownSheet";
import type { PracticeReportSummary } from "@/components/practices/practice-types";
import {
	WorkspaceHealthCards,
	WorkspaceHealthCardsSkeleton,
} from "@/components/practices/WorkspaceHealthCards";
import { Button } from "@/components/ui/button";

export interface PracticeOverviewPageProps {
	workspaceSlug: string;
}

/**
 * The mentor view container: anonymous workspace health per area on top, the
 * developers-by-areas matrix below, and a drill-down sheet per developer. Fetches the roster,
 * the health rollup and (once a row is opened) that developer's report cards through the
 * generated query options. Triage only: the roster order brings people a mentor could help to
 * the top, and nothing on this page ranks or scores anyone.
 */
export function PracticeOverviewPage({ workspaceSlug }: PracticeOverviewPageProps) {
	const [selectedDeveloper, setSelectedDeveloper] = useState<PracticeReportSummary | null>(null);

	const rosterQuery = useQuery(
		listPracticeReportsOptions({ path: { workspaceSlug }, query: { page: 0, size: 100 } }),
	);
	const healthQuery = useQuery(listPracticeHealthOptions({ path: { workspaceSlug } }));
	const drillDownQuery = useQuery({
		...getDeveloperPracticeReportOptions({
			path: { workspaceSlug, userId: selectedDeveloper?.userId ?? 0 },
		}),
		enabled: selectedDeveloper !== null,
	});

	const isLoading = rosterQuery.isLoading || healthQuery.isLoading;
	const isError = rosterQuery.isError || healthQuery.isError;

	return (
		<div className="container mx-auto max-w-5xl py-6">
			<div className="mb-6 flex flex-col gap-1">
				<h1 className="text-3xl font-bold">Practice overview</h1>
				<p className="text-sm text-muted-foreground">
					Where the team stands per practice area, and who could use support. Opening a developer's
					report is recorded.
				</p>
			</div>
			{isLoading && (
				<div className="flex flex-col gap-6">
					<WorkspaceHealthCardsSkeleton />
					<AreaMatrixSkeleton />
				</div>
			)}
			{!isLoading && isError && (
				<div className="flex max-w-xl flex-col items-start gap-2 rounded-lg border border-dashed px-4 py-6">
					<p className="text-sm text-muted-foreground">
						The practice overview could not be loaded right now.
					</p>
					<Button
						variant="outline"
						size="sm"
						onClick={() => {
							rosterQuery.refetch();
							healthQuery.refetch();
						}}
					>
						Try again
					</Button>
				</div>
			)}
			{!isLoading && !isError && (
				<div className="flex flex-col gap-6">
					<WorkspaceHealthCards health={healthQuery.data ?? []} />
					<AreaMatrix
						roster={rosterQuery.data ?? []}
						health={healthQuery.data ?? []}
						onOpenDeveloper={setSelectedDeveloper}
					/>
				</div>
			)}
			<DrillDownSheet
				developer={selectedDeveloper}
				cards={drillDownQuery.data}
				isLoading={drillDownQuery.isLoading}
				isError={drillDownQuery.isError}
				onRetry={() => drillDownQuery.refetch()}
				open={selectedDeveloper !== null}
				onOpenChange={(open) => {
					if (!open) setSelectedDeveloper(null);
				}}
			/>
		</div>
	);
}
