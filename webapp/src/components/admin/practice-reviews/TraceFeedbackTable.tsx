import { EyeOffIcon } from "lucide-react";
import { type ReactNode, use } from "react";

import { cn } from "cn";
import type { ReviewLineage, ReviewLineageFeedback } from "@/api/types.gen";
import type { BadgeVariant } from "@/components/common/status-def";
import { DELIVERY_PLACE_DEFS } from "@/components/practice-vocabulary/delivery-place-defs";
import {
	DEVELOPER_ANSWER_DEFS,
	developerAnswers,
} from "@/components/practice-vocabulary/developer-answer-defs";
import { hasText } from "@/lib/text";

import {
	type BranchOutcome,
	branchOutcome,
	countOf,
	gapLabel,
	type NumberedById,
	isSlowGap,
} from "./lineage-chain";
import { writtenBy } from "./lineage-story";
import { LineageSourceChips } from "./LineageResultChip";
import {
	FeedbackLetter,
	LineageTimes,
	OffPathNote,
	Step,
	StepTag,
	StepTime,
	ToneIcon,
} from "./LineageStep";
import { sameTraceNode, type TraceNode } from "./review-trace";
import { type StepProps, StoryNode, offPath, useStory } from "./TraceStoryNode";
import { TRACE_ROW_CELLS, TRACE_ROW_MAIN, TraceTable, traceRowGrid } from "./TraceTable";

/** The tones a step may take, the one that most needs a look first. */
const TONE_ORDER: readonly BadgeVariant[] = [
	"destructive",
	"warning",
	"secondary",
	"default",
	"outline",
	"success",
];

/** The tone of a piece's verdict: delivered, withheld, failed or waiting. */
function feedbackTone(feedback: ReviewLineageFeedback, lineage: ReviewLineage): BadgeVariant {
	const outcome = branchOutcome(feedback, lineage.work);
	return outcome.kind === "delivered" ? "success" : outcome.def.badgeVariant;
}

/**
 * Pieces of feedback written one after the other, with nothing on the work between them, as one
 * table on the rail: a row per piece with its letter, its channel, its state, the results it was
 * written from by number, and when it was composed. Its dot takes the tone of the piece that most
 * needs a look, and the rail is dashed into it after a long wait.
 */
export function FeedbackTableStep({
	items,
	previous,
	...props
}: StepProps & { items: readonly ReviewLineageFeedback[]; previous: Date | undefined }) {
	const { display } = useStory();
	const { lineage, traced, here } = props;
	const [first] = items;
	if (first === undefined) {
		return null;
	}
	const tones = new Set(items.map((feedback) => feedbackTone(feedback, lineage)));
	const tone = TONE_ORDER.find((entry) => tones.has(entry)) ?? feedbackTone(first, lineage);
	const nodes = items.map((feedback): TraceNode => ({ kind: "feedback", id: feedback.id }));
	return (
		<Step
			dot={{ tone, here: nodes.some((node) => sameTraceNode(here, node)) }}
			at={first.createdAt}
			gapFrom={previous}
			timeInside
			muted={nodes.every((node) => offPath(traced, node))}
			data-step="feedback-table"
		>
			<TraceTable
				kind="feedback"
				times={display.times}
				label={countOf(items.length, "piece of feedback", "pieces of feedback")}
			>
				{items.map((feedback, index) => (
					<FeedbackRow
						key={feedback.id}
						{...props}
						feedback={feedback}
						previous={index === 0 ? previous : items[index - 1]?.createdAt}
					/>
				))}
			</TraceTable>
		</Step>
	);
}

/**
 * One piece of feedback as a row: its letter, the channel it went by, where it went or why not with
 * how the developer answered, the results it was written from, and when it was composed with
 * the wait since the row above. With system info, every fact of the piece with its time under its
 * state.
 */
