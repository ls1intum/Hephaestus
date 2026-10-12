import { ChevronsDownUpIcon, ChevronsUpDownIcon } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";

import type { AgentJob, ReviewLineage } from "@/api/types.gen";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { useNow } from "@/components/common/use-now";
import type { ResultOrigin } from "@/components/practice-vocabulary/result-origin-defs";
import { Button } from "@/components/ui/button";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatWeekdayDay } from "@/lib/dates";

import { TermSentence } from "./GlossaryTerm";
import type { NumberedById, NumberedResult } from "./lineage-chain";
import {
	feedbackLetters,
	type RailItem,
	sourcesOf,
	type StorySiblings,
	storyFeedback,
	storyResults,
	storyRail,
} from "./lineage-story";
import { connectorLanes, laneGutter, LineageConnectors } from "./LineageConnectors";
import { LineageDay, LineageTimes } from "./LineageStep";
import { type RecordRef, workRecord } from "./review-layers";
import { type TraceNode, tracedBy, traceStepKey } from "./review-trace";
import { mergedAtOf, traceBottleneck, traceSpans } from "./trace-bottleneck";
import {
	DEFAULT_TRACE_DISPLAY,
	TRACE_DISPLAY_PARTS,
	type TraceDisplay,
	type TraceDisplayPart,
} from "./trace-display";
import {
	ALL_STEPS,
	filteredLineage,
	parseTraceFilter,
	type TraceFilter,
	traceFilterValue,
	tracePractices,
} from "./trace-filter";
import { traceVerdict } from "./trace-verdict";
import { TraceBottleneckLine } from "./TraceBottleneckLine";
import { TraceLegend } from "./TraceLegend";
import { StageSteps } from "./TraceRailSteps";
import { resultRows } from "./TraceResultTable";
import { traceStepFacts } from "./TraceStepFacts";
import { type StoryContext, StoryNodes } from "./TraceStoryNode";

export interface ReviewLineageGraphProps {
	/** The chain the trace tells, from `getPracticeReviewLineage` with `whole`. */
	lineage: PanelState<{ lineage: ReviewLineage }>;
	/**
	 * The record the trace was opened for, marked "You started here" and ringed: its path is drawn
	 * strong. Opening another record over the trace leaves the mark where it is. Absent, nothing is
	 * marked, as in the state matrix.
	 */
	here?: TraceNode;
	/**
	 * Opens a step's record over the trace: a result, a piece of feedback, the review, or the
	 * work. Each record opens from one step; a moment, the count of results and the end open
	 * nothing. Absent, the steps are text.
	 */
	onOpen?: (record: RecordRef) => void;
	/**
	 * What the trace shows on top of its steps, for the whole trace at once: times, system info,
	 * siblings, connections. Uncontrolled from `defaultDisplay` when absent.
	 */
	display?: TraceDisplay;
	defaultDisplay?: TraceDisplay;
	onDisplayChange?: (display: TraceDisplay) => void;
	/** What of the chain is shown. Uncontrolled when absent. */
	filter?: TraceFilter;
	onFilterChange?: (filter: TraceFilter) => void;
	/** Why a completed review assessed nothing, from the review itself. */
	reviewOutcome?: AgentJob["reviewOutcome"];
	/**
	 * The review itself, as its sheet reads it. Where given, the review's times, model and outcome
	 * and the time its feedback was first read come from it, so the trace and the sheet say the same.
	 */
	job?: AgentJob;
	/**
	 * How the review came to run, from its row: live, by hand or backfilled. Every result of
	 * the review shares it, so the review's step says it once.
	 */
	origin?: ResultOrigin;
	/**
	 * Where the review stands among the reviews of its work, from its row: "Review 3 of 7 on this
	 * work". Re-reviews of the same work are common, so the review's step says which one it is.
	 */
	position?: { index: number; total: number };
}

/**
 * The trace of a record: its chain as one story read top to bottom in the order things happened —
 * the work and the moment that started the review, the review, its results as numbered verdict
 * dots or as a table of their own, the feedback written from them as a table with where each piece
 * went, how the developer answered and the results it came from, the merge wherever it fell,
 * and where the path ends. A sentence on top says what became of it.
 * Each step has its time and the gap since the step before; a long gap is coloured and its rail
 * dashed.
 *
 * The record the trace was opened for is "You started here", ringed. It tells the path of the record
 * and folds its siblings into one line. The "Show" control adds, each with one click and for the
 * whole trace: the times and gaps, the system info (states, model, every fact of a piece, the facts
 * under the record you started from), the siblings, and connectors from each result to the
 * feedback written from it, which the tables' From and Feedback columns also say in words. "Expand
 * all" opens every folded part at once, the results' table among them; that table also opens
 * and folds on its own button, whatever the Show control says. A click on a step opens
 * its record's sheet over the trace; the mark stays where the reader started. A filter narrows the
 * chain to its feedback, its losses or one practice.
 */
