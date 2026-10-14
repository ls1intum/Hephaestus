import { FileSearchIcon } from "lucide-react";
import type { ScanReportItem } from "@/api/types.gen";

import { EmptyState } from "@/components/common/EmptyState";
import { type FilterOption, FilterToggle } from "@/components/common/FilterToggle";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import { TableRowsSkeleton } from "@/components/common/TableRowsSkeleton";
import { OUTCOME_DEFS, problemCount } from "@/components/practice-vocabulary/outcome-defs";
import {
	REMOVED_FILE_DEF,
	SCAN_ENTRY_DEFS,
	type ScanEntryStatus,
} from "@/components/practice-vocabulary/scan-entry-defs";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { formatDate } from "@/lib/dates";
import { itemName } from "@/lib/item-kinds";
import { hasText } from "@/lib/text";

import { notReviewedSentence, withheldSentence } from "./scan-display";
import { ScanListMore, type ScanListState } from "./scan-list-state";
import { SCAN_FILE_FILTERS } from "./scans-search";

type FileFilter = ScanEntryStatus | "ALL";

const FILTER_OPTIONS: readonly FilterOption<FileFilter>[] = [
	{ value: "ALL", label: "All" },
	...SCAN_FILE_FILTERS.map((status) => ({ value: status, label: SCAN_ENTRY_DEFS[status].label })),
];

export interface ScanItemTableProps {
	state: ScanListState<ScanReportItem>;
	filter: ScanEntryStatus | undefined;
	onFilterChange: (filter: ScanEntryStatus | undefined) => void;
}

const SKELETON_COLUMNS = ["w-56", "w-40", "w-20", "w-64"] as const;

// Earlier evidence on a not-reviewed row is not this scan's verdict.
export function ScanItemTable({ state, filter, onFilterChange }: ScanItemTableProps) {
	return (
		<section aria-label="Files" className="space-y-4">
			<FilterToggle
				label="File status"
				options={FILTER_OPTIONS}
				value={filter ?? "ALL"}
				onChange={(next) => onFilterChange(next === "ALL" ? undefined : next)}
			/>
			<ScanItemResults state={state} filtered={filter !== undefined} />
		</section>
	);
}

function ScanItemResults({
	state,
	filtered,
}: {
	state: ScanListState<ScanReportItem>;
	filtered: boolean;
}) {
	if (state.status === "error") {
		return (
			<QueryErrorAlert
				error={state.error}
				title="We could not load the files"
				onRetry={state.onRetry}
			/>
		);
	}
	if (state.status === "ready" && state.entries.length === 0) {
		return (
			<EmptyState
				icon={<FileSearchIcon />}
				headingLevel={2}
				title={filtered ? "No files match this filter." : "No files listed yet."}
			/>
		);
	}
	return (
		<div className="space-y-4">
			<Table bordered aria-label="Files and practices" aria-busy={state.status === "loading"}>
				<TableHeader>
					<TableRow>
						<TableHead scope="col">File</TableHead>
						<TableHead scope="col">Practice</TableHead>
						<TableHead scope="col">Status</TableHead>
						<TableHead scope="col">Result</TableHead>
					</TableRow>
				</TableHeader>
				{state.status === "loading" ? (
					<TableRowsSkeleton columns={SKELETON_COLUMNS} rows={5} />
				) : (
					<TableBody>
						{state.entries.map((entry) => (
							<TableRow key={`${entry.item.id}:${entry.practiceSlug}`}>
								<TableCell className="max-w-xs">
									<span className="block truncate font-mono text-xs">
										{entry.item.path ?? itemName(entry.item)}
									</span>
									{entry.item.removedAt !== null && (
										<StatusBadge def={REMOVED_FILE_DEF} className="mt-1" />
									)}
								</TableCell>
								<TableCell className="max-w-48 text-sm">
									<span className="block truncate">{entry.practiceName}</span>
								</TableCell>
								<TableCell>
									<StatusBadge def={SCAN_ENTRY_DEFS[entry.status]} />
								</TableCell>
								<TableCell className="text-sm">
									<EntryResult entry={entry} />
								</TableCell>
							</TableRow>
						))}
					</TableBody>
				)}
			</Table>
			{state.status === "ready" && (
				<ScanListMore
					hasNext={state.hasNext}
					loadingMore={state.loadingMore}
					onLoadMore={state.onLoadMore}
					noun="files"
				/>
			)}
		</div>
	);
}

function EntryResult({ entry }: { entry: ScanReportItem }) {
	switch (entry.status) {
		case "JUDGED":
		case "REUSED": {
			return (
				<div className="space-y-1">
					<div className="flex flex-wrap items-center gap-2">
						<StatusBadge def={OUTCOME_DEFS[entry.outcome]} />
						{entry.problemCount > 0 && (
							<span className="text-muted-foreground">{problemCount(entry.problemCount)}</span>
						)}
					</div>
					{hasText(entry.summary) && <p className="line-clamp-2">{entry.summary}</p>}
					{entry.status === "REUSED" && (
						<p className="text-xs text-muted-foreground">
							Reused from a review on {formatDate(entry.recordedAt)}.
						</p>
					)}
				</div>
			);
		}
		case "WITHHELD": {
			return <p className="text-muted-foreground">{withheldSentence(entry.reason)}</p>;
		}
		case "NOT_REVIEWED": {
			return (
				<div className="space-y-1">
					<p className="text-muted-foreground">{notReviewedSentence(entry.reason)}</p>
					<EarlierEvidence result={entry.lastResult} />
				</div>
			);
		}
		case "LISTED": {
			return (
				<div className="space-y-1">
					<p className="text-muted-foreground">Not reviewed yet.</p>
					<EarlierEvidence result={entry.lastResult} />
				</div>
			);
		}
	}
}

function EarlierEvidence({
	result,
}: {
	result: Extract<ScanReportItem, { status: "LISTED" }>["lastResult"];
}) {
	if (result === null) {
		return null;
	}
	return (
		<p className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
			<span>Earlier evidence, not from this scan: {formatDate(result.recordedAt)}</span>
			<StatusBadge def={OUTCOME_DEFS[result.outcome]} />
		</p>
	);
}
