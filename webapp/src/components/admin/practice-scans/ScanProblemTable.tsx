import { CircleCheckIcon, SearchXIcon } from "lucide-react";

import type { ProblemChanges, ScanReportProblem } from "@/api/types.gen";
import { TableRowsSkeleton } from "@/components/admin/integrations/TableRowsSkeleton";
import { EmptyState } from "@/components/common/EmptyState";
import { type FilterOption, FilterToggle } from "@/components/common/FilterToggle";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import {
	baselineDef,
	PROBLEM_BASELINE_STATES,
	type ProblemBaselineState,
	SCAN_BASELINE_DEFS,
} from "@/components/practice-vocabulary/scan-baseline-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { itemName } from "@/lib/item-kinds";

import { ScanListMore, type ScanListState } from "./scan-list-state";

type BaselineFilter = ProblemBaselineState | "ALL";

export interface ScanProblemTableProps {
	state: ScanListState<ScanReportProblem>;
	filter: ProblemBaselineState | undefined;
	onFilterChange: (filter: ProblemBaselineState | undefined) => void;
	/** How many problems compare each way with the scan before; the filters say them. */
	changes: ProblemChanges;
	/** Pairs that have no result in this scan: withheld or not reviewed. */
	pairsWithoutResult: number;
}

const SKELETON_COLUMNS = ["w-64", "w-48", "w-32", "w-20"] as const;

function changeCount(changes: ProblemChanges, state: ProblemBaselineState): number {
	switch (state) {
		case "NEW": {
			return changes.new ?? 0;
		}
		case "UNCHANGED": {
			return changes.unchanged;
		}
		case "UPDATED": {
			return changes.updated;
		}
		case "ABSENT": {
			return changes.absent;
		}
	}
}

function citationText(entry: ScanReportProblem): string {
	const citation = entry.problem.citations[0];
	const path = citation?.path ?? entry.item.path ?? itemName(entry.item);
	if (citation?.startLine == null) {
		return path;
	}
	return citation.endLine == null || citation.endLine === citation.startLine
		? `${path}:${citation.startLine}`
		: `${path}:${citation.startLine}–${citation.endLine}`;
}

/**
 * The problems the scan recorded, and the earlier scan's problems it no longer recorded. An absent
 * problem is a fix only where the file was reviewed again under the same standard and method; every
 * other absence says that it proves nothing.
 */
export function ScanProblemTable({
	state,
	filter,
	onFilterChange,
	changes,
	pairsWithoutResult,
}: ScanProblemTableProps) {
	const options: readonly FilterOption<BaselineFilter>[] = [
		{ value: "ALL", label: "All" },
		...PROBLEM_BASELINE_STATES.map((value) => ({
			value,
			label: `${SCAN_BASELINE_DEFS[value].label} (${changeCount(changes, value)})`,
		})),
	];
	return (
		<section aria-label="Problems" className="space-y-4">
			<FilterToggle
				label="Compared with the earlier scan"
				options={options}
				value={filter ?? "ALL"}
				onChange={(next) => onFilterChange(next === "ALL" ? undefined : next)}
			/>
			<ScanProblemResults
				state={state}
				filtered={filter !== undefined}
				pairsWithoutResult={pairsWithoutResult}
			/>
		</section>
	);
}

function ScanProblemResults({
	state,
	filtered,
	pairsWithoutResult,
}: {
	state: ScanListState<ScanReportProblem>;
	filtered: boolean;
	pairsWithoutResult: number;
}) {
	if (state.status === "error") {
		return (
			<QueryErrorAlert
				error={state.error}
				title="We could not load the problems"
				onRetry={state.onRetry}
			/>
		);
	}
	if (state.status === "ready" && state.entries.length === 0) {
		if (filtered) {
			return (
				<EmptyState
					icon={<SearchXIcon />}
					headingLevel={3}
					title="No problems match these filters."
				/>
			);
		}
		return (
			<EmptyState
				icon={<CircleCheckIcon />}
				headingLevel={3}
				title="This scan recorded no problems."
				description={
					pairsWithoutResult > 0
						? `That does not mean every practice is met: ${pairsWithoutResult} practice and file ${pairsWithoutResult === 1 ? "pair has" : "pairs have"} no result in this scan.`
						: undefined
				}
			/>
		);
	}
	return (
		<div className="space-y-4">
			<Table bordered aria-label="Problems in this scan" aria-busy={state.status === "loading"}>
				<TableHeader>
					<TableRow>
						<TableHead scope="col">Problem</TableHead>
						<TableHead scope="col">File</TableHead>
						<TableHead scope="col">Practice</TableHead>
						<TableHead scope="col">Compared</TableHead>
					</TableRow>
				</TableHeader>
				{state.status === "loading" ? (
					<TableRowsSkeleton columns={SKELETON_COLUMNS} rows={5} />
				) : (
					<TableBody>
						{state.entries.map((entry) => {
							const comparison =
								entry.baselineState === null
									? undefined
									: baselineDef(entry.baselineState, entry.absentReason);
							return (
								<TableRow key={entry.problem.id}>
									<TableCell className="max-w-md">
										<div className="flex flex-wrap items-start gap-2">
											<StatusBadge def={SEVERITY_DEFS[entry.problem.severity]} />
											<span className="min-w-0 flex-1">{entry.problem.summary}</span>
										</div>
									</TableCell>
									<TableCell className="max-w-xs">
										<span className="block truncate font-mono text-xs">{citationText(entry)}</span>
									</TableCell>
									<TableCell className="max-w-48 text-sm">
										<span className="block truncate">{entry.practiceName}</span>
									</TableCell>
									<TableCell className="text-sm">
										{comparison === undefined ? (
											<span className="text-muted-foreground">Not compared</span>
										) : (
											<div className="space-y-1">
												<StatusBadge def={comparison} />
												{entry.baselineState === "ABSENT" && (
													<p className="text-xs text-muted-foreground">{comparison.description}</p>
												)}
											</div>
										)}
									</TableCell>
								</TableRow>
							);
						})}
					</TableBody>
				)}
			</Table>
			{state.status === "ready" && (
				<ScanListMore
					hasNext={state.hasNext}
					loadingMore={state.loadingMore}
					onLoadMore={state.onLoadMore}
					noun="problems"
				/>
			)}
		</div>
	);
}