function FeedbackRow({
	feedback,
	previous,
	lineage,
	byId,
	letters,
	traced,
}: StepProps & { feedback: ReviewLineageFeedback; previous: Date | undefined }) {
	const { display, factsOpen, here, job, firstReadAt } = useStory();
	const node: TraceNode = { kind: "feedback", id: feedback.id };
	// Where the facts box opens under the row, it says all of this once; the row keeps its one line.
	const boxed = factsOpen || (display.system && sameTraceNode(here, node));
	const details = display.system && !boxed;
	const outcome = branchOutcome(feedback, lineage.work);
	// The channel column says where it went, so a delivered piece only says that it arrived.
	const stateText = outcome.kind === "delivered" ? "Delivered" : outcome.text;
	const unopened =
		feedback.channel === "IN_APP" &&
		outcome.kind === "delivered" &&
		job !== undefined &&
		firstReadAt === undefined;
	const elsewhere = writtenBy(feedback, lineage.review);
	const answers = developerAnswers(feedback.response);
	const letter = letters.get(feedback.id) ?? "";
	const muted = offPath(traced, node);
	return (
		<li
			data-step="feedback"
			data-feedback-row={feedback.id}
			data-muted={muted}
			className={cn(muted && "text-muted-foreground")}
		>
			<OffPathNote muted={muted} />
			<StoryNode
				row
				node={node}
				className={cn(traceRowGrid("feedback", display.times), "px-1.5 py-1.5")}
			>
				<FeedbackLetter letter={letter} className="mt-px justify-self-start" />
				<span className={TRACE_ROW_CELLS}>
					<span className="min-w-0">
						<StepTag
							def={DELIVERY_PLACE_DEFS[feedback.channel]}
							data-feedback-channel={feedback.channel}
						/>
					</span>
					<span className={TRACE_ROW_MAIN}>
						<span className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5">
							<ToneIcon def={outcome.def} />
							<span className="font-medium">{stateText}</span>
							{/* The row's time is when it was composed; this is how long after it got there. */}
							{outcome.at !== undefined && <GapText from={feedback.createdAt} to={outcome.at} />}
							{unopened && (
								<span
									data-unopened
									className="inline-flex items-center gap-1 text-muted-foreground"
								>
									<EyeOffIcon aria-hidden className="size-3" />
									Not opened yet
								</span>
							)}
						</span>
						{!details && !boxed && answers.length > 0 && (
							<span className="flex flex-wrap items-center gap-x-2">
								<span className="sr-only">Developer answered:</span>
								{answers.map((answer) => (
									<span key={answer} className="inline-flex items-center gap-1">
										<ToneIcon def={DEVELOPER_ANSWER_DEFS[answer]} />
										{DEVELOPER_ANSWER_DEFS[answer].label}
									</span>
								))}
							</span>
						)}
						{details && (
							<FeedbackDetails
								feedback={feedback}
								outcome={outcome}
								letters={letters}
								elsewhere={elsewhere}
							/>
						)}
						{!details && !boxed && (
							<FeedbackExceptions feedback={feedback} letters={letters} elsewhere={elsewhere} />
						)}
					</span>
					<span className="min-w-0 text-muted-foreground">
						<FeedbackSources
							feedback={feedback}
							byId={byId}
							elsewhere={elsewhere}
							connectors={display.connections}
						/>
					</span>
					{display.times && (
						<span className="inline-flex flex-wrap items-baseline gap-x-1.5 text-muted-foreground @lg:justify-end @lg:text-right">
							<StepTime at={feedback.createdAt} previous={previous} />
							{previous !== undefined && previous.getTime() <= feedback.createdAt.getTime() && (
								<GapText from={previous} to={feedback.createdAt} />
							)}
						</span>
					)}
				</span>
			</StoryNode>
		</li>
	);
}

/** A time inside a step, where the trace shows times. */
function RailTime(props: { at: Date; className?: string }) {
	return use(LineageTimes) ? <StepTime {...props} /> : null;
}

/** "+30 s" after a step's own time, coloured where the wait was long, where the trace shows times. */
function GapText({ from, to }: { from: Date; to: Date }) {
	const showTimes = use(LineageTimes);
	if (!showTimes) {
		return null;
	}
	const slow = isSlowGap((to.getTime() - from.getTime()) / 1000);
	return (
		<span
			className={cn(
				"text-2xs text-muted-foreground tabular-nums",
				slow && "font-medium text-warning",
			)}
		>
			{gapLabel(from, to)}
			<span className="sr-only">{slow ? " later, a long wait" : " later"}</span>
		</span>
	);
}

/** The few facts the simple line cannot leave out: who wrote it, what it replaced, an admin's hand. */
function FeedbackExceptions({
	feedback,
	letters,
	elsewhere,
}: {
	feedback: ReviewLineageFeedback;
	letters: ReadonlyMap<string, string>;
	elsewhere: string | undefined;
}) {
	const notes = [
		elsewhere === undefined ? undefined : `Composed by ${elsewhere}`,
		hasText(feedback.replacesId)
			? `Replaces ${letters.get(feedback.replacesId) ?? "earlier feedback"}`
			: undefined,
		hasText(feedback.replacedById)
			? `Replaced by ${letters.get(feedback.replacedById) ?? "newer feedback"}`
			: undefined,
		feedback.decision === "APPROVED" ? "Approved by an admin" : undefined,
		hasText(feedback.response?.disputeExplanation) ? "With the developer’s reason" : undefined,
	].filter(hasText);
	if (notes.length === 0) {
		return null;
	}
	return <span className="mt-0.5 block text-muted-foreground">{notes.join(" · ")}</span>;
}