export function ReviewLineageGraph({ lineage: state, ...props }: ReviewLineageGraphProps) {
	if (state.status === "loading") {
		return <LineageSkeleton />;
	}
	if (state.status === "error") {
		return (
			<QueryErrorAlert
				error={state.error}
				title="We could not load this trace"
				onRetry={state.onRetry}
			/>
		);
	}
	return (
		<Story
			{...props}
			lineage={withJob(state.lineage, props.job)}
			// The lineage names the outcome itself, so the trace does not wait on the review's own read.
			reviewOutcome={
				props.job?.reviewOutcome ?? state.lineage.review?.outcome ?? props.reviewOutcome
			}
		/>
	);
}

/**
 * The chain with its review's facts read from the review itself, where the trace has it: one
 * source for the times and the model the review's sheet shows.
 */
function withJob(lineage: ReviewLineage, job: AgentJob | undefined): ReviewLineage {
	const { review } = lineage;
	const own = job !== undefined && review !== undefined && review.id === job.id ? job : undefined;
	// The chain reads the merge from the pull request, which can miss it where the review or the
	// merge moment on the work has it.
	const workMergedAt = mergedAtOf(lineage, own);
	if (own === undefined || review === undefined) {
		return workMergedAt === lineage.workMergedAt ? lineage : { ...lineage, workMergedAt };
	}
	return {
		...lineage,
		workMergedAt,
		review: {
			...review,
			createdAt: own.createdAt,
			startedAt: own.startedAt,
			finishedAt: own.completedAt,
			model: own.model ?? review.model,
		},
	};
}

// ------------------------------------------------------------------------------------------------
// The story
// ------------------------------------------------------------------------------------------------

type StoryProps = Omit<ReviewLineageGraphProps, "lineage"> & { lineage: ReviewLineage };

