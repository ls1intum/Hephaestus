import { ArrowRightIcon, ExternalLinkIcon } from "lucide-react";
import type { ReactElement, ReactNode } from "react";

import type { AgentJob, ReviewLineage, ReviewLineageFeedback } from "@/api/types.gen";
import { useNow } from "@/components/common/use-now";
import {
	DEVELOPER_ANSWER_DEFS,
	developerAnswers,
} from "@/components/practice-vocabulary/developer-answer-defs";
import { feedbackLocationSentence } from "@/components/practice-vocabulary/feedback-location-defs";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { formatDayTime } from "@/lib/dates";
import { hasText } from "@/lib/text";

import {
	countOf,
	feedbackLocation,
	gapLabel,
	isSlowGap,
	type NumberedResult,
	stageCounts,
} from "./lineage-chain";
import { writtenBy } from "./lineage-story";
import { ToneIcon } from "./LineageStep";
import { sameTraceNode, type TraceNode } from "./review-trace";
import { feedbackLoss } from "./trace-bottleneck";
import { traceVerdict } from "./trace-verdict";

export interface TraceStepFactsProps {
	lineage: ReviewLineage;
	/** The step whose facts these are. */
	node: TraceNode;
	numbered: readonly NumberedResult[];
	letters: ReadonlyMap<string, string>;
	/** The record the trace was opened for, whose verdict already heads the trace. */
	here?: TraceNode;
	/** The review's own record, where the page has it: its retries. */
	job?: AgentJob;
	/**
	 * Whether every step shows its facts, as "Expand all" asks. The results' table then names
	 * each practice, so a piece of feedback does not name them again.
	 */
	everyStep?: boolean;
}

/**
 * What "Expand all" and the system info add under a step: only what the step's own line does not
 * already say. A moment, the count of results and the end say everything on their line, so
 * they add nothing and draw no box. The review adds its times, model, retries and error; the work
 * its link and merge; a result what became of it; a piece of feedback where exactly it went,
 * its timeline and the practices it was written from.
 */
export function traceStepFacts(props: TraceStepFactsProps): ReactElement | null {
	const { node, lineage, numbered, letters, here, job, everyStep = false } = props;
	// The record the trace stands on already heads it with its verdict.
	const told = sameTraceNode(here, node);
	switch (node.kind) {
		case "end":
		case "moment":
		case "results": {
			return null;
		}
		case "work": {
			return <WorkFacts lineage={lineage} />;
		}
		case "review": {
			return lineage.review === undefined ? null : <ReviewFacts lineage={lineage} job={job} />;
		}
		case "result": {
			const entry = numbered.find((candidate) => candidate.result.id === node.id);
			return entry === undefined ? null : (
				<ResultFacts lineage={lineage} entry={entry} told={told} />
			);
		}
		case "feedback": {
			const feedback = lineage.feedback.find((candidate) => candidate.id === node.id);
			return feedback === undefined ? null : (
				<FeedbackFacts
					feedback={feedback}
					lineage={lineage}
					numbered={numbered}
					letters={letters}
					told={told}
					namesSources={!everyStep}
				/>
			);
		}
	}
}

/**
 * A result: what became of it in one sentence, unless the trace stands on it and says so on
 * top, the day it was observed, and when an admin marked it incorrect.
 */
function ResultFacts({
	lineage,
	entry,
	told,
}: {
	lineage: ReviewLineage;
	entry: NumberedResult;
	told: boolean;
}) {
	const { result } = entry;
	const node: TraceNode = { kind: "result", id: result.id };
	return (
		<FactList>
			{!told && <Fact label="Outcome">{traceVerdict(lineage, node).sentence}</Fact>}
			<Fact label="Recorded">
				<DayTime at={result.observedAt} />
			</Fact>
			{result.markedIncorrectAt !== undefined && (
				<Fact label="Marked incorrect">
					<DayTime at={result.markedIncorrectAt} from={result.observedAt} />
				</Fact>
			)}
		</FactList>
	);
}

/** A label and its value, in two aligned columns. */
function Fact({ label, children }: { label: string; children: ReactNode }) {
	return (
		<>
			<dt className="text-muted-foreground">{label}</dt>
			<dd className="min-w-0 break-words text-foreground">{children}</dd>
		</>
	);
}

function FactList({ children }: { children: ReactNode }) {
	return <dl className="grid grid-cols-[5.75rem_minmax(0,1fr)] gap-x-3 gap-y-0.5">{children}</dl>;
}

