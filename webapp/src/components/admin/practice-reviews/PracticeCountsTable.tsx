import { ScanSearchIcon, TriangleAlertIcon } from "lucide-react";
import { useState } from "react";

import { cn } from "cn";
import type { PracticeReviewCounts } from "@/api/types.gen";
import { STALE } from "@/components/activity/activity-tones";
import { FacetMultiSelect, type FacetOption } from "@/components/common/FacetMultiSelect";
import { InfoLines, InfoTip } from "@/components/common/InfoTip";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { GroupPill } from "@/components/practice-vocabulary/GroupPill";
import { MARKED_INCORRECT_DEF } from "@/components/practice-vocabulary/observation-invalidation-defs";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import { OPEN_ROW_BAR, PracticeTableRow } from "@/components/practice-vocabulary/PracticeTable";
import {
	RecordTable,
	type RecordTableColumn,
	RecordTableSkeleton,
} from "@/components/practice-vocabulary/RecordTable";
import { Badge } from "@/components/ui/badge";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { TableCell, TableRow } from "@/components/ui/table";

import { OVERVIEW_TYPE } from "./overview-type-scale";
import {
	feedbackComposed,
	type PracticeTotals,
	practiceTotals,
	practiceTotalsNotes,
} from "./practice-totals";
import { needsALookFirst, PRACTICE_MARKER_DEFS, practiceMarker } from "./review-attention";
import { PRACTICE_MARKER_THRESHOLDS } from "./review-attention-thresholds";
import { practiceLevel } from "./review-levels";
import { familyCount, observationSlots, slotsTotal } from "./review-outcomes";
import type { OverviewRegionState } from "./review-states";
import { ReviewSortSelect, type ReviewSortItem } from "./ReviewSortSelect";
import { type CurrentPractice, isTurnedOff, ShowTurnedOffSwitch } from "./ShowTurnedOffSwitch";

export interface PracticeCountsTableProps {
	state: OverviewRegionState;
	/**
	 * The workspace's practices, to tell which are turned off today; absent while they load, when
	 * none is hidden.
	 */
	practices: readonly CurrentPractice[] | undefined;
	/**
	 * The Group filter and the turned-off switch, held by the route so they live in the URL; the
	 * table keeps them itself when absent.
	 */
	filter?: PracticeCountsFilter;
}

export interface PracticeCountsFilter {
	groups: string[];
	showTurnedOff: boolean;
	/** The ordering; absent, the default, "Needs a look first". */
	sort?: PracticeSort;
	onChange: (
		patch: Partial<Pick<PracticeCountsFilter, "groups" | "showTurnedOff" | "sort">>,
	) => void;
}

/** The groups of the practices the table counts, as the Group filter offers them. */
function groupOptions(rows: readonly PracticeReviewCounts[]): FacetOption[] {
	const options: FacetOption[] = [];
	for (const row of rows) {
		if (row.group && !options.some((option) => option.value === row.group?.slug)) {
			options.push({ value: row.group.slug, label: row.group.name });
		}
	}
	return options;
}

const SKELETON_ROWS = 4;

/** The heads, each one line: the outcome's own word, and the long meaning in the head's (i). */
const NOT_MET = OUTCOME_DEFS.NOT_MET.label;
const DELIVERED = "Feedback delivered";

/**
 * A count column: centred, head and figures alike, so each head stands right over its figures.
 * Every head may take two lines, so the six columns stay narrow and the table fits one page; its
 * (i) sits beside the words. A figure never wraps, so every row keeps one height. Stacked on a
 * narrow screen, the figures stay at the start of their line.
 */
const COUNT_COLUMN = "w-28 whitespace-normal text-center";
const COUNT_CELL = "@2xl:text-center";

const { minimumObservations, neverAppliesShare, oftenUndecidedShare } = PRACTICE_MARKER_THRESHOLDS;

/** How each column's count is reached, for its (i). */
const COLUMN_INFO = {
	observations:
		"Every observation the reviews recorded about this practice in the range: met, not met, not applicable and undetermined.",
	notMet:
		"The observations of this practice whose outcome was not met, out of all its observations.",
	notApplicable: `The observations where the practice did not apply to the work, out of all its observations. From ${minimumObservations} observations, a practice where ${Math.round(neverAppliesShare * 100)} % or more do not apply is marked "Never applies here".`,
	undetermined: `The observations where the review could not decide, out of all its observations. From ${minimumObservations} observations, a practice where ${Math.round(oftenUndecidedShare * 100)} % or more are undetermined is marked "Often undecided".`,
	markedIncorrect: `The observations an admin marked incorrect, out of all its observations. They keep their outcome too. ${MARKED_INCORRECT_DEF.note}`,
	feedbackDelivered:
		"The feedback about this practice that reached the developer, in full or in part, out of all the feedback composed about it in the range: delivered, waiting, withheld or failed. A piece replaced by a newer one is not counted.",
} as const;

