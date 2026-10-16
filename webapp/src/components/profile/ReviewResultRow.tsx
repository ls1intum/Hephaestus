import { cn } from "cn";
import { ChevronDownIcon } from "lucide-react";
import { Fragment, type ReactNode } from "react";

import type {
	EvidenceCitation,
	OpenQuestion,
	ResultDetail,
	ResultExample,
	ResultList,
} from "@/api/types.gen";
import { FOCUS_RING, FOCUS_RING_INSET } from "@/components/common/focus";
import { SectionLabel } from "@/components/common/SectionLabel";
import { statusToneClass } from "@/components/common/status-def";
import { StatusBadge } from "@/components/common/StatusBadge";
import { UNTRUSTED_MARKDOWN_PROSE, UntrustedMarkdown } from "@/components/common/UntrustedMarkdown";
import { ProblemList } from "@/components/practice-problems/ProblemList";
import { claimCurrentnessNote } from "@/components/practice-vocabulary/claim-currentness";
import { OUTCOME_DEFS, outcomeLabel } from "@/components/practice-vocabulary/outcome-defs";
import { PracticePill } from "@/components/practice-vocabulary/PracticePill";
import { RESULT_MARK_DEFS } from "@/components/practice-vocabulary/result-mark-defs";
import { REVIEW_ORIGIN_DEFS } from "@/components/practice-vocabulary/review-origin-defs";
import { StatusTooltip } from "@/components/practice-vocabulary/StatusTooltip";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { rendersContent } from "@/lib/react-node";
import { hasText } from "@/lib/text";

import {
	type EvidenceCheck,
	type EvidenceLocation,
	toEvidenceCheck,
	toEvidenceLocations,
} from "./evidence";
import { EvidenceFileBlock } from "./EvidenceFileBlock";
import { OwnFeedbackList } from "./OwnFeedbackList";
import { type FeedbackResponseControls, resultSummary } from "./reviews";

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

export interface ReviewResultRowProps {
	/**
	 * A result as a list carries it — its verdict and its problems' summaries — or in full, with the
	 * proof behind it and the feedback it was the basis of.
	 */
	result: ResultList | ResultDetail;
	/** Open on arrival; a closed row waits for a press. The card placing the row decides which. */
	defaultOpen?: boolean;
	/** Off where every row is the level's own practice, so no row repeats its name. */
	showPracticeName?: boolean;
	/**
	 * The reviewed work, as a small line under the summary. A review's card gives it when the
	 * review carries this one result and the two are one block; otherwise the card's own head names
	 * the work above the rows and this is left out.
	 */
	work?: ReactNode;
	/** The reader's response to the result's feedback; absent leaves no response controls. */
	responses?: FeedbackResponseControls;
}

const NO_OPEN_QUESTIONS: OpenQuestion[] = [];

function isResultDetail(result: ResultList | ResultDetail): result is ResultDetail {
	return "explanation" in result;
}

function reasonsOf(result: ResultList | ResultDetail) {
	const detail = isResultDetail(result) ? result : undefined;
	return {
		explanation: detail?.explanation,
		problems: result.problems,
		openQuestions: detail?.evidence?.openQuestions ?? NO_OPEN_QUESTIONS,
		example: detail?.example ?? null,
		checks: toEvidenceCheck(detail?.evidence ?? null),
		locations: toEvidenceLocations(detail?.evidence?.citations ?? []),
	};
}

type ResultReasons = ReturnType<typeof reasonsOf>;

function hasReasons(reasons: ResultReasons): boolean {
	return (
		hasText(reasons.explanation) ||
		reasons.problems.length > 0 ||
		reasons.openQuestions.length > 0 ||
		reasons.example !== null ||
		reasons.checks.length > 0 ||
		reasons.locations.length > 0
	);
}

