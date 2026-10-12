import { ChevronRightIcon } from "lucide-react";
import { use } from "react";

import { cn } from "cn";
import type { ReviewLineage, ReviewLineageResult } from "@/api/types.gen";
import type { BadgeVariant } from "@/components/common/status-def";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import { DEVELOPER_RESPONSE_DEFS } from "@/components/practice-vocabulary/result-dispute-defs";
import { MARKED_INCORRECT_DEF } from "@/components/practice-vocabulary/result-invalidation-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { Button } from "@/components/ui/button";
import { hasText } from "@/lib/text";

import { countOf, type NumberedResult, verdictSummary } from "./lineage-chain";
import { answeredEarlier, reviewStepState } from "./lineage-story";
import { LineageResultChip } from "./LineageResultChip";
import { LineageTimes, OffPathNote, Step, StepTime, ToneIcon } from "./LineageStep";
import { sameTraceNode, type TraceNode } from "./review-trace";
import { hiddenByFilter, type TraceFilter } from "./trace-filter";
import { type RailStepProps, StoryNode, offPath, useStory } from "./TraceStoryNode";
import { CellLabel, TRACE_ROW_CELLS, TRACE_ROW_MAIN, TraceTable, traceRowGrid } from "./TraceTable";

/** Past this many, the table lists the first results and folds the rest behind one line. */
const RESULTS_OPEN = 12;

/**
 * The results the review stage's table lists: every one where the table is open, otherwise
 * only the path's own, with the record the reader started from among them. The rest stay dots
 * under the count, each of which opens itself.
 */
export function resultRows(
	numbered: NumberedResult[],
	path: NumberedResult[] | undefined,
	self: NumberedResult | undefined,
	listed: boolean,
): NumberedResult[] {
	if (listed) {
		return numbered;
	}
	if (path !== undefined) {
		return path;
	}
	return self === undefined ? [] : [self];
}

/**
 * The review's results: the count by verdict, then a table with one row per result the
 * reader sees in full, and a numbered dot for each of the rest. The table opens and folds on its own
 * button, whatever else the trace shows; "Expand all" opens it too.
 */