export type PracticeSort = "ATTENTION" | "BUSIEST";

/** The orderings the table offers, the default first. */
export const PRACTICE_SORTS: readonly [ReviewSortItem<PracticeSort>, ReviewSortItem<PracticeSort>] =
	[
		{ value: "ATTENTION", label: "Needs a look first" },
		{ value: "BUSIEST", label: "Busiest first" },
	];

const TABLE_LABELS: Record<PracticeSort, string> = {
	ATTENTION: "Practices, the ones that need a look first",
	BUSIEST: "Practices, busiest first",
};

/**
 * A practice's row has no time to stand beside, so on a narrow screen its name heads the row and each
 * count, named by its column, takes a line of its own across the row.
 */
const STACKED_FULL_WIDTH =
	"@max-2xl:[&>td]:col-span-2! @max-2xl:[&>td]:col-start-1! @max-2xl:[&>td]:row-span-1!";

/**
 * Each practice the reviews checked in the range: its number of observations, then each outcome as
 * "19 of 89", out of that practice's observations, so the reader can judge the share without a
 * percentage to compare, and the feedback delivered out of the feedback composed about it
 * (`feedbackComposed`). Each column's (i) says how its count is reached. By default the
 * practices that need a look lead, each with one marker: one that never applies here costs reviews
 * and tells nobody anything, and one that is often undecided may lack the evidence it needs
 * (`review-attention-thresholds.ts`). The most observations marked incorrect, then undetermined,
 * follow, then the rest busiest first; the reader can sort the whole table busiest first, and the
 * choice is kept in the URL with the filters. It is drawn as the Reviews table is, without the
 * days: a practice's row opens its sheet, and as an overview, not a stream, it has no Track or
 * Trace. Practices are ranked by volume; people never are (ADR 0045).
 *
 * A practice that is turned off today is hidden until the reader asks for it: its counts are history
 * that no new review adds to. A Group filter narrows the rows to the chosen groups. The foot of the
 * table holds the totals of every practice, the figures of the tiles above (`practice-totals.ts`),
 * and a line under it says what the filters hide and why Feedback delivered adds up differently.
 */
