import { ChevronRightIcon, RepeatIcon } from "lucide-react";
import { Fragment, type ReactNode } from "react";

import { cn } from "cn";
import type { AgentJob, ReviewLineage } from "@/api/types.gen";
import type { BadgeVariant } from "@/components/common/status-def";
import { RESULT_ORIGIN_DEFS } from "@/components/practice-vocabulary/result-origin-defs";
import { RESULT_PROCESSING_DEFS } from "@/components/practice-vocabulary/review-status-defs";
import { REVIEW_TRIGGER_DEFS } from "@/components/practice-vocabulary/review-trigger-defs";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { hasText } from "@/lib/text";

import { GlossaryTerm, TermSentence } from "./GlossaryTerm";
import { countOf, stageCounts } from "./lineage-chain";
import {
	type RailItem,
	type RailStage,
	reviewStepState,
	type StorySiblings,
} from "./lineage-story";
import { MergeIcon, StageIcon, Step, StepTag, StepTime, ToneIcon } from "./LineageStep";
import { mergeNote, momentNode } from "./review-trace";
import { ReviewArtifactLabel } from "./ReviewArtifact";
import { type TraceBottleneck, traceSpans } from "./trace-bottleneck";
import { hiddenByFilter, type TraceFilter } from "./trace-filter";
import { STAGE_HEADERS, STAGE_MARKS, type StageKey } from "./trace-stage-headers";
import { TRACE_END_TONES, type TraceEnd } from "./trace-verdict";
import { FeedbackTableStep } from "./TraceFeedbackTable";
import { ResultsStep } from "./TraceResultTable";
import { type RailStepProps, type StepProps, StoryNode, useStory } from "./TraceStoryNode";

function WorkStep({ lineage, here }: { lineage: ReviewLineage; here: boolean }) {
	const { work } = lineage;
	return (
		<Step dot={{ tone: "outline", hollow: true, here }} data-step="work">
			<StoryNode node={{ kind: "work" }} className="px-1.5 py-1">
				<ReviewArtifactLabel reviewedWork={work} className="font-medium" />
				{hasText(work?.title) && (
					<span className="block break-words text-muted-foreground">{work.title}</span>
				)}
			</StoryNode>
		</Step>
	);
}

type FeedbackItem = Extract<RailItem, { kind: "feedback" }>;

/** Where the bottleneck line's stage falls on the rail: a review's own losses sit with its results. */
function bottleneckHeader(
	stage: TraceBottleneck["stage"],
	kind: TraceBottleneck["kind"],
): StageKey {
	return stage === "review" && kind === "lost" ? "results" : stage;
}

/**
 * A stage's header on the rail: its icon on the rail itself, its name, a short summary of the stage
 * where one helps the eye (the review's queue and run, the feedback's count), and a mark where the
 * bottleneck line above places what was lost or slow.
 */
function StageHeader({
	stage,
	first,
	summary,
}: {
	stage: StageKey;
	first: boolean;
	summary?: ReactNode;
}) {
	const { bottleneck } = useStory();
	const { label } = STAGE_HEADERS[stage];
	const marked =
		bottleneck !== undefined &&
		bottleneck.kind !== "none" &&
		bottleneckHeader(bottleneck.stage, bottleneck.kind) === stage;
	return (
		<li
			data-stage={stage}
			className={cn(
				"relative grid grid-cols-[0.6875rem_minmax(0,1fr)] items-center gap-x-2.5 pb-2",
				!first && "pt-1",
			)}
		>
			{!first && <span aria-hidden className="absolute inset-y-0 left-[5px] w-px bg-border" />}
			<StageIcon stage={stage} className="relative z-[1] -ml-1" />
			{/* The gutter the connectors run in, as every step's content keeps it. */}
			<span className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-0.5 pr-(--lane-gutter) pl-1.5">
				<span data-stage-label className="text-xs font-semibold text-foreground">
					{label}
				</span>
				{summary}
				{marked && (
					<Badge
						variant={
							bottleneck.tone === "destructive" || bottleneck.tone === "warning"
								? bottleneck.tone
								: "outline"
						}
						size="xs"
					>
						{STAGE_MARKS[bottleneck.kind]}
					</Badge>
				)}
			</span>
		</li>
	);
}

