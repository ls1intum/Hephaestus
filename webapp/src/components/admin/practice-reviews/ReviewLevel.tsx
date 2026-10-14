import { Link } from "@tanstack/react-router";
import type { ReactNode } from "react";

import type {
	FeedbackCheck,
	ReviewDetail,
	ReviewJob,
	ReviewPractice,
	ReviewUsage,
	ReviewUsageAttempt,
} from "@/api/types.gen";
import { formatTokens } from "@/components/admin/usage/usage-utils";
import { accountName } from "@/components/admin/users/account-labels";
import { InlineLink } from "@/components/common/InlineLink";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { Section } from "@/components/layout/Section";
import { FeedbackChecks } from "@/components/practice-trace/FeedbackChecks";
import { PracticeScriptTraceLine } from "@/components/practice-trace/PracticeScriptTraceLine";
import { FOUND_BY_DESCRIPTIONS, FOUND_BY_LABELS } from "@/components/practice-trace/trace-format";
import { REVIEW_ORIGIN_DEFS } from "@/components/practice-vocabulary/review-origin-defs";
import {
	isActiveReview,
	REVIEW_JOB_PROCESSING_DEFS,
	REVIEW_JOB_STATUS_DEFS,
	REVIEW_PRACTICE_STATUS_DEFS,
	REVIEW_STATUS_DEFS,
	REVIEW_STOP_REASON_SENTENCES,
} from "@/components/practice-vocabulary/review-status-defs";
import { buttonVariants } from "@/components/ui/button";
import { DrawerBody, DrawerFooter } from "@/components/ui/drawer";
import { durationText } from "@/lib/dates";
import { itemName } from "@/lib/item-kinds";
import { formatCostUsd } from "@/lib/money";
import { hasText } from "@/lib/text";

import { jobPhases } from "./job-utils";
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
import { resultSlots } from "./review-outcomes";
import { ReviewActions } from "./ReviewActions";
import { ReviewStatusMark } from "./ReviewBadges";
import { ReviewDayTime } from "./ReviewDayTime";
import { ReviewFact, ReviewFactGrid } from "./ReviewFactGrid";
import { ReviewItemLink } from "./ReviewItem";
import { ReviewLevelHeader } from "./ReviewLevelHeader";
import { ReviewPracticeScriptTable } from "./ReviewPracticeScriptTable";

export interface ReviewLevelProps {
	workspaceSlug: string;
	nested?: boolean;
	path: LevelPath;
	review: PanelState<{ review: ReviewDetail }>;
	eventLabel: (name: string) => string;
	feedbackChecks?: PanelState<{ checks: FeedbackCheck[] }>;
	onCancel: () => void;
	cancelPending: boolean;
	/** Asks for a new review of the same work; omitted where the work cannot be reviewed on demand. */
	onRequestReview?: () => void;
	requestingReview?: boolean;
	/** Why no review can be asked for now, such as another review of the work queued or running. */
	requestBlocked?: string;
	/** Why the last ask started nothing, drawn by the route. */
	requestRefusal?: ReactNode;
}

const KIND_LABELS: Record<ReviewDetail["kind"], string> = {
	ITEM: "Review of the work",
	SCAN: "Scan",
};

export const RUNTIME_EXPIRED = "Execution details are no longer available.";

/** "9 min" between two times, or undefined while either is missing. */
function between(from: Date | null | undefined, to: Date | null | undefined): string | undefined {
	return from === null || from === undefined || to === null || to === undefined
		? undefined
		: durationText((to.getTime() - from.getTime()) / 1000);
}

function firstStart(review: ReviewDetail): Date | undefined {
	let earliest: Date | undefined;
	for (const { startedAt } of [...(review.runtime?.jobs ?? []), ...review.stageTiming]) {
		if (startedAt !== null && (earliest === undefined || startedAt < earliest)) {
			earliest = startedAt;
		}
	}
	return earliest;
}

function reviewNow(review: ReviewDetail): string | undefined {
	if (review.status === "PARTIAL") {
		return "It ended early, so its results and feedback cover only the work it reached.";
	}
	if (review.status === "FAILED" || review.status === "CANCELLED") {
		return "Results it recorded before it stopped still stand.";
	}
	return undefined;
}

