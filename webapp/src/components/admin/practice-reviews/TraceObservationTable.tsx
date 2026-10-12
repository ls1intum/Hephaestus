import { ChevronRightIcon } from "lucide-react";
import { use } from "react";

import { cn } from "cn";
import type { ReviewLineage, ReviewLineageObservation } from "@/api/types.gen";
import type { BadgeVariant } from "@/components/common/status-def";
import { DEVELOPER_RESPONSE_DEFS } from "@/components/practice-vocabulary/observation-dispute-defs";
import { MARKED_INCORRECT_DEF } from "@/components/practice-vocabulary/observation-invalidation-defs";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { Button } from "@/components/ui/button";
import { hasText } from "@/lib/text";

import { countOf, type NumberedObservation, verdictSummary } from "./lineage-chain";
import { answeredEarlier, reviewStepState } from "./lineage-story";
import { LineageObservationChip } from "./LineageObservationChip";
import { LineageTimes, OffPathNote, Step, StepTime, ToneIcon } from "./LineageStep";
import { sameTraceNode, type TraceNode } from "./review-trace";
import { hiddenByFilter, type TraceFilter } from "./trace-filter";
import { type RailStepProps, StoryNode, offPath, useStory } from "./TraceStoryNode";
import { CellLabel, TRACE_ROW_CELLS, TRACE_ROW_MAIN, TraceTable, traceRowGrid } from "./TraceTable";

/** Past this many, the table lists the first observations and folds the rest behind one line. */
const OBSERVATIONS_OPEN = 12;

/**
 * The observations the review stage's table lists: every one where the table is open, otherwise
 * only the path's own, with the record the reader started from among them. The rest stay dots
 * under the count, each of which opens itself.
 */
export function observationRows(
	numbered: NumberedObservation[],
	path: NumberedObservation[] | undefined,
	self: NumberedObservation | undefined,
	listed: boolean,
): NumberedObservation[] {
	if (listed) {
		return numbered;
	}
	if (path !== undefined) {
		return path;
	}
	return self === undefined ? [] : [self];
}

/**
 * The review's observations: the count by verdict, then a table with one row per observation the
 * reader sees in full, and a numbered dot for each of the rest. The table opens and folds on its own
 * button, whatever else the trace shows; "Expand all" opens it too.
 */
export function ObservationsStep({
	lineage,
	full,
	filter,
	numbered,
	letters,
	listed,
	rows,
	onListedChange,
	path,
	self,
	traced,
	here,
	previous,
	at,
	reviewOutcome,
	othersOpen,
	onOthersOpenChange,
}: RailStepProps & { at: Date | undefined }) {
	const { display } = useStory();
	const total = lineage.observations.length + lineage.moreObservations;
	const earlier = numbered.filter(({ observation }) =>
		answeredEarlier(observation, lineage.review),
	);
	const marked = numbered.filter(({ observation }) => observation.invalidated).length;
	const empty = total === 0;
	const nothing =
		lineage.review === undefined
			? undefined
			: reviewStepState(lineage.review, reviewOutcome).assessedNothing;
	const extras = [
		earlier.length > 0 ? `${earlier.length} answered by an earlier review` : undefined,
		marked > 0 ? `${marked} marked incorrect` : undefined,
	].filter(hasText);
	const inTable = new Set(rows.map(({ observation }) => observation.id));
	const dotted = numbered.filter(({ observation }) => !inTable.has(observation.id));
	const shownRows =
		listed && !othersOpen && rows.length > OBSERVATIONS_OPEN
			? rows.slice(0, OBSERVATIONS_OPEN)
			: rows;
	const hiddenRows = rows.length - shownRows.length;
	const folded = path !== undefined || self !== undefined;
	let toggleText = `Show all ${countOf(numbered.length, "observation", "observations")} in the table`;
	if (listed) {
		toggleText = folded ? "Show only the observations on this path" : "Fold the table";
	}
	const toggle = numbered.length > 0 && (listed || dotted.length > 0) && (
		<Button
			variant="link"
			size="inline"
			className="text-muted-foreground"
			aria-expanded={listed}
			onClick={() => onListedChange(!listed)}
		>
			<ChevronRightIcon
				aria-hidden
				className={cn(
					"size-3 transition-transform motion-reduce:transition-none",
					listed && "rotate-90",
				)}
			/>
			{toggleText}
		</Button>
	);
	return (
		<Step
			dot={{
				tone: empty ? "outline" : worstTone(lineage.observations),
				hollow: empty,
				here: here?.kind === "observation" || here?.kind === "observations",
			}}
			at={at}
			gapFrom={at === undefined ? undefined : previous}
			data-step="observations"
		>
			<StoryNode
				node={{ kind: "observations" }}
				className="px-1.5 py-1"
				after={
					<>
						{shownRows.length > 0 && (
							<TraceTable
								kind="observations"
								times={display.times}
								label={listed ? "Observations of this review" : "Observations on this path"}
							>
								{shownRows.map((entry) => (
									<ObservationRow
										key={entry.observation.id}
										entry={entry}
										lineage={lineage}
										letters={letters}
										muted={offPath(traced, { kind: "observation", id: entry.observation.id })}
									/>
								))}
							</TraceTable>
						)}
						{hiddenRows > 0 && (
							<Button
								variant="link"
								size="inline"
								className="mt-1 ml-1.5 text-muted-foreground"
								onClick={() => onOthersOpenChange(true)}
							>
								{`Show ${countOf(hiddenRows, "more observation", "more observations")}`}
							</Button>
						)}
						{(dotted.length > 0 || toggle !== false) && (
							<div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 px-1">
								{dotted.length > 0 && (
									<ul
										aria-label={
											shownRows.length === 0
												? "Observations by number"
												: `${countOf(dotted.length, "other observation", "other observations")} of this review`
										}
										className="flex flex-wrap items-center gap-0.5"
									>
										{shownRows.length > 0 && (
											<li aria-hidden className="mr-0.5 text-muted-foreground">
												{`${countOf(dotted.length, "other", "others")}:`}
											</li>
										)}
										{dotted.map(({ observation, number }) => (
											<li key={observation.id}>
												<ObservationDot
													observation={observation}
													number={number}
													disputed={disputedFrom(observation.id, lineage)}
												/>
											</li>
										))}
									</ul>
								)}
								{toggle}
							</div>
						)}
					</>
				}
			>
				<ObservationsHead
					full={full}
					filter={filter}
					total={total}
					nothing={nothing}
					extras={extras}
				/>
			</StoryNode>
		</Step>
	);
}

