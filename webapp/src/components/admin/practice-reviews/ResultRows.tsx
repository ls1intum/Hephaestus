import { ScanSearchIcon } from "lucide-react";

import type { ReviewResult } from "@/api/types.gen";
import { StatusBadge } from "@/components/common/StatusBadge";
import { useNow } from "@/components/common/use-now";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { ReviewRunWorkCell } from "@/components/practice-trace/ReviewRunDayTable";
import { ClaimCurrentnessBadge } from "@/components/practice-vocabulary/ClaimCurrentness";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import { PracticeTableRow } from "@/components/practice-vocabulary/PracticeTable";
import {
	RecordDayTable,
	RecordGroupedTable,
	type RecordTableColumn,
	RecordTimeCell,
} from "@/components/practice-vocabulary/RecordTable";
import { DEVELOPER_RESPONSE_DEFS } from "@/components/practice-vocabulary/result-dispute-defs";
import { MARKED_INCORRECT_DEF } from "@/components/practice-vocabulary/result-invalidation-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { StatusLabel } from "@/components/practice-vocabulary/StatusTooltip";
import { TableCell } from "@/components/ui/table";
import { formatDayTime } from "@/lib/dates";

import { RecordResults, type RecordResultsWords } from "./RecordResults";
import { RecordRowActions, ROW_OPEN_COLUMN, rowActionsColumn } from "./RecordRowActions";
import { resultLevel } from "./review-levels";
import { ResultOriginNote, resultOutcomeDef } from "./ReviewBadges";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeName } from "./ReviewPracticeLink";
import { useTracked } from "./use-tracked";

/** See `FeedbackResultsState`: a filtered empty state has to carry the way out of itself. */
export type ResultRowsState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "empty"; filtered: false }
	| { status: "empty"; filtered: true; onClearFilters: () => void }
	| { status: "ready"; results: ReviewResult[] };

export interface ResultRowsProps {
	state: ResultRowsState;
	/**
	 * `false` for an order other than time, such as "Most actionable first": the rows then sit in one
	 * body and each names its own day, since headings per day would repeat a day each time it came
	 * back.
	 */
	byDay?: boolean;
}

const LIST_LABEL = "Results";

/** The heads of the Results table, which every row below fills. */
export const RESULT_COLUMNS: readonly RecordTableColumn[] = [
	{ label: "Recorded", className: "w-24 whitespace-nowrap" },
	{ label: "Result" },
	{ label: "Work" },
	{ label: "Developer", className: "w-44" },
	rowActionsColumn(),
	ROW_OPEN_COLUMN,
];

const RESULT_WORDS: RecordResultsWords = {
	errorTitle: "We could not load results",
	icon: ScanSearchIcon,
	emptyTitle: "No results yet",
	filteredTitle: "No results match these filters",
	emptyDescription: "Results appear after a practice review completes.",
};

/** Results by day, drawn as the Reviews table draws reviews. */
export function ResultRows({ state, byDay = true }: ResultRowsProps) {
	const rows = state.status === "ready" ? state.results : [];
	return (
		<RecordResults
			state={state}
			aria-label={LIST_LABEL}
			columns={RESULT_COLUMNS}
			words={RESULT_WORDS}
		>
			{(empty) =>
				byDay ? (
					<RecordDayTable
						aria-label={LIST_LABEL}
						columns={RESULT_COLUMNS}
						rows={rows}
						dateOf={(result) => result.observedAt}
						rowKey={(result) => result.id}
						renderRow={(result) => <ResultRow result={result} />}
						empty={empty}
					/>
				) : (
					<RecordGroupedTable
						aria-label={LIST_LABEL}
						columns={RESULT_COLUMNS}
						groups={byActionability(rows)}
						rowKey={(result) => result.id}
						renderRow={(result) => <ResultRow result={result} withDay />}
						empty={empty}
					/>
				)
			}
		</RecordResults>
	);
}

/**
 * The rank "Most actionable first" sorts by, in the server's words: not met by severity, then met,
 * then not applicable and undetermined together.
 */