interface DetailLine {
	key: string;
	body: ReactNode;
	at?: Date;
	from?: Date;
}

/** Every fact of a piece with its time: where it went, an admin's decision, each answer. */
function FeedbackDetails({
	feedback,
	outcome,
	letters,
	elsewhere,
}: {
	feedback: ReviewLineageFeedback;
	outcome: BranchOutcome;
	letters: ReadonlyMap<string, string>;
	elsewhere: string | undefined;
}) {
	const { response } = feedback;
	const answers = developerAnswers(response).filter((answer) => answer !== "RESOLVED_BY_WORK");
	const lines: DetailLine[] = [];
	if (elsewhere !== undefined) {
		lines.push({ key: "by", body: `Composed by ${elsewhere}` });
	}
	if (hasText(feedback.replacesId)) {
		lines.push({
			key: "replaces",
			body: `Replaces ${letters.get(feedback.replacesId) ?? "earlier feedback"}`,
		});
	}
	if (feedback.decision !== undefined) {
		lines.push({
			key: "decision",
			body: feedback.decision === "APPROVED" ? "Approved by an admin" : "Rejected by an admin",
			at: feedback.decidedAt,
			from: feedback.createdAt,
		});
	}
	// The outcome and its time head the piece; a reason the short words cut stands under it.
	if (hasText(outcome.detail)) {
		lines.push({ key: "outcome", body: outcome.detail });
	}
	if (
		outcome.kind === "delivered" &&
		feedback.channel === "IN_CONTEXT" &&
		feedback.placementCount > 0
	) {
		lines.push({
			key: "placements",
			body: countOf(feedback.placementCount, "comment", "comments"),
		});
	}
	if (hasText(feedback.replacedById)) {
		lines.push({
			key: "replaced",
			body: `Replaced by ${letters.get(feedback.replacedById) ?? "newer feedback"}`,
		});
	}
	if (answers.length > 0) {
		lines.push({
			key: "answered",
			body: (
				<span className="inline-flex flex-wrap items-center gap-x-2">
					{answers.map((answer) => (
						<span key={answer} className="inline-flex items-center gap-1 text-foreground">
							<ToneIcon def={DEVELOPER_ANSWER_DEFS[answer]} />
							{DEVELOPER_ANSWER_DEFS[answer].label}
						</span>
					))}
				</span>
			),
			at: response?.respondedAt,
			from: feedback.deliveredAt,
		});
	}
	if (hasText(response?.disputeExplanation)) {
		lines.push({
			key: "explanation",
			body: <q className="text-foreground italic">{response.disputeExplanation}</q>,
		});
	}
	if (response?.resolvedByWorkAt !== undefined) {
		lines.push({
			key: "resolved",
			body: (
				<span className="inline-flex items-center gap-1 text-foreground">
					<ToneIcon def={DEVELOPER_ANSWER_DEFS.RESOLVED_BY_WORK} />
					{DEVELOPER_ANSWER_DEFS.RESOLVED_BY_WORK.label}
				</span>
			),
			at: response.resolvedByWorkAt,
			from: response.respondedAt ?? feedback.deliveredAt,
		});
	}
	if (response === undefined) {
		const reached = outcome.kind === "delivered" || feedback.deliveredAt !== undefined;
		lines.push({ key: "none", body: reached ? "No answer yet" : "Nothing to answer" });
	}
	return (
		<ul className="mt-1 space-y-0.5 border-l pl-2 text-muted-foreground">
			{lines.map((line) => (
				<li key={line.key} className="flex flex-wrap items-baseline gap-x-1.5">
					<span className="min-w-0 break-words">{line.body}</span>
					{line.at !== undefined && <RailTime at={line.at} className="text-2xs" />}
					{line.at !== undefined && line.from !== undefined && (
						<GapText from={line.from} to={line.at} />
					)}
				</li>
			))}
		</ul>
	);
}

/**
 * The results a piece was written from, as chips. A piece another review wrote may rest on
 * results this chain does not list: those are counted as that review's, never as missing.
 */
function FeedbackSources({
	feedback,
	byId,
	elsewhere,
	connectors,
}: {
	feedback: ReviewLineageFeedback;
	byId: NumberedById;
	elsewhere: "an earlier review" | "a later review" | undefined;
	connectors: boolean;
}) {
	const listed = feedback.practiceResultIds.filter((id) => byId.has(id)).length;
	if (elsewhere !== undefined && listed === 0) {
		return `From ${countOf(feedback.practiceResultIds.length, "result", "results")} that review recorded`;
	}
	return (
		<LineageSourceChips
			feedback={feedback}
			byId={byId}
			elsewhere={elsewhere}
			connectors={connectors}
			fromClassName="@lg:sr-only"
		/>
	);
}