function Story({
	lineage: full,
	here,
	onOpen,
	display: displayProp,
	defaultDisplay = DEFAULT_TRACE_DISPLAY,
	onDisplayChange,
	filter: filterProp,
	onFilterChange,
	reviewOutcome,
	job,
	origin,
	position,
}: StoryProps) {
	const now = new Date(useNow());
	const [ownDisplay, setOwnDisplay] = useState<TraceDisplay>(defaultDisplay);
	const [ownFilter, setOwnFilter] = useState<TraceFilter>(ALL_STEPS);
	const filter = filterProp ?? ownFilter;
	const setFilter = (next: TraceFilter) => {
		setOwnFilter(next);
		onFilterChange?.(next);
	};
	const filtered = filter.kind !== "all";
	const display = displayProp ?? ownDisplay;
	const setDisplay = (next: TraceDisplay) => {
		setOwnDisplay(next);
		onDisplayChange?.(next);
	};
	const hereKey = here === undefined ? undefined : traceStepKey(here);
	// The folded parts, opened one by one or all at once; none of them is a reading preference.
	const [beforeOpen, setBeforeOpen] = useState(false);
	const [othersOpen, setOthersOpen] = useState(false);
	const [siblingsOpen, setSiblingsOpen] = useState(false);
	const [factsOpen, setFactsOpen] = useState(false);
	const [legendOpen, setLegendOpen] = useState(false);
	// The results' table, opened or folded by hand; undefined follows what the reader shows.
	const [tableOpen, setTableOpen] = useState<boolean | undefined>(undefined);
	const setAllOpen = (open: boolean) => {
		setBeforeOpen(open);
		setOthersOpen(open);
		setSiblingsOpen(open);
		setFactsOpen(open);
		setTableOpen(open ? true : undefined);
	};
	const box = useRef<HTMLDivElement>(null);
	const rail = useRef<HTMLDivElement>(null);
	useReveal(rail, hereKey);

	const lineage = filteredLineage(full, filter);
	const { numbered, self } = storyResults(lineage, here, full);
	// A filter lists every step it leaves, and where the path folds away the record the reader
	// started from — a sibling, a result drawn only as its dot — it is listed too.
	const siblingsShown =
		display.siblings || siblingsOpen || filtered || foldedOffPath(lineage, here, numbered, self);
	const byId: NumberedById = new Map(numbered.map((entry) => [entry.result.id, entry]));
	const letters = feedbackLetters(full.feedback);
	const told = storyFeedback(lineage, here, siblingsShown);
	const path = siblingsShown ? undefined : pathResults(lineage, here, numbered, self);
	// Every result is a row of the table where the reader opened it, where the siblings show, or
	// where connectors need an end to start from. Folded, the table holds the path's own rows.
	const listed = tableOpen ?? (siblingsShown || display.connections);
	const expanded = beforeOpen && othersOpen && siblingsOpen && factsOpen && listed;
	const siblings: StorySiblings | undefined =
		path === undefined
			? undefined
			: {
					results: numbered.length + lineage.moreResults - path.length,
					feedback: told.siblings.length + lineage.moreFeedback,
				};
	const stages = storyRail(lineage, told.shown, siblings);
	const verdict = traceVerdict(full, here, reviewOutcome);
	const { ownJob, bottleneck, spans } = pathTimes(full, here, job, now);
	const traced = tracedBy(lineage, here);
	const rows = resultRows(numbered, path, self, listed);
	const lanes = display.connections ? connectorLanes(rows, told.shown) : [];
	const hasStart = stages[0]?.items.some((item) => item.kind === "moment") ?? false;
	const showBefore = beforeOpen;
	const practices = tracePractices(full);

	const work = full.work && workRecord(full.work.kind, full.work.id);
	const recordOf = (node: TraceNode): RecordRef | undefined => {
		switch (node.kind) {
			// One step opens each record, once: a moment is part of the work, the count of results
			// part of the review, and the end belongs to no record.
			case "work": {
				return work;
			}
			case "review": {
				return full.review === undefined ? undefined : { kind: "review", id: full.review.id };
			}
			case "result":
			case "feedback": {
				return { kind: node.kind, id: node.id };
			}
			case "moment":
			case "results":
			case "end": {
				return undefined;
			}
		}
	};

	const context: StoryContext = {
		here,
		recordOf,
		open: onOpen,
		facts: (node) =>
			traceStepFacts({
				lineage: full,
				node,
				numbered,
				letters,
				here,
				job: ownJob,
				everyStep: factsOpen,
			}),
		display,
		factsOpen,
		firstReadAt: firstReadOf(job, full),
		resultProcessing: resultProcessingOf(job, full),
		origin,
		job: ownJob,
		position,
		bottleneck,
	};

	const day = lineage.review?.createdAt ?? numbered[0]?.result.observedAt;
	// A step's gap is from the step drawn above it with a time, and never runs backwards: the stages
	// put the merge among the feedback even where it fell before a piece.
	const drawn = stages.flatMap(({ items }) =>
		items.flatMap((item) => {
			if (item.kind !== "before") {
				return [item];
			}
			return showBefore ? item.items : [];
		}),
	);
	const endAt = verdict.end.at ?? verdict.end.since;
	const previousAt = (item: RailItem) => {
		const index = drawn.indexOf(item);
		const at = item.kind === "end" ? endAt : item.at;
		let earlier: Date | undefined;
		for (const entry of drawn.slice(0, Math.max(index, 0))) {
			// A review whose record is gone keeps its place on the rail, but has no time to count from.
			const gone = entry.kind === "review" && full.review === undefined;
			earlier = (gone ? undefined : entry.at) ?? earlier;
		}
		return at !== undefined && earlier !== undefined && earlier.getTime() <= at.getTime()
			? earlier
			: undefined;
	};

	return (
		<StoryNodes value={context}>
			<LineageDay value={day}>
				<LineageTimes value={display.times}>
					<div
						ref={box}
						role="group"
						aria-label="How this came about"
						className="min-w-0 space-y-3"
					>
						<p data-trace-verdict className="text-sm font-medium text-balance">
							<TermSentence text={verdict.sentence} />
						</p>
						<TraceBottleneckLine
							bottleneck={bottleneck}
							spans={display.times ? spans : []}
							verdict={verdict.sentence}
						/>
						<div data-trace-toolbar className="flex flex-wrap items-center gap-2">
							<TraceFilterSelect value={filter} practices={practices} onChange={setFilter} />
							<TraceDisplayControl
								display={display}
								onChange={setDisplay}
								siblingsListed={filtered}
							/>
							<Button
								variant="ghost"
								size="sm"
								aria-expanded={expanded}
								title={
									expanded
										? "Fold what came before, the results, the siblings and the facts"
										: "Open what came before, every result, the siblings and every step’s facts"
								}
								onClick={() => setAllOpen(!expanded)}
							>
								{expanded ? (
									<ChevronsDownUpIcon aria-hidden data-icon="inline-start" />
								) : (
									<ChevronsUpDownIcon aria-hidden data-icon="inline-start" />
								)}
								{expanded ? "Collapse all" : "Expand all"}
							</Button>
							<TraceLegend open={legendOpen} onOpenChange={setLegendOpen} />
						</div>
						<TraceDay day={display.times ? day : undefined} />
						<div ref={rail} className="relative">
							{lanes.length > 0 && (
								<LineageConnectors
									// Drawn again whenever what the rail lists changes; a resize it measures itself.
									key={[
										showBefore,
										othersOpen,
										siblingsShown,
										listed,
										factsOpen,
										display.times,
										display.system,
										traceFilterValue(filter),
									].join(":")}
									container={rail}
									lanes={lanes}
									numbered={numbered}
									traced={traced}
								/>
							)}
							<ol
								aria-label="Steps, oldest first"
								className="text-xs"
								style={{
									"--lane-gutter": laneGutter(lanes),
									// With connectors, every step's words end at one edge, where the lanes start.
									"--time-column": lanes.length > 0 && display.times ? "6.5rem" : undefined,
								}}
							>
								{stages.map((stage, index) => (
									<StageSteps
										key={stage.stage}
										stage={stage}
										first={index === 0}
										previousAt={previousAt}
										showBefore={showBefore}
										onShowBefore={() => setBeforeOpen(true)}
										end={verdict.end}
										lineage={lineage}
										full={full}
										filter={filter}
										numbered={numbered}
										self={self}
										path={path}
										listed={listed}
										rows={rows}
										onListedChange={setTableOpen}
										byId={byId}
										letters={letters}
										traced={traced}
										here={here}
										hasStart={hasStart}
										reviewOutcome={reviewOutcome}
										othersOpen={othersOpen}
										onOthersOpenChange={setOthersOpen}
										onShowSiblings={() => setSiblingsOpen(true)}
									/>
								))}
							</ol>
						</div>
					</div>
				</LineageTimes>
			</LineageDay>
		</StoryNodes>
	);
}