/**
 * What the observation step says: what the review recorded, from the whole chain, so a filter
 * never makes it say the review recorded less; how much of it the filter shows; or why there is none.
 */
function ObservationsHead({
	full,
	filter,
	total,
	nothing,
	extras,
}: {
	full: ReviewLineage;
	filter: TraceFilter;
	total: number;
	nothing: string | undefined;
	extras: readonly string[];
}) {
	const wholeTotal = full.observations.length + full.moreObservations;
	const filtered = filter.kind !== "all";
	if (wholeTotal === 0) {
		return (
			<>
				<span className="block font-medium">No observations</span>
				{hasText(nothing) && <span className="block text-muted-foreground">{nothing}</span>}
			</>
		);
	}
	let shown: string | undefined;
	if (filtered && total === 0) {
		shown = hiddenByFilter(filter);
	} else if (filtered && total < wholeTotal) {
		shown = `The filter shows ${countOf(total, "of them", "of them")}.`;
	}
	return (
		<>
			<span className="block font-medium">{`It recorded ${verdictSummary(full)}.`}</span>
			{shown !== undefined && <span className="block text-muted-foreground">{shown}</span>}
			{total > 0 && extras.length > 0 && (
				<span className="block text-muted-foreground">{extras.join(" · ")}</span>
			)}
		</>
	);
}

function worstTone(observations: readonly ReviewLineageObservation[]): BadgeVariant {
	for (const outcome of ["NOT_MET", "MET", "UNDETERMINED", "NOT_APPLICABLE"] as const) {
		if (observations.some((entry) => entry.outcome === outcome)) {
			return OUTCOME_DEFS[outcome].badgeVariant;
		}
	}
	return "outline";
}

/**
 * Whether the developer disputes a piece of feedback written from the observation, as the
 * Observations list's "Developer response" filter reads it. Only that they dispute it: their rating
 * and notes are never part of the chain.
 */
function disputedFrom(observationId: string, lineage: ReviewLineage): boolean {
	return lineage.feedback.some(
		(feedback) =>
			feedback.observationIds.includes(observationId) && feedback.response?.disputed === true,
	);
}

/** One observation as its dot, which brings it into view and opens its sheet. */
function ObservationDot({
	observation,
	number,
	disputed,
}: {
	observation: ReviewLineageObservation;
	number: number;
	/** Whether the developer disputes feedback written from it. */
	disputed: boolean;
}) {
	const context = useStory();
	const node: TraceNode = { kind: "observation", id: observation.id };
	const chip = (
		<LineageObservationChip
			observation={observation}
			number={number}
			withSummary
			ringed={sameTraceNode(context.here, node)}
		/>
	);
	const { open } = context;
	// The outcome in words too, so a dot is never told by its colour alone.
	const facts = [
		OUTCOME_DEFS[observation.outcome].label,
		observation.severity && SEVERITY_DEFS[observation.severity].label.toLowerCase(),
		disputed ? "disputed" : undefined,
		observation.invalidated ? "marked incorrect" : undefined,
	].filter(hasText);
	const title = `${number} · ${facts.join(", ")}: ${observation.summary}`;
	if (open === undefined) {
		return (
			<span title={title} className="flex size-6 items-center justify-center rounded-full">
				{chip}
			</span>
		);
	}
	return (
		// 24 px, the smallest target a pointer should have to hit (WCAG 2.5.8), around the smaller chip.
		<button
			type="button"
			title={title}
			onClick={() => {
				open({ kind: "observation", id: observation.id });
			}}
			className="flex size-6 items-center justify-center rounded-full outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
		>
			{chip}
		</button>
	);
}

