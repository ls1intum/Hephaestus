import { cn } from "cn";
import { ChevronDownIcon } from "lucide-react";
import { Fragment, type ReactNode, useState } from "react";

import type {
	EvidenceCitation,
	FeedbackResponseRequest,
	Observation,
	OpenQuestion,
	PracticeResultDetail,
	PracticeResultExample,
} from "@/api/types.gen";
import { FOCUS_RING, FOCUS_RING_INSET } from "@/components/common/focus";
import { InlineLink } from "@/components/common/InlineLink";
import { ResponseButton, toneOf } from "@/components/common/ResponseButton";
import { ResponseCommentBand } from "@/components/common/ResponseCommentBand";
import { SectionLabel } from "@/components/common/SectionLabel";
import { statusToneClass, statusValues } from "@/components/common/status-def";
import { StatusBadge } from "@/components/common/StatusBadge";
import { UNTRUSTED_MARKDOWN_PROSE, UntrustedMarkdown } from "@/components/common/UntrustedMarkdown";
import { claimCurrentnessNote } from "@/components/practice-vocabulary/claim-currentness";
import {
	FEEDBACK_RESOLUTION_DEFS,
	type FeedbackResolution,
} from "@/components/practice-vocabulary/feedback-resolution-defs";
import { FEEDBACK_USEFULNESS_DEFS } from "@/components/practice-vocabulary/feedback-usefulness-defs";
import { OUTCOME_DEFS, resultVerdict } from "@/components/practice-vocabulary/outcome-defs";
import { PracticePill } from "@/components/practice-vocabulary/PracticePill";
import { MARKED_INCORRECT_DEF } from "@/components/practice-vocabulary/result-invalidation-defs";
import { RESULT_ORIGIN_DEFS } from "@/components/practice-vocabulary/result-origin-defs";
import { StatusTooltip } from "@/components/practice-vocabulary/StatusTooltip";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { rendersContent } from "@/lib/react-node";
import { capitalise, hasText } from "@/lib/text";

import {
	type EvidenceCheck,
	type EvidenceLocation,
	toEvidenceCheck,
	toEvidenceLocations,
} from "./evidence";
import { EvidenceFileBlock } from "./EvidenceFileBlock";

/**
 * The reviewer's own words about this result, in the Markdown it writes them in: a file, a
 * field or a value it quotes comes back as code rather than as a line of stray backticks. The
 * same restricted rendering the feedback card's body gets, so the two never drift apart.
 */
function ReviewerText({ children }: { children: string }) {
	return (
		<div className={cn(UNTRUSTED_MARKDOWN_PROSE, "text-sm text-pretty")}>
			<UntrustedMarkdown>{children}</UntrustedMarkdown>
		</div>
	);
}

interface DetailSectionProps {
	label: string;
	className?: string;
	children: ReactNode;
}

/** A section of the open row: its label in the muted kicker, then what it carries. */
function DetailSection({ label, className, children }: DetailSectionProps) {
	return (
		<div className={cn("flex min-w-0 flex-col gap-1", className)}>
			{/* The row's anchor is its summary, so a label naming one of its details carries no
			    weight of its own. */}
			<SectionLabel as="span" className="font-normal">
				{label}
			</SectionLabel>
			{children}
		</div>
	);
}

/** A dispute is the one answer the developer writes for somebody else to read. */
const DISPUTE_AUDIENCE =
	"Workspace admins read this on the result, and can mark it incorrect or withdraw the feedback.";

export interface ReviewResultRowProps {
	result: PracticeResultDetail;
	/** Open on arrival; a closed row waits for a press. The card placing the row decides which. */
	defaultOpen?: boolean;
	/** Off where every row is the level's own practice, so no row repeats its name. */
	showPracticeName?: boolean;
	/**
	 * The reviewed work, as a small line under the summary. A run's card gives it when the run
	 * carries this one result and the two are one block; otherwise the card's own head names the
	 * work above the rows and this is left out.
	 */
	work?: ReactNode;
	onRespond?: (result: PracticeResultDetail, response: FeedbackResponseRequest) => void;
	/**
	 * The response being written to this result's feedback: shown over the one it arrived with, and
	 * its buttons wait until the write lands.
	 */
	pendingResponse?: FeedbackResponseRequest;
}

const NO_OPEN_QUESTIONS: OpenQuestion[] = [];