/** The review's own record where it is the trace's review, and where the path was lost and its time went. */
function pathTimes(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	job: AgentJob | undefined,
	now: Date,
) {
	const ownJob = job !== undefined && job.id === lineage.review?.id ? job : undefined;
	return {
		ownJob,
		bottleneck: traceBottleneck(lineage, here, ownJob, now),
		spans: traceSpans(lineage, here, ownJob),
	};
}

/** How the trace's review processed its results, as the review itself says. */
function resultProcessingOf(
	job: AgentJob | undefined,
	lineage: ReviewLineage,
): AgentJob["deliveryStatus"] {
	return job !== undefined && job.id === lineage.review?.id ? job.deliveryStatus : undefined;
}

/** When a developer first read feedback of the trace's review, as the review itself says. */
function firstReadOf(job: AgentJob | undefined, lineage: ReviewLineage): Date | undefined {
	return job !== undefined && job.id === lineage.review?.id ? job.firstFeedbackReadAt : undefined;
}

/** Above the rail: the day the times are on, while the times are shown. */
function TraceDay({ day }: { day: Date | undefined }) {
	if (day === undefined) {
		return null;
	}
	return (
		<div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
			<p className="ml-auto font-medium text-muted-foreground">
				<DayLabel day={day} />
			</p>
		</div>
	);
}

/**
 * What the trace shows on top of its steps, each part a toggle of its own for the whole trace. A
 * filter lists every step it keeps, so the siblings show while one is on.
 */
function TraceDisplayControl({
	display,
	onChange,
	siblingsListed,
}: {
	display: TraceDisplay;
	onChange: (display: TraceDisplay) => void;
	siblingsListed: boolean;
}) {
	const labelId = useId();
	const value = TRACE_DISPLAY_PARTS.filter(({ part }) => display[part]).map(({ part }) => part);
	return (
		<div className="flex items-center gap-1.5">
			<span id={labelId} className="text-xs font-medium text-muted-foreground">
				Show
			</span>
			<ToggleGroup
				multiple
				variant="outline"
				size="sm"
				value={value}
				onValueChange={(next: TraceDisplayPart[]) => {
					onChange({
						times: next.includes("times"),
						system: next.includes("system"),
						siblings: next.includes("siblings"),
						connections: next.includes("connections"),
					});
				}}
				aria-labelledby={labelId}
			>
				{TRACE_DISPLAY_PARTS.map(({ part, label, hint }) => (
					<ToggleGroupItem
						key={part}
						value={part}
						title={
							part === "siblings" && siblingsListed ? "A filter lists every step it keeps" : hint
						}
						disabled={part === "siblings" && siblingsListed}
					>
						{label}
					</ToggleGroupItem>
				))}
			</ToggleGroup>
		</div>
	);
}