/** A stage's summary next to its name: the review's queue and run, the feedback's count and reach. */
function stageSummary(stage: RailStage["stage"], full: ReviewLineage): ReactNode {
	if (stage === "review") {
		const spans = traceSpans(full, undefined).filter(
			(entry) =>
				entry.kind === "queue" ||
				entry.kind === "preparation" ||
				entry.kind === "execution" ||
				entry.kind === "run",
		);
		if (spans.length === 0) {
			return undefined;
		}
		return (
			<span className="text-2xs text-muted-foreground">
				{spans.map((entry, index) => (
					<Fragment key={entry.kind}>
						{index > 0 && " · "}
						{entry.label}{" "}
						<span
							className={cn(
								"tabular-nums",
								entry.slow ? "font-semibold text-warning" : "text-foreground",
							)}
						>
							{entry.text}
						</span>
					</Fragment>
				))}
			</span>
		);
	}
	if (stage === "feedback") {
		const counts = stageCounts(full);
		if (counts.feedback === 0) {
			return undefined;
		}
		return (
			<span className="text-2xs text-muted-foreground tabular-nums">
				{`${countOf(counts.feedback, "listed piece", "listed pieces")}, ${counts.reached} reached the developer`}
			</span>
		);
	}
	return undefined;
}

/** One stage of the rail under its header: Trigger, Review, Feedback, Effect. */
export function StageSteps({
	stage,
	first,
	previousAt,
	showBefore,
	onShowBefore,
	end,
	...props
}: StepProps & {
	stage: RailStage;
	first: boolean;
	previousAt: (item: RailItem) => Date | undefined;
	showBefore: boolean;
	onShowBefore: () => void;
	end: TraceEnd;
}) {
	const { lineage, full, filter } = props;
	const { firstReadAt } = useStory();
	const feedbackCount = stage.items.filter((item) => item.kind === "feedback").length;
	const siblingCount = stage.items.some((item) => item.kind === "siblings");
	const step = (item: RailItem) => (
		<RailStep key={item.key} {...props} item={item} previous={previousAt(item)} />
	);
	// Pieces written one after another, with nothing on the work between them, share one table.
	const runs: (RailItem | { kind: "table"; key: string; items: FeedbackItem[] })[] = [];
	for (const item of stage.items) {
		const last = runs.at(-1);
		if (item.kind !== "feedback") {
			runs.push(item);
		} else if (last?.kind === "table") {
			last.items.push(item);
		} else {
			runs.push({ kind: "table", key: `table:${item.key}`, items: [item] });
		}
	}
	return (
		<>
			<StageHeader stage={stage.stage} first={first} summary={stageSummary(stage.stage, full)} />
			{stage.stage === "trigger" && (
				<WorkStep lineage={lineage} here={props.here?.kind === "work"} />
			)}
			{runs.map((item) => {
				if (item.kind === "table") {
					return (
						<FeedbackTableStep
							key={item.key}
							{...props}
							items={item.items.map((entry) => entry.feedback)}
							previous={item.items[0] === undefined ? undefined : previousAt(item.items[0])}
						/>
					);
				}
				if (item.kind === "before") {
					return (
						<BeforeSteps
							key={item.key}
							items={item.items}
							open={showBefore}
							onOpen={onShowBefore}
							renderStep={step}
						/>
					);
				}
				if (item.kind === "results") {
					return (
						<Fragment key={item.key}>
							<StageHeader stage="results" first={false} />
							{step(item)}
						</Fragment>
					);
				}
				if (item.kind === "end") {
					return (
						<Fragment key={item.key}>
							{firstReadAt !== undefined && (
								<Step
									dot={{ tone: "outline", hollow: true }}
									at={firstReadAt}
									data-step="first-read"
								>
									<span className="block px-1.5 py-1 text-muted-foreground">
										A developer first read feedback of this review.
									</span>
								</Step>
							)}
							<EndStep end={end} previous={previousAt(item)} />
						</Fragment>
					);
				}
				return step(item);
			})}
			{stage.stage === "feedback" && feedbackCount === 0 && !siblingCount && (
				<Step dot={{ tone: "outline", hollow: true }} data-step="no-feedback">
					<span className="px-1.5 text-muted-foreground">
						{noFeedbackText(full, lineage, filter)}
					</span>
				</Step>
			)}
			{stage.stage === "feedback" && props.path === undefined && lineage.moreFeedback > 0 && (
				<Step dot={{ tone: "outline", hollow: true }}>
					<p className="px-1.5 text-muted-foreground">
						{`${countOf(lineage.moreFeedback, "more piece", "more pieces")} of feedback not listed`}
					</p>
				</Step>
			)}
		</>
	);
}

/**
 * What an empty feedback stage says. Only a review that wrote nothing at all has "No feedback from
 * this review"; a filter, or a path that leaves the review's other pieces off, says so instead.
 */