function DayTime({ at, from }: { at: Date; from?: Date }) {
	const today = new Date(useNow());
	const slow = from !== undefined && isSlowGap((at.getTime() - from.getTime()) / 1000);
	return (
		<span className="inline-flex flex-wrap items-baseline gap-x-1.5">
			<time dateTime={at.toISOString()} className="tabular-nums">
				{formatDayTime(at, today)}
			</time>
			{from !== undefined && from.getTime() <= at.getTime() && (
				<span
					className={
						slow
							? "text-2xs font-medium text-warning tabular-nums"
							: "text-2xs text-muted-foreground tabular-nums"
					}
				>
					{gapLabel(from, at)}
				</span>
			)}
		</span>
	);
}

const PROVIDER_NAMES: Record<string, string> = { GITHUB: "GitHub", GITLAB: "GitLab" };

function WorkFacts({ lineage }: { lineage: ReviewLineage }) {
	const { work } = lineage;
	const pullRequest = work?.kind === "scm.pull_request";
	return (
		<FactList>
			{hasText(work?.url) && (
				<Fact label="Link">
					<a
						href={work.url}
						target="_blank"
						rel="noopener noreferrer"
						className="inline-flex items-center gap-1 text-primary underline-offset-2 hover:underline"
					>
						{`Open on ${PROVIDER_NAMES[work.provider ?? ""] ?? "its site"}`}
						<ExternalLinkIcon aria-hidden className="size-3" />
					</a>
				</Fact>
			)}
			<Fact label="Moments">
				{`${countOf(lineage.workMoments.length, "moment", "moments")} recorded on the work`}
			</Fact>
			{lineage.workMergedAt !== undefined && (
				<Fact label="Merged">
					<DayTime at={lineage.workMergedAt} />
				</Fact>
			)}
			{lineage.workMergedAt === undefined && pullRequest && <Fact label="Merged">Not yet</Fact>}
		</FactList>
	);
}

/** How far the review got, step by step: observed, written, reached the developer, answered. */
function StageLine({ lineage }: { lineage: ReviewLineage }) {
	const counts = stageCounts(lineage);
	const stages = [
		countOf(counts.results, "listed result", "listed results"),
		countOf(counts.feedback, "listed piece of feedback", "listed pieces of feedback"),
		`${counts.reached} reached the developer`,
		`${counts.answered} answered`,
	];
	return (
		<span className="flex flex-wrap items-center gap-1">
			{stages.map((stage, index) => (
				<span key={stage} className="inline-flex items-center gap-1">
					{index > 0 && <ArrowRightIcon aria-hidden className="size-3 text-muted-foreground" />}
					{stage}
				</span>
			))}
			{counts.resolvedByWork > 0 && (
				<span>{`· ${counts.resolvedByWork} resolved by the work`}</span>
			)}
		</span>
	);
}

function ReviewFacts({ lineage, job }: { lineage: ReviewLineage; job: AgentJob | undefined }) {
	const { review } = lineage;
	if (review === undefined) {
		return null;
	}
	return (
		<FactList>
			<Fact label="Asked for">
				<DayTime at={review.createdAt} />
			</Fact>
			{review.startedAt !== undefined && (
				<Fact label="Picked up">
					<DayTime at={review.startedAt} from={review.createdAt} />
				</Fact>
			)}
			{review.finishedAt !== undefined && (
				<Fact label="Finished">
					<DayTime at={review.finishedAt} from={review.startedAt ?? review.createdAt} />
				</Fact>
			)}
			{job !== undefined && job.retryCount > 0 && (
				<Fact label="Attempts">
					{`${job.retryCount + 1}, after ${countOf(job.retryCount, "retry", "retries")}`}
				</Fact>
			)}
			{hasText(review.model) && <Fact label="Model">{review.model}</Fact>}
			<Fact label="How far">
				<StageLine lineage={lineage} />
			</Fact>
		</FactList>
	);
}

/**
 * What became of a piece, said without the trace's opening words: why it was lost where it was,
 * otherwise where its path ended.
 */
function outcomeText(feedback: ReviewLineageFeedback, lineage: ReviewLineage): string {
	const loss = feedbackLoss(feedback, lineage);
	if (loss !== undefined) {
		return loss.text;
	}
	const { end } = traceVerdict(lineage, { kind: "feedback", id: feedback.id });
	return `${end.label}: ${end.reason}.`;
}

interface TimelineEntry {
	key: string;
	label: string;
	at: Date;
	from?: Date;
	body?: ReactNode;
}