function ReasonSections({ reasons }: { reasons: ResultReasons }) {
	return (
		<>
			{hasText(reasons.explanation) && (
				<DetailSection label="Why it was noted">
					<ReviewerText>{reasons.explanation}</ReviewerText>
				</DetailSection>
			)}
			{reasons.problems.length > 0 && (
				<DetailSection label="Problems" className="gap-1.5">
					<ProblemList
						problems={reasons.problems}
						evidence={(problem) => <CitationBlocks citations={problem.citations} />}
						severity="omitted"
					/>
				</DetailSection>
			)}
			{reasons.openQuestions.length > 0 && (
				<DetailSection label="Open questions" className="gap-1.5">
					<OpenQuestionList questions={reasons.openQuestions} />
				</DetailSection>
			)}
			{reasons.example !== null && (
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

export function ReviewResultRow({
	result,
	defaultOpen = true,
	showPracticeName = true,
	work,
	responses,
}: ReviewResultRowProps) {
	const outcome = OUTCOME_DEFS[result.outcome];
	const OutcomeIcon = outcome.icon;
	const note = claimCurrentnessNote(result.currentness);
	const markedIncorrect = result.markedIncorrectAt !== null;
	const reasons = reasonsOf(result);
	const feedback = isResultDetail(result) ? result.feedback : [];
	const hasWorkLine = rendersContent(work);
	const hasBody =
		markedIncorrect || note !== undefined || hasReasons(reasons) || feedback.length > 0;
	const summary = resultSummary(result);
	const showPill = showPracticeName && summary !== result.practiceName;

	const headLineClassName = cn(
		"flex w-full min-w-0 flex-wrap items-center gap-x-4 gap-y-1.5 px-4 pt-2.5",
		hasWorkLine ? "pb-1" : "pb-2.5",
	);
	const headLine = (
		<>
			<span className="flex min-w-0 basis-full flex-col items-start gap-1 text-sm sm:flex-1 sm:basis-0">
				<span className="font-medium text-foreground">{summary}</span>
				{result.summary === null && (
					<span className="text-xs text-muted-foreground">Result text unavailable</span>
				)}
				{showPill && <PracticePill name={result.practiceName} />}
			</span>
			<span className="ml-auto flex min-w-0 flex-wrap items-center justify-end gap-x-2.5 gap-y-1">
				{markedIncorrect && <StatusBadge def={RESULT_MARK_DEFS.INCORRECT} />}
				{result.origin !== "EVENT" && <StatusBadge def={REVIEW_ORIGIN_DEFS[result.origin]} />}
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
					{outcomeLabel(result.outcome, reasons.problems.length)}
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

	// A row with no expandable detail must not create an empty focusable control.
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
						{markedIncorrect && (
							<p className="text-sm">
								<span className="font-medium">
									A workspace admin marked this result as incorrect.
								</span>{" "}
								It no longer counts toward your standing or what Heph knows about your work.
							</p>
						)}
						{note !== undefined && <p className="text-sm text-muted-foreground">{note}</p>}
						<ReasonSections reasons={reasons} />
						{feedback.length > 0 && (
							<DetailSection label="Feedback" className="gap-2">
								<OwnFeedbackList feedback={feedback} responses={responses} />
							</DetailSection>
						)}
					</div>
				</CollapsibleContent>
			</Collapsible>
		</li>
	);
}

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

function EvidenceBlocks({ locations }: { locations: EvidenceLocation[] }) {
	return (
		<div className="flex min-w-0 flex-col gap-2">
			{locations.map((location) => (
				<EvidenceFileBlock
					// Fold a diff pair only when both sides retain quotes; missing quotes carry distinct evidence limits.
					key={`${location.sourceKind}-${location.path ?? ""}-${location.commit ?? ""}-${location.startLine ?? ""}-${location.side ?? ""}-${location.snippet === undefined}`}
					location={location}
				/>
			))}
		</div>
	);
}

function CitationBlocks({ citations }: { citations: EvidenceCitation[] }) {
	const locations = toEvidenceLocations(citations);
	return locations.length > 0 ? <EvidenceBlocks locations={locations} /> : null;
}

const ITEM_CLASS = "flex min-w-0 flex-col gap-1.5 rounded-xl border bg-background p-3";

// Open questions describe uncertainty, so they cannot become feedback claims.
function OpenQuestionList({ questions }: { questions: OpenQuestion[] }) {
	return (
		<ul aria-label="Open questions" className="flex min-w-0 flex-col gap-2">
			{questions.map((question) => (
				<li key={`${question.openQuestion}-${question.wouldSettleIt}`} className={ITEM_CLASS}>
					<ReviewerText>{question.openQuestion}</ReviewerText>
					<TermList pairs={[{ term: "Would settle it", detail: question.wouldSettleIt }]} />
					{question.citation !== null && <CitationBlocks citations={[question.citation]} />}
				</li>
			))}
		</ul>
	);
}

// An example is evidence about the work, not praise of its author.
function ExampleFromTheWork({ example }: { example: ResultExample }) {
	return (
		<div className="flex min-w-0 flex-col gap-1.5">
			<ReviewerText>{example.note}</ReviewerText>
			<CitationBlocks citations={[example.citation]} />
		</div>
	);
}
