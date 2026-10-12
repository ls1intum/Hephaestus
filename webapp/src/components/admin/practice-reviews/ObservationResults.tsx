import { ScanSearchIcon } from "lucide-react";

import type { ReviewObservation } from "@/api/types.gen";
import { StatusBadge } from "@/components/common/StatusBadge";
import { useNow } from "@/components/common/use-now";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { ReviewRunWorkCell } from "@/components/practice-trace/ReviewRunDayTable";
import { ClaimCurrentnessBadge } from "@/components/practice-vocabulary/ClaimCurrentness";
import { DEVELOPER_RESPONSE_DEFS } from "@/components/practice-vocabulary/observation-dispute-defs";
import { MARKED_INCORRECT_DEF } from "@/components/practice-vocabulary/observation-invalidation-defs";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import { PracticeTableRow } from "@/components/practice-vocabulary/PracticeTable";
import {
	RecordDayTable,
	RecordGroupedTable,
	type RecordTableColumn,
	RecordTimeCell,
} from "@/components/practice-vocabulary/RecordTable";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { StatusLabel } from "@/components/practice-vocabulary/StatusTooltip";
import { TableCell } from "@/components/ui/table";
import { formatDayTime } from "@/lib/dates";

import { RecordResults, type RecordResultsWords } from "./RecordResults";
import { RecordRowActions, ROW_OPEN_COLUMN, rowActionsColumn } from "./RecordRowActions";
import { observationLevel } from "./review-levels";
import { ObservationOriginNote } from "./ReviewBadges";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeName } from "./ReviewPracticeLink";
import { useTracked } from "./use-tracked";

/** See `FeedbackResultsState`: a filtered empty state has to carry the way out of itself. */
export type ObservationResultsState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "empty"; filtered: false }
	| { status: "empty"; filtered: true; onClearFilters: () => void }
	| { status: "ready"; observations: ReviewObservation[] };

export interface ObservationResultsProps {
	state: ObservationResultsState;
	/**
	 * `false` for an order other than time, such as "Most actionable first": the rows then sit in one
	 * body and each names its own day, since headings per day would repeat a day each time it came
	 * back.
	 */
	byDay?: boolean;
}

const LIST_LABEL = "Observations";

/** The heads of the Observations table, which every row below fills. */
export const OBSERVATION_COLUMNS: readonly RecordTableColumn[] = [
	{ label: "Observed", className: "w-24 whitespace-nowrap" },
	{ label: "Observation" },
	{ label: "Work" },
	{ label: "Developer", className: "w-44" },
	rowActionsColumn(),
	ROW_OPEN_COLUMN,
];

const OBSERVATION_WORDS: RecordResultsWords = {
	errorTitle: "We could not load observations",
	icon: ScanSearchIcon,
	emptyTitle: "No observations yet",
	filteredTitle: "No observations match these filters",
	emptyDescription: "Observations appear after a practice review completes.",
};