function actionabilityRank(result: ReviewResult): string {
	if (result.outcome === "NOT_MET") {
		return result.severity === undefined
			? OUTCOME_DEFS.NOT_MET.label
			: `${OUTCOME_DEFS.NOT_MET.label} · ${SEVERITY_DEFS[result.severity].label}`;
	}
	if (result.outcome === "MET") {
		return OUTCOME_DEFS.MET.label;
	}
	return `${OUTCOME_DEFS.NOT_APPLICABLE.label} or ${OUTCOME_DEFS.UNDETERMINED.label.toLowerCase()}`;
}

/**
 * The server's order cut into its ranks, so the table keeps the headed bodies it has by day. A rank
 * that comes back after another page still joins its heading.
 */
export function byActionability(
	rows: readonly ReviewResult[],
): { label: string; rows: ReviewResult[] }[] {
	const groups: { label: string; rows: ReviewResult[] }[] = [];
	for (const row of rows) {
		const label = actionabilityRank(row);
		const group = groups.find((candidate) => candidate.label === label);
		if (group) {
			group.rows.push(row);
		} else {
			groups.push({ label, rows: [row] });
		}
	}
	return groups;
}

export interface ResultRowProps {
	result: ReviewResult;
	/** The row names its day, in a table without day headings. */
	withDay?: boolean;
}

/**
 * Only a costly shortfall names its severity in the row: a minor or informational one is the
 * ordinary case, and the result's sheet says it.
 */
function costlySeverity(result: ReviewResult) {
	return result.outcome === "NOT_MET" &&
		(result.severity === "CRITICAL" || result.severity === "MAJOR")
		? SEVERITY_DEFS[result.severity]
		: undefined;
}

/**
 * One result, drawn as a review row is: the time and the outcome as an icon, named with the problems
 * a not met result counts, what it says with its practice as text, the work and who it is about;
 * what came of it is the trace's to tell. Badges are kept for what went wrong: a claim that is no
 * longer current, a correction, a dispute. The whole row opens the result's sheet, and that is its
 * one link; Track and Trace end the row.
 */
export function ResultRow({ result, withDay = false }: ResultRowProps) {
	const today = new Date(useNow());
	const tracked = useTracked({ kind: "result", id: result.id });
	const severity = costlySeverity(result);
	const hasException =
		result.claimCurrentness !== "CURRENT" ||
		result.invalidatedAt !== undefined ||
		result.disputedAt !== undefined;
	return (
		<PracticeTableRow
			link={{
				text: "Open result",
				name: `${result.summary}, ${formatDayTime(result.observedAt, today)}`,
				render: <DetailStackLink entry={resultLevel(result.id)} single />,
			}}
			variant={tracked ? "highlighted" : undefined}
			action={
				<RecordRowActions
					record={{ kind: "result", id: result.id }}
					agentJobId={result.agentJobId}
					name={`result ${result.summary}`}
				/>
			}
		>
			<RecordTimeCell
				status={resultOutcomeDef(result.outcome, result.problemCount)}
				at={result.observedAt}
				statusTooltip={false}
				withDay={withDay}
			/>
			<TableCell className="align-top whitespace-normal">
				<span className="flex min-w-0 flex-col items-start gap-1.5">
					<span className="max-w-md text-sm font-semibold break-words">{result.summary}</span>
					<span className="flex max-w-full flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
						<ReviewPracticeName practiceName={result.practiceName} group={result.group} />
						{severity && <StatusLabel def={severity} className="text-xs" />}
						<ResultOriginNote origin={result.origin} />
					</span>
					{hasException && (
						<span className="flex flex-wrap items-center gap-1.5">
							<ClaimCurrentnessBadge currentness={result.claimCurrentness} />
							{result.invalidatedAt !== undefined && <StatusBadge def={MARKED_INCORRECT_DEF} />}
							{result.disputedAt !== undefined && (
								<StatusBadge def={DEVELOPER_RESPONSE_DEFS.DISPUTED} />
							)}
						</span>
					)}
				</span>
			</TableCell>
			<ReviewRunWorkCell work={result.reviewedWork} linked={false} />
			<TableCell className="align-top whitespace-normal">
				<ReviewPerson person={result.subject} />
			</TableCell>
		</PracticeTableRow>
	);
}
