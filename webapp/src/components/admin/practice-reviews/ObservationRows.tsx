import { CircleAlertIcon } from "lucide-react";

import type { PracticeReviewObservationRow } from "@/api/types.gen";
import { useNow } from "@/components/common/use-now";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { ReviewRunWorkCell } from "@/components/practice-trace/ReviewRunDayTable";
import { PracticeTableRow } from "@/components/practice-vocabulary/PracticeTable";
import {
	RecordDayTable,
	type RecordTableColumn,
	RecordTimeCell,
} from "@/components/practice-vocabulary/RecordTable";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { TableCell } from "@/components/ui/table";
import { formatDayTime } from "@/lib/dates";

import { RecordResults, type RecordResultsWords } from "./RecordResults";
import { RecordRowActions, ROW_OPEN_COLUMN, rowActionsColumn } from "./RecordRowActions";
import { citationLocation, citationSide } from "./ResultEvidence";
import { ResultExceptions } from "./ResultRows";
import { resultLevel } from "./review-levels";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeName } from "./ReviewPracticeLink";
import { useTracked } from "./use-tracked";

export type ObservationRowsState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "empty"; filtered: false }
	| { status: "empty"; filtered: true; onClearFilters: () => void }
	| { status: "ready"; observations: PracticeReviewObservationRow[] };

const LIST_LABEL = "Problems";

export const OBSERVATION_COLUMNS: readonly RecordTableColumn[] = [
	{ label: "Recorded", className: "w-24 whitespace-nowrap" },
	{ label: "Problem" },
	{ label: "Work" },
	{ label: "Developer", className: "w-44" },
	rowActionsColumn(),
	ROW_OPEN_COLUMN,
];

const OBSERVATION_WORDS: RecordResultsWords = {
	errorTitle: "We could not load problems",
	icon: CircleAlertIcon,
	emptyTitle: "No problems yet",
	filteredTitle: "No problems match these filters",
	emptyDescription: "A problem appears when a review records a not met result.",
};

/** Stands in for the summary and the place where the result's sheet hides its evidence. */
const HIDDEN_SUMMARY = "Details hidden";

export interface ObservationRowsProps {
	state: ObservationRowsState;
}

/** Problems by day, drawn as the Results table draws results. */
export function ObservationRows({ state }: ObservationRowsProps) {
	const rows = state.status === "ready" ? state.observations : [];
	return (
		<RecordResults
			state={state}
			aria-label={LIST_LABEL}
			columns={OBSERVATION_COLUMNS}
			words={OBSERVATION_WORDS}
		>
			{(empty) => (
				<RecordDayTable
					aria-label={LIST_LABEL}
					columns={OBSERVATION_COLUMNS}
					rows={rows}
					dateOf={(observation) => observation.observedAt}
					rowKey={(observation) => observation.id}
					renderRow={(observation) => <ObservationRow observation={observation} />}
					empty={empty}
				/>
			)}
		</RecordResults>
	);
}

/** The place of the problem's first passage, as the result's sheet names it; none when hidden. */
function placeOf(observation: PracticeReviewObservationRow) {
	const { sourceKind, path, startLine, endLine, side } = observation;
	if (sourceKind === undefined || path === undefined) {
		return;
	}
	const place = { sourceKind, path, startLine, endLine, side };
	// After the change is where a problem usually is, so a row names only the side before it.
	return {
		location: citationLocation(place),
		before: side === "OLD" ? citationSide(place) : undefined,
	};
}

export interface ObservationRowProps {
	observation: PracticeReviewObservationRow;
}

/**
 * One problem: its severity as the row's icon, what it says with its practice and place, the work
 * and who it is about. Its result's correction, dispute or lost currentness shows as on the result's
 * row. The row opens the result it belongs to; Track and Trace act on that result.
 */
export function ObservationRow({ observation }: ObservationRowProps) {
	const today = new Date(useNow());
	const tracked = useTracked({ kind: "result", id: observation.practiceResultId });
	const summary = observation.summary ?? HIDDEN_SUMMARY;
	const place = placeOf(observation);
	// A hidden summary is the same on every hidden row, so the name also says whose result it is.
	const name = `${summary}, ${observation.practiceName}, ${observation.reviewedWork.label}, ${formatDayTime(observation.observedAt, today)}`;
	return (
		<PracticeTableRow
			link={{
				text: "Open result",
				name,
				render: <DetailStackLink entry={resultLevel(observation.practiceResultId)} single />,
			}}
			variant={tracked ? "highlighted" : undefined}
			action={
				<RecordRowActions
					record={{ kind: "result", id: observation.practiceResultId }}
					agentJobId={observation.agentJobId}
					name={`result ${name}`}
				/>
			}
		>
			<RecordTimeCell
				status={SEVERITY_DEFS[observation.severity]}
				at={observation.observedAt}
				statusTooltip={false}
			/>
			<TableCell className="align-top whitespace-normal">
				<span className="flex min-w-0 flex-col items-start gap-1.5">
					<span
						className={
							observation.evidenceHidden
								? "text-sm text-muted-foreground"
								: "max-w-md text-sm font-semibold break-words"
						}
					>
						{summary}
					</span>
					<span className="flex max-w-full flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
						<ReviewPracticeName practiceName={observation.practiceName} group={observation.group} />
						{place !== undefined && <code className="break-all">{place.location}</code>}
						{place?.before !== undefined && <span>{place.before}</span>}
					</span>
					<ResultExceptions result={observation} />
				</span>
			</TableCell>
			<ReviewRunWorkCell work={observation.reviewedWork} linked={false} />
			<TableCell className="align-top whitespace-normal">
				<ReviewPerson person={observation.subject} />
			</TableCell>
		</PracticeTableRow>
	);
}