/** Observations by day, drawn as the Reviews table draws reviews. */
export function ObservationResults({ state, byDay = true }: ObservationResultsProps) {
	const rows = state.status === "ready" ? state.observations : [];
	return (
		<RecordResults
			state={state}
			aria-label={LIST_LABEL}
			columns={OBSERVATION_COLUMNS}
			words={OBSERVATION_WORDS}
		>
			{(empty) =>
				byDay ? (
					<RecordDayTable
						aria-label={LIST_LABEL}
						columns={OBSERVATION_COLUMNS}
						rows={rows}
						dateOf={(observation) => observation.observedAt}
						rowKey={(observation) => observation.id}
						renderRow={(observation) => <ObservationRow observation={observation} />}
						empty={empty}
					/>
				) : (
					<RecordGroupedTable
						aria-label={LIST_LABEL}
						columns={OBSERVATION_COLUMNS}
						groups={byActionability(rows)}
						rowKey={(observation) => observation.id}
						renderRow={(observation) => <ObservationRow observation={observation} withDay />}
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
function actionabilityRank(observation: ReviewObservation): string {
	if (observation.outcome === "NOT_MET") {
		return observation.severity === undefined
			? OUTCOME_DEFS.NOT_MET.label
			: `${OUTCOME_DEFS.NOT_MET.label} · ${SEVERITY_DEFS[observation.severity].label}`;
	}
	if (observation.outcome === "MET") {
		return OUTCOME_DEFS.MET.label;
	}
	return `${OUTCOME_DEFS.NOT_APPLICABLE.label} or ${OUTCOME_DEFS.UNDETERMINED.label.toLowerCase()}`;
}

/**
 * The server's order cut into its ranks, so the table keeps the headed bodies it has by day. A rank
 * that comes back after another page still joins its heading.
 */
export function byActionability(
	rows: readonly ReviewObservation[],
): { label: string; rows: ReviewObservation[] }[] {
	const groups: { label: string; rows: ReviewObservation[] }[] = [];
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

export interface ObservationRowProps {
	observation: ReviewObservation;
	/** The row names its day, in a table without day headings. */
	withDay?: boolean;
}

/**
 * Only a costly shortfall names its severity in the row: a minor or informational one is the
 * ordinary case, and the observation's sheet says it.
 */
function costlySeverity(observation: ReviewObservation) {
	return observation.outcome === "NOT_MET" &&
		(observation.severity === "CRITICAL" || observation.severity === "MAJOR")
		? SEVERITY_DEFS[observation.severity]
		: undefined;
}

/**
 * One observation, drawn as a review row is: the time and the verdict as an icon, what it says with
 * its practice as text, the work and who it is about; what came of it is the trace's to tell. Badges are kept for what went
 * wrong: a claim that is no longer current, a correction, a dispute. The whole row opens the
 * observation's sheet, and that is its one link; Track and Trace end the row.
 */
export function ObservationRow({ observation, withDay = false }: ObservationRowProps) {
	const today = new Date(useNow());
	const tracked = useTracked({ kind: "observation", id: observation.id });
	const severity = costlySeverity(observation);
	const hasException =
		observation.claimCurrentness !== "CURRENT" ||
		observation.invalidatedAt !== undefined ||
		observation.disputedAt !== undefined;
	return (
		<PracticeTableRow
			link={{
				text: "Open observation",
				name: `${observation.summary}, ${formatDayTime(observation.observedAt, today)}`,
				render: <DetailStackLink entry={observationLevel(observation.id)} single />,
			}}
			variant={tracked ? "highlighted" : undefined}
			action={
				<RecordRowActions
					record={{ kind: "observation", id: observation.id }}
					agentJobId={observation.agentJobId}
					name={`observation ${observation.summary}`}
				/>
			}
		>
			<RecordTimeCell
				status={OUTCOME_DEFS[observation.outcome]}
				at={observation.observedAt}
				statusTooltip={false}
				withDay={withDay}
			/>
			<TableCell className="align-top whitespace-normal">
				<span className="flex min-w-0 flex-col items-start gap-1.5">
					<span className="max-w-md text-sm font-semibold break-words">{observation.summary}</span>
					<span className="flex max-w-full flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
						<ReviewPracticeName practiceName={observation.practiceName} group={observation.group} />
						{severity && <StatusLabel def={severity} className="text-xs" />}
						<ObservationOriginNote origin={observation.origin} />
					</span>
					{hasException && (
						<span className="flex flex-wrap items-center gap-1.5">
							<ClaimCurrentnessBadge currentness={observation.claimCurrentness} />
							{observation.invalidatedAt !== undefined && (
								<StatusBadge def={MARKED_INCORRECT_DEF} />
							)}
							{observation.disputedAt !== undefined && (
								<StatusBadge def={DEVELOPER_RESPONSE_DEFS.DISPUTED} />
							)}
						</span>
					)}
				</span>
			</TableCell>
			<ReviewRunWorkCell work={observation.reviewedWork} linked={false} />
			<TableCell className="align-top whitespace-normal">
				<ReviewPerson person={observation.subject} />
			</TableCell>
		</PracticeTableRow>
	);
}