/**
 * What the review wrote about the work, in the order the open row shows it: why, the problems, the
 * questions it left open, an example, what it checked, and the result's own evidence.
 */
function reasonsOf(result: PracticeResultDetail) {
	return {
		rationale: result.evidenceRationale,
		// In the review's order: the server sends them by position.
		problems: result.observations,
		openQuestions: result.evidence?.undecidability ?? NO_OPEN_QUESTIONS,
		example: result.example,
		checks: toEvidenceCheck(result.evidence),
		locations: toEvidenceLocations(result.evidence?.citations ?? []),
	};
}

type ResultReasons = ReturnType<typeof reasonsOf>;

function hasReasons(reasons: ResultReasons): boolean {
	return (
		hasText(reasons.rationale) ||
		reasons.problems.length > 0 ||
		reasons.openQuestions.length > 0 ||
		reasons.example !== undefined ||
		reasons.checks.length > 0 ||
		reasons.locations.length > 0
	);
}

/** Each part of what the review wrote, under its own label; a part it did not write is left out. */
function ReasonSections({ reasons }: { reasons: ResultReasons }) {
	return (
		<>
			{hasText(reasons.rationale) && (
				<DetailSection label="Why it was noted">
					<ReviewerText>{reasons.rationale}</ReviewerText>
				</DetailSection>
			)}
			{reasons.problems.length > 0 && (
				<DetailSection label="Problems" className="gap-1.5">
					<ProblemList problems={reasons.problems} />
				</DetailSection>
			)}
			{reasons.openQuestions.length > 0 && (
				<DetailSection label="Open questions" className="gap-1.5">
					<OpenQuestionList questions={reasons.openQuestions} />
				</DetailSection>
			)}
			{reasons.example !== undefined && (
				<DetailSection label="Example from this work" className="gap-1.5">
					<ExampleFromTheWork example={reasons.example} />
				</DetailSection>
			)}
			{reasons.checks.length > 0 && (
				<DetailSection label="What was checked">
					<TermList pairs={reasons.checks} />
				</DetailSection>
			)}
			{reasons.locations.length > 0 && (
				<DetailSection label="Evidence" className="gap-1.5">
					<EvidenceBlocks locations={reasons.locations} />
				</DetailSection>
			)}
		</>
	);
}

/**
 * One result as a row of its review's card: the verdict on its head, and, open, the problems behind
 * it, the questions the review left open, and an example from the work. The outcome is what the
 * reader acts on, so the severity of the result and of each problem stays in the workspace's admin
 * console rather than ranking the reader's own work here.
 */
