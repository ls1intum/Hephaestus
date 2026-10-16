import { ChevronRightIcon } from "lucide-react";

import { cn } from "cn";
import type { ProblemSummary, ReviewLineage } from "@/api/types.gen";
import type { BadgeVariant } from "@/components/common/status-def";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { Button } from "@/components/ui/button";
import { hasText } from "@/lib/text";

import {
	countOf,
	feedbackOf,
	type LineageRecords,
	type LineageResult,
	lineageRecords,
	type NumberedResult,
	verdictSummary,
} from "./lineage-chain";
import { answeredEarlier, reviewStepState } from "./lineage-story";
import { LineageResultChip } from "./LineageResultChip";
import { OffPathNote, Step, StepTime, ToneIcon } from "./LineageStep";
import { resultSummary } from "./result-words";
import { chainReview, sameTraceNode, type TraceNode } from "./review-trace";
import { hiddenByFilter, type TraceFilter } from "./trace-filter";
import { type RailStepProps, StoryNode, offPath, useStory } from "./TraceStoryNode";
import { CellLabel, TRACE_ROW_CELLS, TRACE_ROW_MAIN, TraceTable, traceRowGrid } from "./TraceTable";

const RESULTS_OPEN = 12;

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
	othersOpen,
	onOthersOpenChange,
}: RailStepProps & { at: Date | undefined }) {
	const { display, review: detail } = useStory();
	const records = lineageRecords(lineage);
	const review = chainReview(records);
	const total = records.results.length;
	const earlier = numbered.filter(({ result }) => answeredEarlier(result, review));
	const empty = total === 0;
	const nothing =
		review === undefined
			? undefined
			: reviewStepState(review, detail?.id === review.id ? detail : undefined).assessedNothing;
	const extras = earlier.length > 0 ? [`${earlier.length} answered by an earlier review`] : [];
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
				tone: empty ? "outline" : worstTone(records.results),
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
										records={records}
										review={review?.id}
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
												<ResultDot result={result} number={number} />
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
	const whole = lineageRecords(full).results;
	const wholeTotal = whole.length;
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
			<span className="block font-medium">{`It recorded ${verdictSummary(whole)}.`}</span>
			{shown !== undefined && <span className="block text-muted-foreground">{shown}</span>}
			{total > 0 && extras.length > 0 && (
				<span className="block text-muted-foreground">{extras.join(" · ")}</span>
			)}
		</>
	);
}

function worstTone(results: readonly LineageResult[]): BadgeVariant {
	for (const outcome of ["NOT_MET", "MET", "COULD_NOT_DETERMINE", "DOES_NOT_APPLY"] as const) {
		if (results.some((entry) => entry.outcome === outcome)) {
			return OUTCOME_DEFS[outcome].badgeVariant;
		}
	}
	return "outline";
}

function ResultDot({ result, number }: { result: LineageResult; number: number }) {
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
	// Dots need outcome words so color is not their only distinction.
	const facts = [
		OUTCOME_DEFS[result.outcome].label,
		result.severity === null ? undefined : SEVERITY_DEFS[result.severity].label.toLowerCase(),
	].filter(hasText);
	const title = `${number} · ${facts.join(", ")}: ${resultSummary(result)}`;
	if (open === undefined) {
		return (
			<span title={title} className="flex size-6 items-center justify-center rounded-full">
				{chip}
			</span>
		);
	}
	return (
		// Keep the pointer target at least 24 px (WCAG 2.5.8), even when the chip is smaller.
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

function fedLetters(
	resultId: string,
	records: LineageRecords,
	letters: ReadonlyMap<string, string>,
): string[] {
	return feedbackOf(records.feedback, resultId)
		.map((feedback) => letters.get(feedback.id))
		.filter(hasText)
		.sort((a, b) => a.length - b.length || a.localeCompare(b));
}

function ResultRow({
	entry,
	records,
	review,
	letters,
	muted,
}: {
	entry: NumberedResult;
	records: LineageRecords;
	review: string | undefined;
	letters: ReadonlyMap<string, string>;
	muted: boolean;
}) {
	const { display } = useStory();
	const { result, number } = entry;
	const outcome = OUTCOME_DEFS[result.outcome];
	const severity = result.severity === null ? undefined : SEVERITY_DEFS[result.severity];
	const earlier = review !== undefined && result.reviewId !== review;
	const fed = fedLetters(result.id, records, letters);
	return (
		<li data-muted={muted} className={cn(muted && "text-muted-foreground")}>
			<OffPathNote muted={muted} />
			<StoryNode
				row
				node={{ kind: "result", id: result.id }}
				className={cn(traceRowGrid("results", display.times), "px-1.5 py-1.5")}
				after={
					result.problems.length > 0 && <TraceProblems problems={result.problems} number={number} />
				}
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
						<span className="block break-words text-muted-foreground">{resultSummary(result)}</span>
						{earlier && (
							<span className="block text-muted-foreground">Answered by an earlier review</span>
						)}
					</span>
					<span className="inline-flex items-center gap-1">
						<ToneIcon def={outcome} className="size-3" />
						{outcome.label}
					</span>
					{severity === undefined ? (
						<span aria-hidden className="hidden @lg:block" />
					) : (
						<span className="inline-flex items-center gap-1">
							<ToneIcon def={severity} className="size-3" />
							{severity.label}
						</span>
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
							<StepTime at={result.recordedAt} />
						</span>
					)}
				</span>
			</StoryNode>
		</li>
	);
}

/**
 * The problems a Not met result records, in recorded order, each with its severity. The row above
 * opens the result; each problem opens its own sheet over the trace.
 */
function TraceProblems({
	problems,
	number,
}: {
	problems: readonly ProblemSummary[];
	number: number;
}) {
	const { open } = useStory();
	return (
		<ul
			aria-label={`Problems of result ${String(number)}`}
			className="flex min-w-0 flex-col gap-0.5 pb-1.5 pl-9 text-xs"
		>
			{problems.map((problem, index) => {
				const severity = SEVERITY_DEFS[problem.severity];
				// Results can share a summary, so each name says which result and which of its problems.
				const name = `Problem ${String(index + 1)} of result ${String(number)}: ${severity.label}, ${problem.summary}`;
				const words = (
					<>
						<ToneIcon def={severity} className="size-3 shrink-0" />
						<span className="shrink-0">{severity.label}</span>
						<span className="min-w-0 break-words text-muted-foreground">{problem.summary}</span>
					</>
				);
				return (
					<li key={problem.id} className="min-w-0">
						{open === undefined ? (
							<span className="flex min-w-0 items-baseline gap-1.5 px-1 py-0.5">{words}</span>
						) : (
							<button
								type="button"
								title="Open its sheet"
								aria-label={name}
								onClick={() => {
									open({ kind: "problem", id: problem.id });
								}}
								className="flex w-full min-w-0 items-baseline gap-1.5 rounded-sm px-1 py-0.5 text-left outline-none hover:bg-muted/60 focus-visible:ring-2 focus-visible:ring-ring"
							>
								{words}
							</button>
						)}
					</li>
				);
			})}
		</ul>
	);
}