function noFeedbackText(full: ReviewLineage, shown: ReviewLineage, filter: TraceFilter): string {
	const reviewId = full.review?.id;
	const own = full.feedback.filter(
		(entry) => reviewId === undefined || entry.agentJobId === reviewId,
	).length;
	const any = own + full.moreFeedback > 0;
	if (filter.kind !== "all" && any) {
		return hiddenByFilter(filter);
	}
	if (any || shown.feedback.length > 0) {
		return "No feedback on this path.";
	}
	return full.review?.phase === "QUEUED" || full.review?.phase === "RUNNING"
		? "No feedback yet."
		: "No feedback from this review.";
}

function RailStep(props: RailStepProps) {
	const { item, previous, lineage } = props;
	switch (item.kind) {
		case "moment": {
			return <MomentStep item={item} previous={previous} lineage={lineage} />;
		}
		case "review": {
			return (
				<ReviewStep
					lineage={lineage}
					at={item.at}
					previous={previous}
					reviewOutcome={props.reviewOutcome}
					hasStart={props.hasStart}
					here={props.here?.kind === "review"}
				/>
			);
		}
		case "results": {
			return <ResultsStep {...props} at={item.at} />;
		}
		case "feedback": {
			return <FeedbackTableStep {...props} items={[item.feedback]} />;
		}
		case "siblings": {
			return (
				<SiblingsStep
					results={item.results}
					feedback={item.feedback}
					onShowSiblings={props.onShowSiblings}
				/>
			);
		}
		case "before":
		case "end": {
			// The stage draws these itself: one folds steps, the other reads the verdict.
			return null;
		}
	}
}

/**
 * What happened on the work before the moment that started the review, folded into one line: the
 * moments before it and feedback an earlier review wrote. None of it caused this review.
 */
function BeforeSteps({
	items,
	open,
	onOpen,
	renderStep,
}: {
	items: readonly RailItem[];
	open: boolean;
	onOpen: () => void;
	renderStep: (item: RailItem) => ReactNode;
}) {
	if (open) {
		return <>{items.map(renderStep)}</>;
	}
	const moments = items.filter((item) => item.kind === "moment");
	const merged = moments.some((item) => item.moment.kind === "MERGED");
	const others = moments.length - (merged ? 1 : 0);
	const feedback = items.filter((item) => item.kind === "feedback").length;
	return (
		<Step dot={{ tone: "outline", hollow: true }} data-step="before">
			<span className="block px-1.5 text-muted-foreground">
				Before this review: {merged && "the work was merged"}
				{merged && (others > 0 || feedback > 0) && ", "}
				{others > 0 && (
					<>
						{others}{" "}
						<GlossaryTerm term="moment">{others === 1 ? "moment" : "moments"}</GlossaryTerm>
					</>
				)}
				{others > 0 && feedback > 0 && ", "}
				{feedback > 0 &&
					`${countOf(feedback, "piece", "pieces")} of feedback from an earlier review`}
				.{" "}
				<Button
					variant="link"
					size="inline"
					className="text-muted-foreground"
					aria-label="Show what came before this review"
					onClick={onOpen}
				>
					Show
				</Button>
			</span>
		</Step>
	);
}

/** The record's siblings, as one line; it lists them on the rail. */
function SiblingsStep({
	results,
	feedback,
	onShowSiblings,
}: StorySiblings & { onShowSiblings: () => void }) {
	const parts = [
		results > 0 ? countOf(results, "other result", "other results") : undefined,
		feedback > 0 ? `${countOf(feedback, "other piece", "other pieces")} of feedback` : undefined,
	].filter(hasText);
	return (
		<Step dot={{ tone: "outline", hollow: true }} data-step="siblings">
			<Button
				variant="link"
				size="inline"
				className="ml-1.5 text-muted-foreground"
				title="List them on the rail"
				onClick={onShowSiblings}
			>
				<ChevronRightIcon aria-hidden className="size-3" />
				{parts.join(", ")}
			</Button>
		</Step>
	);
}

/** Where the path of the record in front ends: it worked, it did not arrive, or it is still open. */
function EndStep({ end, previous }: { end: TraceEnd; previous: Date | undefined }) {
	const { display } = useStory();
	return (
		<Step
			dot={{ tone: TRACE_END_TONES[end.kind], hollow: end.kind === "none" }}
			at={end.at}
			gapFrom={end.at === undefined ? undefined : previous}
			data-step="end"
			data-end={end.kind}
		>
			<StoryNode node={{ kind: "end" }} className="px-1.5 py-1">
				<span className="font-medium">{end.label}</span>
				{end.since !== undefined && display.times && (
					<>
						{" since "}
						<StepTime at={end.since} />
					</>
				)}
				{": "}
				<TermSentence text={`${end.reason}.`} />
			</StoryNode>
		</Step>
	);
}