export function ResultsStep({
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
	const total = lineage.results.length + lineage.moreResults;
	const earlier = numbered.filter(({ result }) => answeredEarlier(result, lineage.review));
	const marked = numbered.filter(({ result }) => result.invalidated).length;
	const empty = total === 0;
	const nothing =
		lineage.review === undefined
			? undefined
			: reviewStepState(lineage.review, reviewOutcome).assessedNothing;
	const extras = [
		earlier.length > 0 ? `${earlier.length} answered by an earlier review` : undefined,
		marked > 0 ? `${marked} marked incorrect` : undefined,
	].filter(hasText);
	const inTable = new Set(rows.map(({ result }) => result.id));
	const dotted = numbered.filter(({ result }) => !inTable.has(result.id));
	const shownRows =
		listed && !othersOpen && rows.length > RESULTS_OPEN ? rows.slice(0, RESULTS_OPEN) : rows;
	const hiddenRows = rows.length - shownRows.length;
	const folded = path !== undefined || self !== undefined;
	let toggleText = `Show all ${countOf(numbered.length, "result", "results")} in the table`;
	if (listed) {
		toggleText = folded ? "Show only the results on this path" : "Fold the table";
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
				tone: empty ? "outline" : worstTone(lineage.results),
				hollow: empty,
				here: here?.kind === "result" || here?.kind === "results",
			}}
			at={at}
			gapFrom={at === undefined ? undefined : previous}
			data-step="results"
		>
			<StoryNode
				node={{ kind: "results" }}
				className="px-1.5 py-1"
				after={
					<>
						{shownRows.length > 0 && (
							<TraceTable
								kind="results"
								times={display.times}
								label={listed ? "Results of this review" : "Results on this path"}
							>
								{shownRows.map((entry) => (
									<ResultRow
										key={entry.result.id}
										entry={entry}
										lineage={lineage}
										letters={letters}
										muted={offPath(traced, { kind: "result", id: entry.result.id })}
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
								{`Show ${countOf(hiddenRows, "more result", "more results")}`}
							</Button>
						)}
						{(dotted.length > 0 || toggle !== false) && (
							<div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 px-1">
								{dotted.length > 0 && (
									<ul
										aria-label={
											shownRows.length === 0
												? "Results by number"
												: `${countOf(dotted.length, "other result", "other results")} of this review`
										}
										className="flex flex-wrap items-center gap-0.5"
									>
										{shownRows.length > 0 && (
											<li aria-hidden className="mr-0.5 text-muted-foreground">
												{`${countOf(dotted.length, "other", "others")}:`}
											</li>
										)}
										{dotted.map(({ result, number }) => (
											<li key={result.id}>
												<ResultDot
													result={result}
													number={number}
													disputed={disputedFrom(result.id, lineage)}
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
				<ResultsHead full={full} filter={filter} total={total} nothing={nothing} extras={extras} />
			</StoryNode>
		</Step>
	);
}

/**
 * What the result step says: what the review recorded, from the whole chain, so a filter
 * never makes it say the review recorded less; how much of it the filter shows; or why there is none.
 */
function ResultsHead({
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
	const wholeTotal = full.results.length + full.moreResults;
	const filtered = filter.kind !== "all";
	if (wholeTotal === 0) {
		return (
			<>
				<span className="block font-medium">No results</span>
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

function worstTone(results: readonly ReviewLineageResult[]): BadgeVariant {
	for (const outcome of ["NOT_MET", "MET", "UNDETERMINED", "NOT_APPLICABLE"] as const) {
		if (results.some((entry) => entry.outcome === outcome)) {
			return OUTCOME_DEFS[outcome].badgeVariant;
		}
	}
	return "outline";
}

/**
 * Whether the developer disputes a piece of feedback written from the result, as the
 * Results list's "Developer response" filter reads it. Only that they dispute it: their rating
 * and notes are never part of the chain.
 */
function disputedFrom(practiceResultId: string, lineage: ReviewLineage): boolean {
	return lineage.feedback.some(
		(feedback) =>
			feedback.practiceResultIds.includes(practiceResultId) && feedback.response?.disputed === true,
	);
}

/** One result as its dot, which brings it into view and opens its sheet. */
function ResultDot({
	result,
	number,
	disputed,
}: {
	result: ReviewLineageResult;
	number: number;
	/** Whether the developer disputes feedback written from it. */
	disputed: boolean;
}) {
	const context = useStory();
	const node: TraceNode = { kind: "result", id: result.id };
	const chip = (
		<LineageResultChip
			result={result}
			number={number}
			withSummary
			ringed={sameTraceNode(context.here, node)}
		/>
	);
	const { open } = context;
	// The outcome in words too, so a dot is never told by its colour alone.
	const facts = [
		OUTCOME_DEFS[result.outcome].label,
		result.severity && SEVERITY_DEFS[result.severity].label.toLowerCase(),
		disputed ? "disputed" : undefined,
		result.invalidated ? "marked incorrect" : undefined,
	].filter(hasText);
	const title = `${number} · ${facts.join(", ")}: ${result.summary}`;
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
				open({ kind: "result", id: result.id });
			}}
			className="flex size-6 items-center justify-center rounded-full outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
		>
			{chip}
		</button>
	);
}

/** The letters of the pieces of feedback written from a result, in the order they were written. */
function fedLetters(
	practiceResultId: string,
	lineage: ReviewLineage,
	letters: ReadonlyMap<string, string>,
): string[] {
	return lineage.feedback
		.filter((feedback) => feedback.practiceResultIds.includes(practiceResultId))
		.map((feedback) => letters.get(feedback.id))
		.filter(hasText)
		.sort((a, b) => a.length - b.length || a.localeCompare(b));
}

/**
 * One result as a row of the review stage's table: its number in its verdict's colour, its
 * practice and summary, the outcome, the severity, the pieces of feedback written from it by letter,
 * and when it was observed. A correction, a dispute or an earlier review's answer stands under the
 * summary.
 */
function ResultRow({
	entry,
	lineage,
	letters,
	muted,
}: {
	entry: NumberedResult;
	lineage: ReviewLineage;
	letters: ReadonlyMap<string, string>;
	muted: boolean;
}) {
	const { display } = useStory();
	const { result, number } = entry;
	const outcome = OUTCOME_DEFS[result.outcome];
	const severity = result.severity && SEVERITY_DEFS[result.severity];
	const earlier = answeredEarlier(result, lineage.review);
	const disputed = disputedFrom(result.id, lineage);
	const fed = fedLetters(result.id, lineage, letters);
	const flagged = result.invalidated || disputed || earlier;
	return (
		<li data-muted={muted} className={cn(muted && "text-muted-foreground")}>
			<OffPathNote muted={muted} />
			<StoryNode
				row
				node={{ kind: "result", id: result.id }}
				className={cn(traceRowGrid("results", display.times), "px-1.5 py-1.5")}
			>
				<LineageResultChip
					result={result}
					number={number}
					ringed={false}
					connector="source"
					className="mt-px"
				/>
				<span className={TRACE_ROW_CELLS}>
					<span className={TRACE_ROW_MAIN}>
						<span className="block font-medium break-words">{result.practiceName}</span>
						<span
							className={cn(
								"block break-words text-muted-foreground",
								result.invalidated && "line-through",
							)}
						>
							{result.summary}
						</span>
						{flagged && (
							<span className="flex flex-wrap items-center gap-x-2 text-muted-foreground">
								{result.invalidated && (
									<span className="inline-flex flex-wrap items-center gap-x-1 font-medium text-foreground">
										<ToneIcon def={MARKED_INCORRECT_DEF} className="size-3" />
										{MARKED_INCORRECT_DEF.label}
										{result.markedIncorrectAt && (
											<LabeledRailTime
												label="at"
												at={result.markedIncorrectAt}
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
							<CellLabel>Recorded</CellLabel>
							<StepTime at={result.observedAt} />
						</span>
					)}
				</span>
			</StoryNode>
		</li>
	);
}

/** "Recorded 3:37 pm": a time inside a step with what it is the time of, so two never read alike. */
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