/** Every dated thing that happened to a piece, in order, each with the wait since the one before. */
function feedbackTimeline(feedback: ReviewLineageFeedback): TimelineEntry[] {
	const { response } = feedback;
	const entries: TimelineEntry[] = [{ key: "composed", label: "Composed", at: feedback.createdAt }];
	if (feedback.decision !== undefined && feedback.decidedAt !== undefined) {
		entries.push({
			key: "decided",
			label: feedback.decision === "APPROVED" ? "Approved" : "Rejected",
			at: feedback.decidedAt,
		});
	}
	if (feedback.deliveredAt !== undefined) {
		entries.push({ key: "delivered", label: "Delivered", at: feedback.deliveredAt });
	}
	if (feedback.withdrawnAt !== undefined) {
		entries.push({ key: "withdrawn", label: "Withdrawn", at: feedback.withdrawnAt });
	}
	if (feedback.replacedAt !== undefined) {
		entries.push({ key: "replaced", label: "Replaced", at: feedback.replacedAt });
	}
	const answers = developerAnswers(response).filter((answer) => answer !== "RESOLVED_BY_WORK");
	if (response?.respondedAt !== undefined && answers.length > 0) {
		entries.push({
			key: "answered",
			label: "Answered",
			at: response.respondedAt,
			body: (
				<span className="inline-flex flex-wrap items-center gap-x-2">
					{answers.map((answer) => (
						<span key={answer} className="inline-flex items-center gap-1">
							<ToneIcon def={DEVELOPER_ANSWER_DEFS[answer]} />
							{DEVELOPER_ANSWER_DEFS[answer].label}
						</span>
					))}
				</span>
			),
		});
	}
	if (response?.resolvedByWorkAt !== undefined) {
		entries.push({ key: "resolved", label: "Resolved", at: response.resolvedByWorkAt });
	}
	entries.sort((a, b) => a.at.getTime() - b.at.getTime());
	return entries.map((entry, index) => ({ ...entry, from: entries[index - 1]?.at }));
}

function FeedbackFacts({
	feedback,
	lineage,
	numbered,
	letters,
	told,
	namesSources,
}: {
	feedback: ReviewLineageFeedback;
	lineage: ReviewLineage;
	numbered: readonly NumberedResult[];
	letters: ReadonlyMap<string, string>;
	told: boolean;
	namesSources: boolean;
}) {
	const location = feedbackLocation(feedback, lineage.work);
	const timeline = feedbackTimeline(feedback);
	const elsewhere = writtenBy(feedback, lineage.review);
	const sources = numbered.filter(({ result }) => feedback.practiceResultIds.includes(result.id));
	return (
		<FactList>
			<Fact label="Channel">{feedbackLocationSentence(location, lineage.work)}</Fact>
			{!told && <Fact label="Outcome">{outcomeText(feedback, lineage)}</Fact>}
			{timeline.map((entry) => (
				<Fact key={entry.key} label={entry.label}>
					<span className="inline-flex flex-wrap items-baseline gap-x-2">
						<DayTime at={entry.at} from={entry.from} />
						{entry.body}
					</span>
				</Fact>
			))}
			{elsewhere !== undefined && <Fact label="Composed by">{elsewhere}</Fact>}
			{hasText(feedback.replacesId) && (
				<Fact label="Replaces">{letters.get(feedback.replacesId) ?? "Earlier feedback"}</Fact>
			)}
			{hasText(feedback.replacedById) && (
				<Fact label="Replaced by">{letters.get(feedback.replacedById) ?? "Newer feedback"}</Fact>
			)}
			{hasText(feedback.response?.disputeExplanation) && (
				<Fact label="Their reason">
					<q className="italic">{feedback.response.disputeExplanation}</q>
				</Fact>
			)}
			{namesSources && sources.length > 0 && (
				<Fact label="Written from">
					<ul className="space-y-0.5">
						{sources.map(({ result, number }) => (
							<li key={result.id}>
								<span className="text-muted-foreground tabular-nums">{number}</span>{" "}
								{result.practiceName}
								<span className="text-muted-foreground">
									{` · ${OUTCOME_DEFS[result.outcome].label.toLowerCase()}${
										result.severity === undefined
											? ""
											: `, ${SEVERITY_DEFS[result.severity].label.toLowerCase()}`
									}`}
								</span>
							</li>
						))}
					</ul>
				</Fact>
			)}
		</FactList>
	);
}