export function ReviewResultRow({
	result,
	defaultOpen = true,
	showPracticeName = true,
	work,
	onRespond,
	pendingResponse,
}: ReviewResultRowProps) {
	const outcome = OUTCOME_DEFS[result.outcome];
	const OutcomeIcon = outcome.icon;
	const note = claimCurrentnessNote(result.claimCurrentness);
	const invalidated = result.invalidatedAt !== undefined;
	const reasons = reasonsOf(result);
	const nextStep = capitalise(result.nextStep ?? "");
	// A response needs feedback to respond to and a route that records it.
	const hasFeedback = hasText(result.feedbackResponse?.feedbackId);
	const respondTo = hasFeedback ? onRespond : undefined;
	const commentUrls = result.feedbackCommentUrls ?? [];
	// Without a recorded comment link the reviewed work is the nearest truthful place to go, and it
	// says so rather than posing as a link to the feedback.
	const workUrl =
		hasFeedback && commentUrls.length === 0 && hasText(result.artifactUrl)
			? result.artifactUrl
			: undefined;
	const hasWorkLine = rendersContent(work);
	const hasBody =
		invalidated ||
		note !== undefined ||
		hasReasons(reasons) ||
		hasText(nextStep) ||
		commentUrls.length > 0 ||
		workUrl !== undefined ||
		respondTo !== undefined;
	// The summary is the row's anchor and the heaviest text in it; the practice under it is the pill
	// every practice surface names a practice with, and it stands alone when the result has no
	// words of its own.
	const summary = result.summary.trim();
	const showPill = showPracticeName || summary.length === 0;

	// At the reflow width the summary takes the whole line and the outcome and the badges sit under
	// it, at the right, wrapping among themselves when even that line is too narrow; from `sm` up
	// they share the line with the summary.
	const headLineClassName = cn(
		"flex w-full min-w-0 flex-wrap items-center gap-x-4 gap-y-1.5 px-4 pt-2.5",
		// The work line below is the same block's second line, so the padding closes under it.
		hasWorkLine ? "pb-1" : "pb-2.5",
	);
	const headLine = (
		<>
			<span className="flex min-w-0 basis-full flex-col items-start gap-1 text-sm sm:flex-1 sm:basis-0">
				{summary.length > 0 && <span className="font-medium text-foreground">{summary}</span>}
				{showPill && <PracticePill name={result.practiceName} />}
			</span>
			<span className="ml-auto flex min-w-0 flex-wrap items-center justify-end gap-x-2.5 gap-y-1">
				{invalidated && <StatusBadge def={MARKED_INCORRECT_DEF} />}
				{result.origin !== "LIVE" && <StatusBadge def={RESULT_ORIGIN_DEFS[result.origin]} />}
				{/* Inside the row's own button the chip is a span, the words visible beside the icon;
				    on a row that is no control it is the button a keyboard reaches the sentence by. */}
				<StatusTooltip
					def={outcome}
					render={hasBody ? <span /> : <button type="button" />}
					className={cn(
						"inline-flex items-center gap-1.5 text-sm font-medium whitespace-nowrap",
						statusToneClass(outcome.badgeVariant),
						!hasBody && FOCUS_RING,
					)}
				>
					<OutcomeIcon className="size-3.5 shrink-0" aria-hidden />
					{resultVerdict(result.outcome, reasons.problems.length)}
				</StatusTooltip>
				{hasBody && (
					<ChevronDownIcon
						className="size-4 shrink-0 text-muted-foreground transition-transform group-data-[panel-open]/result:rotate-180"
						aria-hidden
					/>
				)}
			</span>
		</>
	);

	// A row with nothing under its head line is not a control: a button that opens nothing would
	// still take focus and a pointer.
	if (!hasBody) {
		return (
			<li>
				<div className={headLineClassName}>{headLine}</div>
				{hasWorkLine && <div className="px-4 pb-2.5">{work}</div>}
			</li>
		);
	}

	return (
		<li>
			<Collapsible className="group/result" defaultOpen={defaultOpen}>
				{/* The tint is the wrapper's, not the trigger's: with a work line under it the head is
				    two lines of one block, and tinting only the first would draw back the seam this
				    layout exists to remove. */}
				<div className="transition-colors hover:bg-muted/50">
					<CollapsibleTrigger
						className={cn(headLineClassName, "cursor-pointer text-left", FOCUS_RING_INSET)}
					>
						{headLine}
					</CollapsibleTrigger>
					{hasWorkLine && <div className="px-4 pb-2.5">{work}</div>}
				</div>
				<CollapsibleContent className="border-t bg-sidebar">
					<div className="flex min-w-0 flex-col gap-3.5 px-4 pt-3.5 pb-4">
						{invalidated && (
							<p className="text-sm">
								<span className="font-medium">
									A workspace admin marked this result as incorrect.
								</span>{" "}
								It no longer counts toward your standing or what Heph knows about your work.
								{hasText(result.invalidationReason) && (
									<> Their reason: “{result.invalidationReason}”</>
								)}
							</p>
						)}
						{note !== undefined && <p className="text-sm text-muted-foreground">{note}</p>}
						<ReasonSections reasons={reasons} />
						{hasText(nextStep) && (
							<DetailSection label="Next step">
								<ReviewerText>{nextStep}</ReviewerText>
							</DetailSection>
						)}
						{commentUrls.length > 0 && (
							<DetailSection label="Feedback on the work">
								<FeedbackCommentLinks urls={commentUrls} />
							</DetailSection>
						)}
						{workUrl !== undefined && (
							<p className="text-sm">
								<InlineLink href={workUrl} external>
									Open reviewed work
								</InlineLink>
							</p>
						)}
					</div>
					{respondTo && (
						<ResultResponse
							result={result}
							onRespond={respondTo}
							pendingResponse={pendingResponse}
						/>
					)}
				</CollapsibleContent>
			</Collapsible>
		</li>
	);
}

/**
 * Muted terms with the reviewer's words for each. A definition list, so a term and what the review
 * wrote for it reach a screen reader as one pair rather than as two sentences in a row.
 */