const MERGE_TONE: Record<"by-design" | "problem" | "plain", BadgeVariant> = {
	"by-design": "outline",
	problem: "warning",
	plain: "outline",
};

/** What happened on the work: what started the review, a later moment folded into it, the merge. */
function MomentStep({
	item,
	previous,
	lineage,
}: {
	item: Extract<RailItem, { kind: "moment" }>;
	previous: Date | undefined;
	lineage: ReviewLineage;
}) {
	const { moment } = item;
	const def = REVIEW_TRIGGER_DEFS[moment.kind];
	const merged = moment.kind === "MERGED";
	const note = merged ? mergeNote(lineage) : undefined;
	const tone = merged ? MERGE_TONE[note?.tone ?? "plain"] : "outline";
	return (
		<Step
			// Filled only where the merge has something to judge: a plain one is hollow, as the legend says.
			dot={{ tone, hollow: tone === "outline" }}
			at={item.at}
			gapFrom={previous}
			data-step={merged ? "merged" : "moment"}
		>
			<StoryNode node={momentNode(moment)} className="px-1.5 py-1">
				<span className="flex items-center gap-1.5">
					{merged ? <MergeIcon tone={tone} /> : <ToneIcon def={def} />}
					<span className={cn(merged && "font-medium")}>{def.label}</span>
				</span>
				{moment.startedReview && (
					<span className="block text-muted-foreground">
						This{" "}
						<GlossaryTerm term="moment" as="span">
							moment
						</GlossaryTerm>{" "}
						started the review.
					</span>
				)}
				{moment.joinedReview && (
					<span className="block text-muted-foreground">
						<GlossaryTerm term="coalesced" as="span">
							Coalesced
						</GlossaryTerm>{" "}
						into this review.
					</span>
				)}
				{note !== undefined && <span className="block text-muted-foreground">{note.text}</span>}
			</StoryNode>
		</Step>
	);
}

/** The review: how it stands, why it ran, and with system info how long it waited and ran and its model. */
function ReviewStep({
	lineage,
	at,
	previous,
	reviewOutcome,
	hasStart,
	here,
}: {
	lineage: ReviewLineage;
	at: Date;
	previous: Date | undefined;
	reviewOutcome: AgentJob["reviewOutcome"] | undefined;
	hasStart: boolean;
	here: boolean;
}) {
	const { display, resultProcessing, origin, position, factsOpen } = useStory();
	const { review, reviewTrigger } = lineage;
	if (review === undefined) {
		return (
			<Step dot={{ tone: "outline", hollow: true }}>
				<p className="text-muted-foreground">The record of this review is gone.</p>
			</Step>
		);
	}
	const state = reviewStepState(review, reviewOutcome);
	const trigger = reviewTrigger && REVIEW_TRIGGER_DEFS[reviewTrigger];
	// The queue and the run stand on the stage's header; the model is all the system adds here.
	const facts = [review.model].filter(hasText);
	return (
		<Step
			dot={{ tone: state.def.badgeVariant, here }}
			at={at}
			gapFrom={previous}
			data-step="review"
		>
			<StoryNode node={{ kind: "review" }} className="px-1.5 py-1">
				<span className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5">
					<span className="inline-flex items-center gap-1.5 font-medium">
						<ToneIcon def={state.def} />
						{state.title}
					</span>
					{/* Processed results and a live run are the ordinary case, said by saying nothing. */}
					{resultProcessing !== undefined && resultProcessing !== "DELIVERED" && (
						<StepTag def={RESULT_PROCESSING_DEFS[resultProcessing]} />
					)}
					{origin !== undefined && origin !== "LIVE" && (
						<StepTag def={RESULT_ORIGIN_DEFS[origin]} />
					)}
					{position !== undefined && position.total > 1 && (
						<Badge variant="outline" size="xs" data-review-position>
							<RepeatIcon aria-hidden />
							{`Review ${position.index} of ${position.total} on this work`}
						</Badge>
					)}
				</span>
				{hasText(state.note) && <span className="block">{state.note}</span>}

				{!hasStart && (
					<span className="block text-muted-foreground">
						{trigger === undefined
							? "Why it ran: not recorded on the work"
							: `Why it ran: ${trigger.label}`}
					</span>
				)}
				{/* The facts box under the step names the model where it opens. */}
				{display.system && !factsOpen && !here && facts.length > 0 && (
					<span className="block text-muted-foreground">{facts.join(" · ")}</span>
				)}
			</StoryNode>
		</Step>
	);
}