export function ReviewLevel({
	workspaceSlug,
	nested,
	path,
	review: state,
	eventLabel,
	feedbackChecks,
	onCancel,
	cancelPending,
	onRequestReview,
	requestingReview = false,
	requestBlocked,
	requestRefusal,
}: ReviewLevelProps) {
	const review = state.status === "ready" ? state.review : undefined;
	const header = (
		<ReviewLevelHeader
			nested={nested}
			path={path}
			kind="review"
			loading={state.status === "loading"}
			chips={review && <ReviewStatusLine review={review} />}
			title={review && (review.item.title ?? itemName(review.item))}
			description={review && <ReviewItemLink item={review.item} />}
		/>
	);
	if (review === undefined) {
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
	const cancellable = isActiveReview(review.status);
	const scripted = review.practices.filter((practice) => practice.script !== null);
	return (
		<>
			{header}
			<DrawerBody className="flex flex-col gap-8 pt-2">
				{requestRefusal}
				<SheetSection part="what">
					<ReviewWhat workspaceSlug={workspaceSlug} review={review} eventLabel={eventLabel} />
				</SheetSection>
				<SheetSection part="state">
					<ReviewHistory review={review} />
				</SheetSection>
				<SheetSection part="details" title="Every practice">
					<ReviewPracticeList workspaceSlug={workspaceSlug} review={review} />
				</SheetSection>
				{feedbackChecks !== undefined && <ReviewFeedbackChecks state={feedbackChecks} />}
				{scripted.length > 0 && (
					<SheetSection
						part="details"
						title="Practice scripts"
						description="How the program of each practice ran in this review."
					>
						<ReviewPracticeScriptTable workspaceSlug={workspaceSlug} practices={scripted} />
						<p className="text-xs text-muted-foreground">
							Spend is on{" "}
							<InlineLink
								render={<Link to="/w/$workspaceSlug/admin/usage" params={{ workspaceSlug }} />}
							>
								AI usage
							</InlineLink>
							.
						</p>
					</SheetSection>
				)}
				<SheetTechnical facts={technicalFacts(review)}>
					{review.usage.attempts.length > 1 && <UsageAttempts attempts={review.usage.attempts} />}
					<ReviewJobs jobs={review.runtime?.jobs} />
					<GeneratedPaths review={review} />
				</SheetTechnical>
			</DrawerBody>
			{(cancellable || onRequestReview !== undefined) && (
				<DrawerFooter>
					{cancellable && <ReviewActions isCancelling={cancelPending} onCancel={onCancel} />}
					{/* While this review still runs, another would only be coalesced into it. */}
					{!cancellable && onRequestReview !== undefined && (
						<RequestReviewAction
							label="Request another review"
							request={{
								work: itemName(review.item),
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

function ReviewStatusLine({ review }: { review: ReviewDetail }) {
	const started = firstStart(review);
	let at: { label: string; date: Date } = { label: "asked for", date: review.createdAt };
	if (review.completedAt !== null) {
		at = { label: "finished", date: review.completedAt };
	} else if (started !== undefined) {
		at = { label: "started", date: started };
	}
	const ran = between(started, review.completedAt);
	return (
		<SheetStatusLine
			status={<ReviewStatusMark def={REVIEW_STATUS_DEFS[review.status]} />}
			figure={ran === undefined ? undefined : `ran ${ran}`}
			at={at}
		/>
	);
}

function ReviewWhat({
	workspaceSlug,
	review,
	eventLabel,
}: {
	workspaceSlug: string;
	review: ReviewDetail;
	eventLabel: (name: string) => string;
}) {
	const origin = REVIEW_ORIGIN_DEFS[review.origin];
	const event = review.startedBy;
	return (
		<ReviewFactGrid>
			<ReviewFact label="Kind">{KIND_LABELS[review.kind]}</ReviewFact>
			<ReviewFact label="Started by">
				<span className="space-y-0.5">
					<span className="block" title={origin.description}>
						{origin.label}
						{review.requestedBy !== null && ` by ${accountName(review.requestedBy)}`}
					</span>
					{event !== null && (
						<span className="block text-muted-foreground">
							{eventLabel(event.name)} · <ReviewDayTime at={event.occurredAt} /> ·{" "}
							<span title={FOUND_BY_DESCRIPTIONS[event.foundBy]}>
								{FOUND_BY_LABELS[event.foundBy]}
							</span>
						</span>
					)}
				</span>
			</ReviewFact>
			{hasText(review.itemVersion) && (
				<ReviewFact label="Reviewed version">
					<code className="break-all">{review.itemVersion}</code>
				</ReviewFact>
			)}
			{review.kind === "SCAN" && (
				<ReviewFact label="Report">
					<Link
						to="/w/$workspaceSlug/admin/practices/scans/$scanId"
						params={{ workspaceSlug, scanId: review.id }}
						className={buttonVariants({ variant: "link", size: "inline" })}
					>
						Open the scan report
					</Link>
				</ReviewFact>
			)}
		</ReviewFactGrid>
	);
}

function ReviewHistory({ review }: { review: ReviewDetail }) {
	return <SheetHistory events={reviewHistory(review)} now={reviewNow(review)} />;
}

export function reviewHistory(review: ReviewDetail): SheetEvent[] {
	const events: SheetEvent[] = [
		{
			key: "asked",
			at: review.createdAt,
			title: "Asked for",
			by: review.requestedBy === null ? undefined : accountName(review.requestedBy),
		},
	];
	const started = firstStart(review);
	const queued = between(review.createdAt, started);
	if (started !== undefined) {
		events.push({
			key: "started",
			at: started,
			title: "Started",
			detail: queued === undefined ? undefined : `after ${queued} in the queue`,
		});
	}
	for (const job of review.runtime?.jobs ?? []) {
		if (job.attempt > 1) {
			events.push({
				key: `retry-${job.id}`,
				at: job.availableAt,
				title: "Retry due",
				detail: `Attempt ${job.attempt} waits until then after an earlier attempt stopped.`,
			});
		}
	}
	if (review.completedAt !== null) {
		const ran = between(started, review.completedAt);
		events.push({
			key: "ended",
			at: review.completedAt,
			title: REVIEW_STATUS_DEFS[review.status].label,
			detail: (
				<>
					{ran !== undefined && <span className="block">after {ran} running</span>}
					{review.stopReason !== null && (
						<span className="block">{REVIEW_STOP_REASON_SENTENCES[review.stopReason]}</span>
					)}
				</>
			),
		});
	}
	return events;
}

function resultsLine(practice: ReviewPractice): string {
	const counted = resultSlots(practice.results).filter((slot) => slot.count > 0);
	return counted.length === 0
		? "No results"
		: counted.map((slot) => `${slot.count} ${slot.label}`).join(", ");
}

function itemCountsLine(practice: ReviewPractice): string {
	const { reviewed, reused, withheld, notReviewed, listed } = practice.itemCounts;
	const counts: { count: number; label: string }[] = [
		{ count: reviewed, label: "reviewed" },
		{ count: reused, label: "reused" },
		{ count: withheld, label: "withheld" },
		{ count: notReviewed, label: "not reviewed" },
		{ count: listed, label: "listed" },
	];
	return counts
		.filter(({ count }) => count > 0)
		.map(({ count, label }) => `${count} ${label}`)
		.join(", ");
}

function practiceReason(practice: ReviewPractice): string {
	if (practice.status === "DONE") {
		return resultsLine(practice);
	}
	return hasText(practice.reason)
		? practice.reason
		: REVIEW_PRACTICE_STATUS_DEFS[practice.status].description;
}

function ReviewPracticeList({
	workspaceSlug,
	review,
}: {
	workspaceSlug: string;
	review: ReviewDetail;
}) {
	if (review.practices.length === 0) {
		return (
			<p className="text-sm text-muted-foreground">
				{isActiveReview(review.status)
					? "Practices appear here as the review reaches them."
					: "This review reached no practice, so there is nothing to list here."}
			</p>
		);
	}
	return (
		<ul aria-label="Every practice in this review" className="divide-y rounded-xl border">
			{review.practices.map((practice) => (
				<li
					key={practice.practiceSlug}
					data-practice-status={practice.status}
					className="@container px-3 py-2.5 text-sm"
				>
					<div className="grid min-w-0 items-start gap-x-4 gap-y-1.5 @lg:grid-cols-[9rem_minmax(0,14rem)_minmax(0,1fr)]">
						<span>
							<StatusBadge def={REVIEW_PRACTICE_STATUS_DEFS[practice.status]} />
						</span>
						<span className="font-medium break-words">{practice.practiceName}</span>
						<span className="flex min-w-0 flex-col gap-1 text-pretty text-muted-foreground">
							<span>{practiceReason(practice)}</span>
							{review.kind === "SCAN" && itemCountsLine(practice) !== "" && (
								<span className="text-xs">Files: {itemCountsLine(practice)}</span>
							)}
							{practice.script !== null && (
								<span className="text-xs">
									<PracticeScriptTraceLine
										workspaceSlug={workspaceSlug}
										practiceSlug={practice.practiceSlug}
										practiceName={practice.practiceName}
										reviewId={review.id}
										run={practice.script}
										details={false}
									/>
								</span>
							)}
						</span>
					</div>
				</li>
			))}
		</ul>
	);
}

function costText(usage: ReviewUsage): string {
	const { attempts, costUsd, priced } = usage;
	if (attempts.length === 0) {
		return "No usage recorded";
	}
	if (priced && costUsd !== null) {
		const cost = formatCostUsd(Number(costUsd));
		return attempts.length > 1 ? `${cost} over ${attempts.length} attempts` : cost;
	}
	const unpriced = attempts.filter((attempt) => attempt.pricingState === "UNPRICED").length;
	return unpriced === 1
		? "Not known: 1 attempt has no price"
		: `Not known: ${unpriced} attempts have no price`;
}

function attemptCostText(attempt: ReviewUsageAttempt): string {
	if (attempt.pricingState === "NO_CHARGE") {
		return "no charge";
	}
	return attempt.costUsd === null ? "no price known" : formatCostUsd(Number(attempt.costUsd));
}

function UsageAttempts({ attempts }: { attempts: ReviewUsageAttempt[] }) {
	return (
		<Section level={4} size="sm" title="Attempts" className="space-y-2">
			<ol className="space-y-1 text-sm">
				{attempts.map((attempt) => (
					<li
						key={`${attempt.jobId}-${attempt.attempt}`}
						className="flex flex-wrap gap-x-2 tabular-nums"
					>
						<span className="font-medium">Attempt {attempt.attempt}</span>
						<span>{attemptCostText(attempt)}</span>
						<span className="text-muted-foreground">
							{formatTokens(attempt.inputTokens)} read · {formatTokens(attempt.outputTokens)}{" "}
							written · {formatTokens(attempt.calls)} calls
						</span>
					</li>
				))}
			</ol>
		</Section>
	);
}

function ReviewJobs({ jobs }: { jobs: ReviewJob[] | undefined }) {
	return (
		<Section level={4} size="sm" title="Execution" className="space-y-2">
			{jobs === undefined || jobs.length === 0 ? (
				<p className="text-sm text-muted-foreground">{RUNTIME_EXPIRED}</p>
			) : (
				<ol aria-label="Execution" className="space-y-3 text-sm">
					{jobs.map((job, index) => (
						<ReviewJobEntry
							key={job.id}
							job={job}
							position={jobs.length > 1 ? `${index + 1} of ${jobs.length} · ` : ""}
						/>
					))}
				</ol>
			)}
		</Section>
	);
}

function ReviewJobEntry({ job, position }: { job: ReviewJob; position: string }) {
	const phases = jobPhases(job);
	return (
		<li className="min-w-0 space-y-1">
			<p className="flex flex-wrap items-center gap-2">
				<span className="font-medium">
					{position}Attempt {job.attempt}
				</span>
				<StatusBadge def={REVIEW_JOB_STATUS_DEFS[job.status]} />
				<StatusBadge def={REVIEW_JOB_PROCESSING_DEFS[job.processingStatus]} />
			</p>
			<p className="text-muted-foreground tabular-nums">
				{phases.retried ? "Request to this attempt" : "Wait to start"}: {phases.wait} · Running:{" "}
				{phases.execution}
				{job.exitCode !== null && ` · Exit code ${job.exitCode}`}
			</p>
			<p className="text-muted-foreground">
				Trace ID <code className="text-xs break-all">{job.traceId}</code>
			</p>
			{hasText(job.errorMessage) && (
				<pre className="max-h-48 overflow-auto rounded-md bg-muted/50 p-2 text-xs break-words whitespace-pre-wrap">
					{job.errorMessage}
				</pre>
			)}
		</li>
	);
}

function technicalFacts(review: ReviewDetail): TechnicalFact[] {
	const { usage } = review;
	let reasoning = 0;
	let cacheRead = 0;
	let cacheWrite = 0;
	for (const attempt of usage.attempts) {
		reasoning += attempt.reasoningTokens;
		cacheRead += attempt.cacheReadTokens;
		cacheWrite += attempt.cacheWriteTokens;
	}
	const facts: TechnicalFact[] = [
		{ label: "Review ID", value: <code>{review.id}</code>, copy: review.id },
		{ label: "Cost", value: costText(usage) },
		{ label: "Model", value: hasText(review.model) ? review.model : "—" },
		// The review's own calls: a practice script's decision, embedding and reranking calls are on
		// the Practice scripts section above.
		{ label: "Review model calls", value: formatTokens(usage.calls) },
		{ label: "Tokens read", value: formatTokens(usage.inputTokens) },
		{
			label: "Tokens written",
			value:
				reasoning > 0
					? `${formatTokens(usage.outputTokens)} (${formatTokens(reasoning)} of it reasoning)`
					: formatTokens(usage.outputTokens),
		},
		{
			label: "Cache tokens",
			value: `${formatTokens(cacheRead)} read · ${formatTokens(cacheWrite)} written`,
		},
	];
	if (review.batchId !== null) {
		facts.push({ label: "Batch ID", value: <code>{review.batchId}</code>, copy: review.batchId });
	}
	return facts;
}

function GeneratedPaths({ review }: { review: ReviewDetail }) {
	const { generatedPaths } = review;
	if (generatedPaths === null) {
		return null;
	}
	return (
		<Section
			level={4}
			size="sm"
			title="Generated paths"
			description="Kept as generated output, not judged as hand-written work."
			className="space-y-2 text-sm"
		>
			{generatedPaths.withheld ? (
				<p className="text-muted-foreground">
					{hasText(generatedPaths.reason)
						? generatedPaths.reason
						: "Generated paths were not checked for this review."}
				</p>
			) : (
				<>
					<p className="break-all">
						Patterns:{" "}
						{generatedPaths.patterns.length > 0
							? generatedPaths.patterns.join(", ")
							: "None configured"}
					</p>
					{generatedPaths.excludedPaths.length > 0 ? (
						<ul className="list-inside list-disc">
							{generatedPaths.excludedPaths.map((excluded) => (
								<li key={excluded}>
									<code className="break-all">{excluded}</code>
								</li>
							))}
						</ul>
					) : (
						<p className="text-muted-foreground">No changed paths matched.</p>
					)}
				</>
			)}
		</Section>
	);
}

function ReviewFeedbackChecks({ state }: { state: PanelState<{ checks: FeedbackCheck[] }> }) {
	if (state.status === "ready" && state.checks.length === 0) {
		return null;
	}
	return (
		<Section level={3} title="Feedback checks of the review" className="min-w-0">
			{state.status === "loading" && <LevelBodySkeleton />}
			{state.status === "error" && (
				<QueryErrorAlert
					error={state.error}
					title="We could not load the feedback checks"
					onRetry={state.onRetry}
				/>
			)}
			{state.status === "ready" && <FeedbackChecks checks={state.checks} />}
		</Section>
	);
}