function TermList({ pairs }: { pairs: EvidenceCheck[] }) {
	return (
		<dl className="grid min-w-0 grid-cols-1 gap-x-3 gap-y-1 text-sm sm:grid-cols-[auto_1fr]">
			{pairs.map(({ term, detail }) => (
				<Fragment key={term}>
					<dt className="text-muted-foreground">{term}:</dt>
					<dd className="min-w-0">
						<ReviewerText>{detail}</ReviewerText>
					</dd>
				</Fragment>
			))}
		</dl>
	);
}

/** Quoted places as their blocks, in the order the reviewer cited them. */
function EvidenceBlocks({ locations }: { locations: EvidenceLocation[] }) {
	return (
		<div className="flex min-w-0 flex-col gap-2">
			{locations.map((location) => (
				<EvidenceFileBlock
					// A diff pair only folds when both sides carry a quote, so a redacted side leaves
					// two citations of the same lines apart: the side and the redaction tell them apart.
					key={`${location.sourceKind}-${location.path}-${location.revision ?? ""}-${location.startLine}-${location.side ?? ""}-${location.redacted}`}
					location={location}
				/>
			))}
		</div>
	);
}

/**
 * The blocks of what one problem, open question or example cites. Each list folds on its own, so
 * a quote never joins the block of another problem.
 */
function CitationBlocks({ citations }: { citations: EvidenceCitation[] }) {
	const locations = toEvidenceLocations(citations);
	return locations.length > 0 ? <EvidenceBlocks locations={locations} /> : null;
}

/** One item of a list inside the open row: a block on the row's ground, so items stay apart. */
const ITEM_CLASS = "flex min-w-0 flex-col gap-1.5 rounded-xl border bg-background p-3";

/**
 * The problems behind a result that is not met, in the review's order: what is wrong, why, where,
 * and what to change. No problem carries its severity here, for the reason the row gives.
 */
function ProblemList({ problems }: { problems: Observation[] }) {
	return (
		<ol aria-label="Problems" className="flex min-w-0 flex-col gap-2">
			{problems.map((problem) => {
				const problemNextStep = capitalise(problem.nextStep ?? "");
				return (
					<li key={problem.observationId} className={ITEM_CLASS}>
						<span className="text-sm font-medium text-pretty">{problem.summary.trim()}</span>
						{hasText(problem.explanation) && <ReviewerText>{problem.explanation}</ReviewerText>}
						<CitationBlocks citations={problem.citations} />
						{hasText(problemNextStep) && (
							<TermList pairs={[{ term: "Next step", detail: problemNextStep }]} />
						)}
					</li>
				);
			})}
		</ol>
	);
}

/**
 * What the review could not settle, each with what would settle it. An open question makes no
 * claim about the work and is never feedback, so it reads as a question and nothing more.
 */
function OpenQuestionList({ questions }: { questions: OpenQuestion[] }) {
	return (
		<ul aria-label="Open questions" className="flex min-w-0 flex-col gap-2">
			{questions.map((question) => (
				<li key={`${question.openQuestion}-${question.wouldSettleIt}`} className={ITEM_CLASS}>
					<ReviewerText>{question.openQuestion}</ReviewerText>
					<TermList pairs={[{ term: "Would settle it", detail: question.wouldSettleIt }]} />
					{question.citation !== undefined && <CitationBlocks citations={[question.citation]} />}
				</li>
			))}
		</ul>
	);
}

/**
 * One place in the work the review points at, with its note on it. It is evidence about the work
 * on the neutral ground every other block has: never praise, and never in a status colour that
 * would read as a grade.
 */
function ExampleFromTheWork({ example }: { example: PracticeResultExample }) {
	return (
		<div className="flex min-w-0 flex-col gap-1.5">
			<ReviewerText>{example.note}</ReviewerText>
			<CitationBlocks citations={[example.citation]} />
		</div>
	);
}

/**
 * The comments that carry this result's feedback on the reviewed work, in the order the
 * server gives: the summary first, then the line notes.
 */
function FeedbackCommentLinks({ urls }: { urls: string[] }) {
	if (urls.length === 1) {
		return (
			<p className="text-sm">
				<InlineLink href={urls[0]} external>
					Read the feedback comment
				</InlineLink>
			</p>
		);
	}
	return (
		<ul className="flex flex-wrap gap-x-3 gap-y-1 text-sm">
			{urls.map((url, index) => (
				<li key={url}>
					<InlineLink href={url} external>
						Read feedback comment {index + 1}
					</InlineLink>
				</li>
			))}
		</ul>
	);
}

