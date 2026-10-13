import { CopyIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "cn";

import type {
	AgentJob,
	AgentJobUsage,
	AgentJobUsageAttempt,
	DeliveryPolicyTrace as DeliveryCheck,
	ReviewRunSummary,
} from "@/api/types.gen";
import { formatTokens, JOB_TYPE_LABELS } from "@/components/admin/usage/usage-utils";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { useNow } from "@/components/common/use-now";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { Section } from "@/components/layout/Section";
import { DeliveryPolicyTrace } from "@/components/practice-trace/DeliveryPolicyTrace";
import {
	DISCOVERED_VIA_DESCRIPTIONS,
	DISCOVERED_VIA_LABELS,
} from "@/components/practice-trace/trace-format";
import { OBSERVATION_ORIGIN_DEFS } from "@/components/practice-vocabulary/observation-origin-defs";
import { REVIEW_CANCELLATION_DEFS } from "@/components/practice-vocabulary/review-cancellation-defs";
import {
	RESULT_PROCESSING_DEFS,
	reviewStatusDef,
} from "@/components/practice-vocabulary/review-status-defs";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { DrawerBody, DrawerFooter } from "@/components/ui/drawer";
import { reviewedWorkName } from "@/lib/artifact-kinds";
import { copyToClipboard } from "@/lib/clipboard";
import { asDate, durationText } from "@/lib/dates";
import { formatCostUsd } from "@/lib/money";
import { hasText } from "@/lib/text";

import {
	holdReasonCopy,
	isCancellable,
	isResultProcessingRetryable,
	jobPhases,
	jobWait,
} from "./job-utils";
import { LevelBodySkeleton } from "./LevelBodySkeleton";
import {
	type SheetEvent,
	SheetHistory,
	SheetSection,
	SheetStatusLine,
	SheetTechnical,
	type TechnicalFact,
} from "./RecordSheet";
import { RequestReviewAction } from "./RequestReviewAction";
import { revisionLabel, subjectLabel } from "./review-format";
import { reviewLevel } from "./review-levels";
import {
	endedEarly,
	type PracticeGroupLook,
	ReviewPractices,
	type RunTraceState,
	startingMoment,
} from "./review-run-sections";
import { ReviewArtifactLink } from "./ReviewArtifact";
import { ReviewStatusMark } from "./ReviewBadges";
import { ReviewDayTime } from "./ReviewDayTime";
import { ReviewFact, ReviewFactGrid } from "./ReviewFactGrid";
import { ReviewLevelHeader } from "./ReviewLevelHeader";
import { ReviewRunActions } from "./ReviewRunActions";

export interface ReviewLevelProps {
	nested?: boolean;
	path: LevelPath;
	job: PanelState<{ job: AgentJob }>;
	/** The review's row on the Reviews list, for its counts, its developers and its place on the work. */
	summary: ReviewRunSummary | undefined;
	trace: RunTraceState;
	/** The workspace's groups, for the colour each practice's group is named in. */
	groups?: readonly PracticeGroupLook[] | undefined;
	/** The delivery checks the review ran for itself rather than for one piece of feedback. */
	deliveryChecks?: PanelState<{ checks: DeliveryCheck[] }>;
	/** What the review spent on the model, attempt by attempt. */
	usage?: PanelState<{ usage: AgentJobUsage }>;
	onCancel: () => void;
	cancelPending: boolean;
	onRetryResultProcessing: () => void;
	retryResultProcessingPending: boolean;
	/** Asks for a new review of the same work; omitted where the work cannot be reviewed on demand. */
	onRequestReview?: () => void;
	requestingReview?: boolean;
	/** Why no review can be asked for now, such as another review of the work queued or running. */
	requestBlocked?: string;
	/** Why the last ask started nothing, drawn by the route. */
	requestRefusal?: ReactNode;
}

const REVIEW_OUTCOME_LABELS: Record<AgentJob["reviewOutcome"], string> = {
	REVIEWED: "Reviewed",
	INSUFFICIENT_EVIDENCE: "Nothing assessed: the evidence it needed was missing or unreadable",
	COALESCED: "Coalesced: an earlier review had answered every practice on the same code",
	SUPERSEDED: "Replaced: a newer review of the same work carries its practices",
};

/** What the history says of where the review stands now, where that is not plain from its events. */
function reviewNow(job: AgentJob, partial: boolean): string | undefined {
	if (job.reviewOutcome === "SUPERSEDED") {
		return "A newer review of the same work replaced it while it waited. That review carries its practices and may still be waiting or running.";
	}
	return partial
		? "It ended early, so its observations and feedback may be only part of what it would have found."
		: undefined;
}

/** "9 min" between two times, or undefined while either is missing. */
function between(from: Date | undefined, to: Date | undefined): string | undefined {
	return from === undefined || to === undefined
		? undefined
		: durationText((to.getTime() - from.getTime()) / 1000);
}

/**
 * One review's sheet, and only the review, in the order every sheet has: the header's status line
 * (status, how it ended when not plainly, how long it ran, when it finished), what it is, its
 * history from the ask to the first posted comment, every practice with its state and the review's
 * own delivery checks, and the technical facts. Its actions are the footer.
 *
 * It lists no other record and links to none: its observations, its feedback, when its feedback was
 * first read and the other moments on the work are its trace or the work's sheet. The work links
 * out to its provider.
 */
export function ReviewLevel({
	nested,
	path,
	job: state,
	summary,
	trace,
	groups,
	deliveryChecks,
	usage,
	onCancel,
	cancelPending,
	onRetryResultProcessing,
	retryResultProcessingPending,
	onRequestReview,
	requestingReview = false,
	requestBlocked,
	requestRefusal,
}: ReviewLevelProps) {
	const now = useNow();
	const job = state.status === "ready" ? state.job : undefined;
	const header = (
		<ReviewLevelHeader
			nested={nested}
			path={path}
			kind="review"
			loading={state.status === "loading"}
			chips={job && <ReviewStatus job={job} />}
			title={job?.target.title}
			description={job && <ReviewArtifactLink reviewedWork={job.target.reviewedWork} />}
		/>
	);
	if (job === undefined) {
		return (
			<>
				{header}
				<DrawerBody className="pt-2">
					{state.status === "error" ? (
						<QueryErrorAlert
							error={state.error}
							title="We could not load this review"
							onRetry={state.onRetry}
						/>
					) : (
						<LevelBodySkeleton />
					)}
				</DrawerBody>
			</>
		);
	}
	const cancellable = isCancellable(job.status);
	const retryable = isResultProcessingRetryable(job);
	const wait = jobWait(job, now);
	const hold = wait?.kind === "hold" ? holdReasonCopy(wait.reason) : undefined;
	const partial =
		endedEarly(job) &&
		summary !== undefined &&
		Object.values(summary.observations).some((count) => count > 0);
	return (
		<>
			{header}
			<DrawerBody className="flex flex-col gap-8 pt-2">
				{requestRefusal}
				{/* The one alert: a hold is happening now, and it says what ends it. */}
				{hold && (
					<Alert variant="warning">
						<AlertTitle>{hold.label}</AlertTitle>
						<AlertDescription>{hold.detail}</AlertDescription>
					</Alert>
				)}
				<SheetSection part="what">
					<ReviewWhat job={job} summary={summary} trace={trace} />
				</SheetSection>
				<SheetSection part="state">
					<SheetHistory events={reviewHistory(job)} now={reviewNow(job, partial)} />
					<ReviewPhases job={job} />
					{job.reviewOutcome === "SUPERSEDED" &&
						hasText(job.coveringJobId) && (
							// The one link a sheet makes to another record, by decision: a replaced review has no
							// practices of its own, so the review that carries them is the only next step, and
							// its ID alone under Technical left the reader to find it by hand.
							<DetailStackLink
								entry={reviewLevel(job.coveringJobId)}
								single
								className={cn(buttonVariants({ variant: "link", size: "inline" }), "mt-2 text-sm")}
							>
								Open the newer review
							</DetailStackLink>
						)}
				</SheetSection>
				<SheetSection part="details" title="Every practice">
					<ReviewPractices job={job} trace={trace} summary={summary} groups={groups} />
				</SheetSection>
				{deliveryChecks !== undefined && <ReviewDeliveryChecks state={deliveryChecks} />}
				<SheetTechnical facts={technicalFacts(job, usage)}>
					{usage?.status === "error" && (
						<QueryErrorAlert
							error={usage.error}
							title="We could not load what the review cost"
							onRetry={usage.onRetry}
						/>
					)}
					{usage?.status === "ready" && usage.usage.attempts.length > 1 && (
						<UsageAttempts attempts={usage.usage.attempts} />
					)}
					<GeneratedPaths job={job} />
				</SheetTechnical>
			</DrawerBody>
			{(cancellable || retryable || onRequestReview !== undefined) && (
				<DrawerFooter>
					{(cancellable || retryable) && (
						<ReviewRunActions
							job={job}
							isCancelling={cancelPending}
							isRetrying={retryResultProcessingPending}
							onCancel={onCancel}
							onRetry={onRetryResultProcessing}
						/>
					)}
					{/* While this review still runs, another would only be coalesced into it. */}
					{!cancellable && onRequestReview !== undefined && (
						<RequestReviewAction
							label="Request another review"
							request={{
								work:
									job.target.reviewedWork === undefined
										? "this work"
										: reviewedWorkName(job.target.reviewedWork),
								onRequest: onRequestReview,
								requesting: requestingReview,
								blocked: requestBlocked,
							}}
						/>
					)}
				</DrawerFooter>
			)}
		</>
	);
}

/**
 * The status line: its status; how it ended, where that is not plain (nothing assessed, coalesced,
 * the result processing not done); how long it ran; and the time that matters for where it is.
 */
function ReviewStatus({ job }: { job: AgentJob }) {
	let flag: ReactNode;
	if (job.deliveryStatus !== undefined && job.deliveryStatus !== "DELIVERED") {
		flag = <ReviewStatusMark def={RESULT_PROCESSING_DEFS[job.deliveryStatus]} />;
	} else if (job.status === "COMPLETED" && job.reviewOutcome === "INSUFFICIENT_EVIDENCE") {
		flag = "Nothing assessed";
	} else if (job.status === "COMPLETED" && job.reviewOutcome === "COALESCED") {
		flag = "Coalesced";
	}
	let at: { label: string; date: Date } = { label: "asked for", date: job.createdAt };
	if (job.completedAt !== undefined) {
		at = { label: "finished", date: job.completedAt };
	} else if (job.startedAt !== undefined) {
		at = { label: "started", date: job.startedAt };
	}
	const ran = between(job.startedAt, job.completedAt);
	return (
		<SheetStatusLine
			status={<ReviewStatusMark def={reviewStatusDef(job)} />}
			flag={flag}
			figure={ran === undefined ? undefined : `ran ${ran}`}
			at={at}
		/>
	);
}

function ReviewWhat({
	job,
	summary,
	trace,
}: {
	job: AgentJob;
	summary: ReviewRunSummary | undefined;
	trace: RunTraceState;
}) {
	const moment = startingMoment(trace, job.id);
	let startedBy: ReactNode = (
		<span className="text-muted-foreground">
			{trace.status === "loading" ? "Loading…" : "No moment on the work is recorded"}
		</span>
	);
	if (moment !== undefined) {
		startedBy = (
			<span className="space-y-0.5">
				<span className="block">{moment.displayName}</span>
				<span className="block text-muted-foreground">
					<ReviewDayTime at={moment.occurredAt} /> ·{" "}
					<span title={DISCOVERED_VIA_DESCRIPTIONS[moment.discoveredVia]}>
						{DISCOVERED_VIA_LABELS[moment.discoveredVia]}
					</span>
				</span>
			</span>
		);
	}
	return (
		<ReviewFactGrid>
			<ReviewFact label="Reviewed as">{JOB_TYPE_LABELS[job.jobType]}</ReviewFact>
			<ReviewFact label="Started by">{startedBy}</ReviewFact>
			{summary?.origin !== undefined && (
				<ReviewFact label="Origin">
					<span title={OBSERVATION_ORIGIN_DEFS[summary.origin].description}>
						{OBSERVATION_ORIGIN_DEFS[summary.origin].label}
					</span>
				</ReviewFact>
			)}
			{moment !== undefined && hasText(moment.revision) && (
				<ReviewFact label="Reviewed revision">
					<code className="break-all">{revisionLabel(moment.revision)}</code>
				</ReviewFact>
			)}
			{summary !== undefined && summary.developers.length > 0 && (
				<ReviewFact label={summary.developers.length === 1 ? "Developer" : "Developers"}>
					{summary.developers.map((developer) => subjectLabel(developer)).join(", ")}
				</ReviewFact>
			)}
			{summary?.positionOnWork !== undefined && (
				<ReviewFact label="On this work">Review {summary.positionOnWork}</ReviewFact>
			)}
			{job.status === "COMPLETED" && (
				<ReviewFact label="Outcome">{REVIEW_OUTCOME_LABELS[job.reviewOutcome]}</ReviewFact>
			)}
		</ReviewFactGrid>
	);
}

/**
 * The current attempt's wall-clock phases between the boundaries the job records: not model,
 * provider or tool timing. A phase still running is in progress, one that has not begun is not
 * started, and one whose boundary is missing is not recorded, never a zero.
 */
function ReviewPhases({ job }: { job: AgentJob }) {
	const phases = jobPhases(job);
	return (
		<div className="mt-4">
			<ReviewFactGrid>
				<ReviewFact label={phases.retried ? "Request to this attempt" : "Wait to start"}>
					{phases.wait}
					{phases.retried && (
						<span className="text-muted-foreground">
							{" "}
							(includes earlier attempts and retry waits)
						</span>
					)}
				</ReviewFact>
				<ReviewFact label="Preparation">{phases.preparation}</ReviewFact>
				<ReviewFact label="Execution">{phases.execution}</ReviewFact>
			</ReviewFactGrid>
		</div>
	);
}

/**
 * From the ask to the first posted comment, each step once with its time and how long the step
 * before it took. The merge is the work's; only how it fell against this review is said here.
 */
export function reviewHistory(job: AgentJob): SheetEvent[] {
	const events: SheetEvent[] = [{ key: "asked", at: job.createdAt, title: "Asked for" }];
	if (hasText(job.holdReason) && job.availableAt.getTime() > job.createdAt.getTime()) {
		events.push({
			key: "held",
			at: job.availableAt,
			title: "Held until",
			detail: holdReasonCopy(job.holdReason).label,
		});
	}
	const queued = between(job.createdAt, job.startedAt);
	if (job.startedAt !== undefined) {
		events.push({
			key: "started",
			at: job.startedAt,
			title: "Started",
			detail: queued === undefined ? undefined : `after ${queued} in the queue`,
		});
	}
	const preparing = between(job.startedAt, job.executionStartedAt);
	if (job.executionStartedAt !== undefined) {
		events.push({
			key: "model",
			at: job.executionStartedAt,
			title: "With the model",
			detail: preparing === undefined ? undefined : `after ${preparing} preparing`,
		});
	}
	const publicReviewAcceptedAt = asDate(job.publicReviewAcceptedAt);
	if (publicReviewAcceptedAt !== undefined) {
		events.push({
			key: "public-accepted",
			at: publicReviewAcceptedAt,
			title: "Review on the work accepted",
			detail:
				"The public decision is recorded. Each piece of feedback records its own delivery state.",
		});
	}
	if (job.completedAt !== undefined) {
		const withModel = between(job.executionStartedAt, job.completedAt);
		const cancellation =
			job.status === "CANCELLED" && job.cancellationReason !== undefined
				? REVIEW_CANCELLATION_DEFS[job.cancellationReason]
				: undefined;
		events.push({
			key: "ended",
			at: job.completedAt,
			title: reviewStatusDef(job).label,
			detail: (
				<>
					{withModel !== undefined && (
						<span className="block">after {withModel} with the model</span>
					)}
					{cancellation !== undefined && (
						<span className="block">
							Reason: {cancellation.label}. {cancellation.description}
						</span>
					)}
					{hasText(job.errorMessage) && (
						<pre className="mt-1 max-h-48 overflow-auto rounded-md bg-muted/50 p-2 text-xs break-words whitespace-pre-wrap">
							{job.errorMessage}
						</pre>
					)}
				</>
			),
		});
	}
	const posted = between(job.completedAt, job.firstFeedbackPostedAt);
	if (job.firstFeedbackPostedAt !== undefined) {
		let detail: string | undefined;
		if (job.completedAt !== undefined && job.firstFeedbackPostedAt < job.completedAt) {
			detail = "while the review was still running";
		} else if (posted !== undefined) {
			detail = `${posted} after it finished`;
		}
		events.push({
			key: "posted",
			at: job.firstFeedbackPostedAt,
			title: "First comment posted",
			detail,
		});
	}
	const merged = job.workMergedAt;
	const mergedBefore = between(merged, job.completedAt);
	if (
		merged !== undefined &&
		job.completedAt !== undefined &&
		mergedBefore !== undefined &&
		merged < job.completedAt
	) {
		events.push({
			key: "merge",
			at: undefined,
			title: "The work was merged before this review finished",
			detail: `${mergedBefore} before its end. The merge itself is on the work.`,
		});
	}
	return events;
}

/** "$0.04", or why there is no total: no usage recorded, or an attempt has no price. */
function costText(usage: PanelState<{ usage: AgentJobUsage }> | undefined): string {
	if (usage === undefined || usage.status === "error") {
		return "—";
	}
	if (usage.status === "loading") {
		return "Loading…";
	}
	const { attempts, totalCostUsd, unpricedAttempts } = usage.usage;
	if (attempts.length === 0) {
		return "No usage recorded";
	}
	if (totalCostUsd !== undefined) {
		return attempts.length > 1
			? `${formatCostUsd(totalCostUsd)} over ${attempts.length} attempts`
			: formatCostUsd(totalCostUsd);
	}
	return unpricedAttempts === 1
		? "Not known: 1 attempt has no price"
		: `Not known: ${unpricedAttempts} attempts have no price`;
}

/** One attempt's cost, or that its model had no price when it ran. */
function attemptCostText(attempt: AgentJobUsageAttempt): string {
	if (attempt.costUsd === undefined) {
		return "no price known";
	}
	return attempt.pricingState === "NO_CHARGE" ? "no charge" : formatCostUsd(attempt.costUsd);
}

/** Each attempt that reached the model, where a retry made more than one. */
function UsageAttempts({ attempts }: { attempts: AgentJobUsageAttempt[] }) {
	return (
		<Section level={4} size="sm" title="Attempts" className="space-y-2">
			<ol className="space-y-1 text-sm">
				{attempts.map((attempt) => (
					<li key={attempt.attempt} className="flex flex-wrap gap-x-2 tabular-nums">
						<span className="font-medium">Attempt {attempt.attempt + 1}</span>
						<span>{attemptCostText(attempt)}</span>
						<span className="text-muted-foreground">
							{formatTokens(attempt.inputTokens)} read · {formatTokens(attempt.outputTokens)}{" "}
							written · {formatTokens(attempt.totalCalls)} calls
							{hasText(attempt.model) && ` · ${attempt.model}`}
						</span>
					</li>
				))}
			</ol>
		</Section>
	);
}

function technicalFacts(
	job: AgentJob,
	usage: PanelState<{ usage: AgentJobUsage }> | undefined,
): TechnicalFact[] {
	const model = [job.model, job.llmModel, job.llmModelVersion].filter((part) => hasText(part));
	const facts: TechnicalFact[] = [
		{ label: "Review ID", value: <code>{job.id}</code>, copy: job.id },
		{ label: "Cost", value: costText(usage) },
		{ label: "Model", value: model.length > 0 ? model.join(" · ") : "—" },
		{ label: "Model calls", value: formatTokens(job.llmTotalCalls) },
		{ label: "Tokens read", value: formatTokens(job.llmTotalInputTokens) },
		{
			label: "Tokens written",
			value:
				job.llmTotalReasoningTokens != null && job.llmTotalReasoningTokens > 0
					? `${formatTokens(job.llmTotalOutputTokens)} (${formatTokens(job.llmTotalReasoningTokens)} of it reasoning)`
					: formatTokens(job.llmTotalOutputTokens),
		},
		{
			label: "Cache tokens",
			value: `${formatTokens(job.llmCacheReadTokens)} read · ${formatTokens(job.llmCacheWriteTokens)} written`,
		},
		{ label: "Retries", value: String(job.retryCount) },
		{ label: "Exit code", value: job.exitCode === undefined ? "—" : String(job.exitCode) },
	];
	if (hasText(job.traceId)) {
		facts.push({ label: "Trace ID", value: <code>{job.traceId}</code>, copy: job.traceId });
	}
	if (job.reviewOutcome === "SUPERSEDED" && hasText(job.coveringJobId)) {
		facts.push({
			label: "Newer review ID",
			value: <code>{job.coveringJobId}</code>,
			copy: job.coveringJobId,
		});
	}
	if (hasText(job.deliveryCommentId)) {
		facts.push({
			label: "Summary comment",
			value: <code>{job.deliveryCommentId}</code>,
			copy: job.deliveryCommentId,
		});
	}
	return facts;
}

/** The paths the review kept as generated output, and the copy of its configuration. */
function GeneratedPaths({ job }: { job: AgentJob }) {
	let paths: ReactNode = null;
	if (job.generatedPaths) {
		paths = (
			<Section
				level={4}
				size="sm"
				title="Generated paths"
				description="Kept as generated output, not judged as hand-written work."
				className="space-y-2"
			>
				<p className="break-all">
					Patterns:{" "}
					{job.generatedPaths.patterns.length > 0
						? job.generatedPaths.patterns.join(", ")
						: "None configured"}
				</p>
				{job.generatedPaths.paths.length > 0 ? (
					<ul className="list-inside list-disc">
						{job.generatedPaths.paths.map((path) => (
							<li key={path}>
								<code className="break-all">{path}</code>
							</li>
						))}
					</ul>
				) : (
					<p className="text-muted-foreground">No changed paths matched.</p>
				)}
			</Section>
		);
	} else if (job.jobType === "PULL_REQUEST_REVIEW") {
		paths = (
			<p className="text-muted-foreground">Generated paths were not recorded for this review.</p>
		);
	}
	return (
		<div className="space-y-3 text-sm">
			{paths}
			<Button
				variant="outline"
				size="sm"
				onClick={() =>
					copyToClipboard(JSON.stringify(job.configSnapshot, null, 2), "Configuration copied")
				}
			>
				<CopyIcon aria-hidden />
				Copy configuration
			</Button>
		</div>
	);
}

/** The checks the review ran for itself; a piece of feedback shows its own on its sheet. */
function ReviewDeliveryChecks({ state }: { state: PanelState<{ checks: DeliveryCheck[] }> }) {
	if (state.status === "ready" && state.checks.length === 0) {
		return null;
	}
	return (
		<Section level={3} title="Delivery checks of the review" className="min-w-0">
			{state.status === "loading" && <LevelBodySkeleton />}
			{state.status === "error" && (
				<QueryErrorAlert
					error={state.error}
					title="We could not load the delivery checks"
					onRetry={state.onRetry}
				/>
			)}
			{state.status === "ready" && <DeliveryPolicyTrace evaluations={state.checks} />}
		</Section>
	);
}