/** Whether the path alone draws the step only folded: a result as a dot or a sibling, a sibling piece. */
function foldedOffPath(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	numbered: NumberedResult[],
	self: NumberedResult | undefined,
): boolean {
	const node = here;
	if (node?.kind === "result") {
		const path = pathResults(lineage, here, numbered, self) ?? [];
		return !path.some((entry) => entry.result.id === node.id);
	}
	if (node?.kind === "feedback") {
		return !storyFeedback(lineage, here, false).shown.some((entry) => entry.id === node.id);
	}
	return false;
}

/** Every step, only the feedback, only what was lost, or one practice's results and feedback. */
function TraceFilterSelect({
	value,
	practices,
	onChange,
}: {
	value: TraceFilter;
	practices: readonly { slug: string; name: string }[];
	onChange: (filter: TraceFilter) => void;
}) {
	const labelId = useId();
	const items = [
		{ value: "all", label: "Every step" },
		{ value: "feedback", label: "Only feedback" },
		{ value: "losses", label: "Only what was lost" },
		...practices.map((practice) => ({
			value: traceFilterValue({ kind: "practice", practiceSlug: practice.slug }),
			label: `Only ${practice.name}`,
		})),
	];
	return (
		<Select
			items={items}
			value={traceFilterValue(value)}
			onValueChange={(next: string | null) => {
				if (next !== null) {
					onChange(parseTraceFilter(next));
				}
			}}
		>
			<span id={labelId} className="sr-only">
				Steps
			</span>
			<SelectTrigger size="sm" aria-labelledby={labelId} className="w-48 max-w-full">
				<SelectValue />
			</SelectTrigger>
			<SelectContent aria-labelledby={labelId}>
				{items.map((item) => (
					<SelectItem key={item.value} value={item.value}>
						{item.label}
					</SelectItem>
				))}
			</SelectContent>
		</Select>
	);
}

function DayLabel({ day }: { day: Date }) {
	const now = useNow();
	return <time dateTime={day.toISOString()}>{formatWeekdayDay(day, new Date(now))}</time>;
}

/**
 * Brings the record the reader started from into the viewport and focuses its control, once, when
 * the trace arrives on it. Opening another record over the trace moves neither.
 */
function useReveal(rail: React.RefObject<HTMLElement | null>, step: string | undefined) {
	const done = useRef<string>(undefined);
	useEffect(() => {
		if (step === undefined || done.current === step) {
			return;
		}
		const element = rail.current?.querySelector<HTMLElement>(
			`[data-step-key="${CSS.escape(step)}"]`,
		);
		if (element === undefined || element === null) {
			return;
		}
		done.current = step;
		element.scrollIntoView({ block: "nearest" });
		element.querySelector<HTMLElement>("[data-step-control]")?.focus({ preventScroll: true });
	});
}

/**
 * The record the story stands on, and the results on its path where the simple story tells
 * only those: the result itself, or the results a piece of feedback was written from.
 * Undefined where the path is the whole review.
 */
function pathResults(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	numbered: readonly NumberedResult[],
	self: NumberedResult | undefined,
): NumberedResult[] | undefined {
	if (here?.kind === "result") {
		return self === undefined ? undefined : [self];
	}
	if (here?.kind === "feedback") {
		const feedback = lineage.feedback.find((entry) => entry.id === here.id);
		return feedback === undefined ? undefined : sourcesOf(feedback, numbered);
	}
	return undefined;
}

/** The story's steps, in the shape they land in. */
function LineageSkeleton() {
	return (
		<div aria-hidden className="space-y-3">
			<Skeleton className="h-3 w-24" />
			{[1, 1, 2, 2].map((lines, index) => (
				<div key={index} className="flex gap-2">
					<Skeleton className="size-[11px] shrink-0 rounded-full" />
					<div className="flex-1 space-y-1.5">
						<Skeleton className="h-3 w-3/4" />
						{lines > 1 && <Skeleton className="h-10 w-full" />}
					</div>
				</div>
			))}
		</div>
	);
}