interface ResultResponseProps {
	result: PracticeResultDetail;
	onRespond: NonNullable<ReviewResultRowProps["onRespond"]>;
	pendingResponse?: FeedbackResponseRequest;
}

/**
 * The reader's response under the row: the three resolutions, what already stands — the rating
 * given on the feedback card and the comment sent with the response — and the comment band a
 * press opens. The endpoint replaces, so the usefulness the result arrived with travels every
 * time.
 */
function ResultResponse({ result, onRespond, pendingResponse }: ResultResponseProps) {
	// The resolution whose comment band is open; recorded only once Send or Skip closes it.
	const [pendingResolution, setPendingResolution] = useState<FeedbackResolution>();
	const isPending = pendingResponse !== undefined;
	// The response being written stands over the recorded one, so the buttons do not jump back and
	// then forward again when it lands.
	const current: FeedbackResponseRequest = pendingResponse ?? result.feedbackResponse ?? {};
	const shownResolution = pendingResolution ?? current.resolution;
	const respond = (change: Pick<FeedbackResponseRequest, "resolution" | "comment">) => {
		onRespond(result, { usefulness: current.usefulness, ...change });
	};
	// A press opens the band for that choice; the chosen one pressed again withdraws it, comment
	// and all, and a band still open closes without recording anything.
	const chooseResolution = (resolution: FeedbackResolution) => {
		if (pendingResolution === resolution) {
			setPendingResolution(undefined);
			return;
		}
		if (current.resolution === resolution) {
			setPendingResolution(undefined);
			respond({ resolution: undefined, comment: undefined });
			return;
		}
		setPendingResolution(resolution);
	};
	const record = (comment: string | undefined) => {
		if (pendingResolution === undefined) {
			return;
		}
		setPendingResolution(undefined);
		respond({ resolution: pendingResolution, comment });
	};
	return (
		<>
			<DetailSection label="Your response" className="gap-2.5 px-4 pb-4">
				<div
					role="group"
					aria-label={`Your response to ${result.practiceName}`}
					className="flex flex-wrap items-center gap-2"
				>
					{statusValues(FEEDBACK_RESOLUTION_DEFS).map((value) => {
						const def = FEEDBACK_RESOLUTION_DEFS[value];
						return (
							<ResponseButton
								key={value}
								tone={toneOf(def.badgeVariant)}
								pressed={shownResolution === value}
								disabled={isPending}
								className="bg-background"
								onClick={() => chooseResolution(value)}
							>
								{def.label}
							</ResponseButton>
						);
					})}
				</div>
				{/* What the reader already said: the rating given on the feedback card, and the
				    comment sent with the response that stands. */}
				{(current.usefulness !== undefined || hasText(current.comment)) && (
					<p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
						{current.usefulness !== undefined && (
							<StatusBadge def={FEEDBACK_USEFULNESS_DEFS[current.usefulness]} />
						)}
						{hasText(current.comment) && <q>{current.comment}</q>}
					</p>
				)}
			</DetailSection>
			{pendingResolution === "DISPUTED" && (
				// The sentence is what a dispute has to carry, so Skip only closes the band.
				<ResponseCommentBand
					key={pendingResolution}
					name="Why you dispute this"
					label="Why do you dispute this?"
					placeholder="One or two sentences on what is wrong"
					required
					audience={DISPUTE_AUDIENCE}
					isPending={isPending}
					onSend={(comment) => {
						const sentence = comment.trim();
						if (hasText(sentence)) {
							record(sentence);
						}
					}}
					onSkip={() => setPendingResolution(undefined)}
				/>
			)}
			{pendingResolution !== undefined && pendingResolution !== "DISPUTED" && (
				<ResponseCommentBand
					key={pendingResolution}
					name={pendingResolution === "ADDRESSED" ? "What you changed" : "Why this does not apply"}
					label="Anything to add?"
					placeholder="Optional: a note to yourself"
					isPending={isPending}
					onSend={(comment) => record(comment.trim() || undefined)}
					onSkip={() => record(undefined)}
				/>
			)}
		</>
	);
}