export function PracticeCountsTable({ state, practices, filter }: PracticeCountsTableProps) {
	const [ownGroups, setOwnGroups] = useState<string[]>([]);
	const [ownShowTurnedOff, setOwnShowTurnedOff] = useState(false);
	const [ownSort, setOwnSort] = useState<PracticeSort>("ATTENTION");
	const sort = filter ? (filter.sort ?? "ATTENTION") : ownSort;
	const tableLabel = TABLE_LABELS[sort];
	const groups = filter?.groups ?? ownGroups;
	const showTurnedOff = filter?.showTurnedOff ?? ownShowTurnedOff;
	const setGroups = (next: string[]) =>
		filter ? filter.onChange({ groups: next }) : setOwnGroups(next);
	const setShowTurnedOff = (next: boolean) =>
		filter ? filter.onChange({ showTurnedOff: next }) : setOwnShowTurnedOff(next);
	const setSort = (next: PracticeSort) =>
		filter ? filter.onChange({ sort: next }) : setOwnSort(next);
	// Another range's empty answer, standing in while this one loads, says nothing about this one:
	// the rows wait in their shape instead of claiming the range is empty.
	const recorded =
		state.status === "ready" && !(state.stale && state.overview.practices.length === 0)
			? state.overview.practices
			: undefined;
	const options = groupOptions(recorded ?? []);
	// A group this range has no row of cannot be shown or cleared in the facet, so it filters nothing.
	const activeGroups = groups.filter((group) => options.some((option) => option.value === group));
	const inGroups = recorded?.filter(
		(counts) =>
			activeGroups.length === 0 ||
			(counts.group !== undefined && activeGroups.includes(counts.group.slug)),
	);
	const turnedOff = (inGroups ?? []).filter((counts) =>
		isTurnedOff(counts.practiceSlug, practices),
	).length;
	const filtered = inGroups?.filter(
		(counts) => showTurnedOff || !isTurnedOff(counts.practiceSlug, practices),
	);
	const shown = filtered && sort === "ATTENTION" ? needsALookFirst(filtered) : filtered;
	// The tiles' own figures under the rows, and a line wherever the rows add up to something else.
	const totals =
		state.status === "ready" && shown ? practiceTotals(state.overview, shown) : undefined;
	const notes = totals ? practiceTotalsNotes(totals) : [];
	const columns: readonly RecordTableColumn[] = [
		{ label: "Practice" },
		// Six columns of counts, each head at most two lines; the practice's name takes the rest.
		{
			label: "Number of observations",
			className: COUNT_COLUMN,
			info: <ColumnInfo label="Number of observations">{COLUMN_INFO.observations}</ColumnInfo>,
		},
		{
			label: NOT_MET,
			className: COUNT_COLUMN,
			info: <ColumnInfo label={NOT_MET}>{COLUMN_INFO.notMet}</ColumnInfo>,
		},
		{
			label: "Not applicable",
			className: COUNT_COLUMN,
			info: <ColumnInfo label="Not applicable">{COLUMN_INFO.notApplicable}</ColumnInfo>,
		},
		{
			label: "Undetermined",
			className: COUNT_COLUMN,
			info: <ColumnInfo label="Undetermined">{COLUMN_INFO.undetermined}</ColumnInfo>,
		},
		{
			label: MARKED_INCORRECT_DEF.label,
			className: COUNT_COLUMN,
			info: (
				<ColumnInfo label={MARKED_INCORRECT_DEF.label}>{COLUMN_INFO.markedIncorrect}</ColumnInfo>
			),
		},
		{
			label: DELIVERED,
			className: COUNT_COLUMN,
			info: <ColumnInfo label={DELIVERED}>{COLUMN_INFO.feedbackDelivered}</ColumnInfo>,
		},
		{ label: "Open", srOnly: true, className: "w-32" },
	];
	if (recorded?.length === 0) {
		return (
			<Empty variant="outlined">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<ScanSearchIcon />
					</EmptyMedia>
					<EmptyTitle>No practice was checked in this range</EmptyTitle>
					<EmptyDescription>
						Practices appear here once a review records an observation or feedback about them.
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}
	return (
		<div className="space-y-3">
			<div role="group" aria-label="Practice filters" className="flex flex-wrap items-center gap-2">
				<FacetMultiSelect
					title="Group"
					options={options}
					selected={groups}
					onChange={setGroups}
					disabled={recorded === undefined}
					emptyLabel="No groups in this range"
				/>
				<ShowTurnedOffSwitch
					checked={showTurnedOff}
					onCheckedChange={setShowTurnedOff}
					hidden={turnedOff}
				/>
				<div className="ml-auto">
					<ReviewSortSelect
						items={PRACTICE_SORTS}
						value={sort}
						onChange={(next) => setSort(next ?? "ATTENTION")}
					/>
				</div>
			</div>
			{shown ? (
				<RecordTable
					aria-label={tableLabel}
					columns={columns}
					rows={shown}
					rowKey={(counts) => counts.practiceSlug}
					renderRow={(counts) => <PracticeCountsRow counts={counts} />}
					empty={
						<span className="text-muted-foreground">
							No practice in this range matches these filters.
						</span>
					}
					totals={totals && <TotalsRow totals={totals} />}
					footer={
						<>
							{notes.length > 0 && <TotalsNotes notes={notes} />}
							<StackedColumnInfo />
						</>
					}
					busy={state.status === "ready" && state.stale}
					tableClassName={cn(state.status === "ready" && state.stale && STALE)}
				/>
			) : (
				<RecordTableSkeleton aria-label={tableLabel} columns={columns} rows={SKELETON_ROWS} />
			)}
		</div>
	);
}

/** A count, muted when there is none of it; with `of`, out of that whole: "19 of 89". */
function Count({ value, of }: { value: number; of?: number }) {
	return (
		<span className={cn(value === 0 && "text-muted-foreground")}>
			{value}
			{of !== undefined && of > 0 && <span className="text-muted-foreground"> of {of}</span>}
		</span>
	);
}

/**
 * The last row: every practice of the range, filtered or not, in the figures the tiles above show,
 * so the table and the tiles say the same.
 */
function TotalsRow({ totals }: { totals: PracticeTotals }) {
	const cells: [string, number, number?][] = [
		["Observations", totals.observations],
		[NOT_MET, totals.notMet, totals.observations],
		["Not applicable", totals.notApplicable, totals.observations],
		["Undetermined", totals.undetermined, totals.observations],
		[MARKED_INCORRECT_DEF.label, totals.markedIncorrect, totals.observations],
		[DELIVERED, totals.feedbackDelivered, totals.feedbackComposed],
	];
	return (
		<TableRow variant="static" className={STACKED_FULL_WIDTH}>
			<TableCell className="font-semibold">All practices</TableCell>
			{cells.map(([label, value, of]) => (
				<TableCell
					key={label}
					numeric
					data-label={label}
					className={cn(COUNT_CELL, "font-semibold")}
				>
					<Count value={value} of={of} />
				</TableCell>
			))}
			<TableCell />
		</TableRow>
	);
}

/** Why the rows add up to something other than the totals, under the table. */
function TotalsNotes({ notes }: { notes: readonly string[] }) {
	return (
		<div className={cn("space-y-1 border-t px-3 py-2", OVERVIEW_TYPE.note)}>
			{notes.map((note) => (
				<p key={note}>{note}</p>
			))}
		</div>
	);
}

/**
 * Every column's (i) in one, for the stacked layout: there the heads are hidden, so their (i)
 * cannot be reached, and the thresholds behind a marker are only said there.
 */
function StackedColumnInfo() {
	const label = "How the counts are reached";
	return (
		<div
			className={cn("flex items-center gap-1 border-t px-3 py-2 @2xl:hidden", OVERVIEW_TYPE.note)}
		>
			{label}
			<InfoTip label={label}>
				<InfoLines
					lines={[
						{ term: "Number of observations", text: COLUMN_INFO.observations },
						{ term: NOT_MET, text: COLUMN_INFO.notMet },
						{ term: "Not applicable", text: COLUMN_INFO.notApplicable },
						{ term: "Undetermined", text: COLUMN_INFO.undetermined },
						{ term: MARKED_INCORRECT_DEF.label, text: COLUMN_INFO.markedIncorrect },
						{ term: DELIVERED, text: COLUMN_INFO.feedbackDelivered },
					]}
				/>
			</InfoTip>
		</div>
	);
}

/** A column head's (i): how its count is reached, opened by hover, focus or a tap. */
function ColumnInfo({ label, children }: { label: string; children: string }) {
	return (
		<InfoTip label={`How ${label} is counted`} className="font-normal">
			<p>{children}</p>
		</InfoTip>
	);
}

/**
 * The one marker a practice earns, beside its name on the same line, its reason with the numbers
 * read out and on hover.
 */
function Marker({ counts }: { counts: PracticeReviewCounts }) {
	const found = practiceMarker(counts.observations);
	if (found === undefined) {
		return null;
	}
	const def = PRACTICE_MARKER_DEFS[found.marker];
	const reason = def.reason(found.part, found.whole);
	return (
		<Badge variant="outline" title={reason} data-marker={found.marker} className="shrink-0">
			<TriangleAlertIcon aria-hidden />
			{def.label}
			<span className="sr-only">. {reason}</span>
		</Badge>
	);
}

function PracticeCountsRow({ counts }: { counts: PracticeReviewCounts }) {
	const observations = slotsTotal(observationSlots(counts.observations));
	return (
		<PracticeTableRow
			className={STACKED_FULL_WIDTH}
			link={{
				text: "Open practice",
				name: counts.practiceName,
				render: <DetailStackLink entry={practiceLevel(counts.practiceSlug)} single />,
			}}
		>
			{/* One line, whatever the name or marker, so every row is as high as the next: the name
			    truncates, with the whole of it on hover, and the marker stays beside it. */}
			<TableCell className={cn(OPEN_ROW_BAR, "@2xl:w-full @2xl:max-w-0")}>
				<span className="flex min-w-0 items-center gap-2">
					{counts.group && (
						<GroupPill
							size="sm"
							slug={counts.group.slug}
							name={counts.group.name}
							icon={counts.group.icon}
							color={counts.group.color}
							srLabel
						/>
					)}
					<span className="min-w-0 truncate font-semibold" title={counts.practiceName}>
						{counts.practiceName}
					</span>
					<Marker counts={counts} />
				</span>
			</TableCell>
			<TableCell numeric data-label="Observations" className={COUNT_CELL}>
				<Count value={observations} />
			</TableCell>
			<TableCell numeric data-label={NOT_MET} className={COUNT_CELL}>
				<Count value={counts.observations.notMet} of={observations} />
			</TableCell>
			<TableCell numeric data-label="Not applicable" className={COUNT_CELL}>
				<Count value={counts.observations.notApplicable} of={observations} />
			</TableCell>
			<TableCell numeric data-label="Undetermined" className={COUNT_CELL}>
				<Count value={counts.observations.undetermined} of={observations} />
			</TableCell>
			<TableCell numeric data-label={MARKED_INCORRECT_DEF.label} className={COUNT_CELL}>
				<Count value={counts.observationsInvalidated} of={observations} />
			</TableCell>
			<TableCell numeric data-label={DELIVERED} className={COUNT_CELL}>
				<Count
					value={familyCount(counts.feedback, "DELIVERED")}
					of={feedbackComposed(counts.feedback)}
				/>
			</TableCell>
		</PracticeTableRow>
	);
}