/** The letters of the pieces of feedback written from an observation, in the order they were written. */
function fedLetters(
	observationId: string,
	lineage: ReviewLineage,
	letters: ReadonlyMap<string, string>,
): string[] {
	return lineage.feedback
		.filter((feedback) => feedback.observationIds.includes(observationId))
		.map((feedback) => letters.get(feedback.id))
		.filter(hasText)
		.sort((a, b) => a.length - b.length || a.localeCompare(b));
}

/**
 * One observation as a row of the review stage's table: its number in its verdict's colour, its
 * practice and summary, the outcome, the severity, the pieces of feedback written from it by letter,
 * and when it was observed. A correction, a dispute or an earlier review's answer stands under the
 * summary.
 */
function ObservationRow({
	entry,
	lineage,
	letters,
	muted,
}: {
	entry: NumberedObservation;
	lineage: ReviewLineage;
	letters: ReadonlyMap<string, string>;
	muted: boolean;
}) {
	const { display } = useStory();
	const { observation, number } = entry;
	const outcome = OUTCOME_DEFS[observation.outcome];
	const severity = observation.severity && SEVERITY_DEFS[observation.severity];
	const earlier = answeredEarlier(observation, lineage.review);
	const disputed = disputedFrom(observation.id, lineage);
	const fed = fedLetters(observation.id, lineage, letters);
	const flagged = observation.invalidated || disputed || earlier;
	return (
		<li data-muted={muted} className={cn(muted && "text-muted-foreground")}>
			<OffPathNote muted={muted} />
			<StoryNode
				row
				node={{ kind: "observation", id: observation.id }}
				className={cn(traceRowGrid("observations", display.times), "px-1.5 py-1.5")}
			>
				<LineageObservationChip
					observation={observation}
					number={number}
					ringed={false}
					connector="source"
					className="mt-px"
				/>
				<span className={TRACE_ROW_CELLS}>
					<span className={TRACE_ROW_MAIN}>
						<span className="block font-medium break-words">{observation.practiceName}</span>
						<span
							className={cn(
								"block break-words text-muted-foreground",
								observation.invalidated && "line-through",
							)}
						>
							{observation.summary}
						</span>
						{flagged && (
							<span className="flex flex-wrap items-center gap-x-2 text-muted-foreground">
								{observation.invalidated && (
									<span className="inline-flex flex-wrap items-center gap-x-1 font-medium text-foreground">
										<ToneIcon def={MARKED_INCORRECT_DEF} className="size-3" />
										{MARKED_INCORRECT_DEF.label}
										{observation.markedIncorrectAt && (
											<LabeledRailTime
												label="at"
												at={observation.markedIncorrectAt}
												className="font-normal text-muted-foreground"
											/>
										)}
									</span>
								)}
								{disputed && (
									<span className="inline-flex items-center gap-1 font-medium text-foreground">
										<ToneIcon def={DEVELOPER_RESPONSE_DEFS.DISPUTED} className="size-3" />
										{DEVELOPER_RESPONSE_DEFS.DISPUTED.label}
									</span>
								)}
								{earlier && <span>Answered by an earlier review</span>}
							</span>
						)}
					</span>
					<span className="inline-flex items-center gap-1">
						<ToneIcon def={outcome} className="size-3" />
						{outcome.label}
					</span>
					{severity ? (
						<span className="inline-flex items-center gap-1">
							<ToneIcon def={severity} className="size-3" />
							{severity.label}
						</span>
					) : (
						<span aria-hidden className="hidden @lg:block" />
					)}
					<span>
						{fed.length > 0 ? (
							<>
								<CellLabel>Feedback</CellLabel>
								{fed.join(", ")}
							</>
						) : (
							<span className="text-muted-foreground">
								<span className="@lg:hidden">No feedback</span>
								<span className="hidden @lg:inline">None</span>
							</span>
						)}
					</span>
					{display.times && (
						<span className="text-muted-foreground @lg:text-right">
							<CellLabel>Observed</CellLabel>
							<StepTime at={observation.observedAt} />
						</span>
					)}
				</span>
			</StoryNode>
		</li>
	);
}

/** "Observed 3:37 pm": a time inside a step with what it is the time of, so two never read alike. */
function LabeledRailTime({
	label,
	at,
	className,
}: {
	label: string;
	at: Date;
	className?: string;
}) {
	if (!use(LineageTimes)) {
		return null;
	}
	return (
		<span className={className}>
			{label} <StepTime at={at} />
		</span>
	);
}
