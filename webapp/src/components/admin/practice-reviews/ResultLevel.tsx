import { CircleSlashIcon, Undo2Icon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "cn";
import type {
	GetPracticeReviewResultResponse,
	Observation,
	OpenQuestion,
} from "@/api/types.gen";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import { UNTRUSTED_MARKDOWN_PROSE, UntrustedMarkdown } from "@/components/common/UntrustedMarkdown";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { Section } from "@/components/layout/Section";
import { NON_CURRENT } from "@/components/practice-vocabulary/claim-currentness";
import { DELIVERY_PLACE_DEFS } from "@/components/practice-vocabulary/delivery-place-defs";
import { DEVELOPER_RESPONSE_DEFS } from "@/components/practice-vocabulary/result-dispute-defs";
import {
	MARKED_INCORRECT_DEF,
	PROVIDER_COPY_IN_FORCE,
	PROVIDER_COPY_RESTORED,
} from "@/components/practice-vocabulary/result-invalidation-defs";
import { RESULT_ORIGIN_DEFS } from "@/components/practice-vocabulary/result-origin-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { Badge } from "@/components/ui/badge";
import { DrawerBody, DrawerFooter } from "@/components/ui/drawer";
import { hasText } from "@/lib/text";

import { CorrectionReasonPopover } from "./CorrectionReason";
import { LevelBodySkeleton } from "./LevelBodySkeleton";
import {
	ERASED_ACCOUNT,
	type SheetEvent,
	SheetHistory,
	SheetSection,
	SheetStatusLine,
	SheetTechnical,
} from "./RecordSheet";
import { citationLocation, ResultEvidence } from "./ResultEvidence";
import { ReviewArtifactLink } from "./ReviewArtifact";
import { ResultOutcome } from "./ReviewBadges";
import { ReviewFact, ReviewFactGrid } from "./ReviewFactGrid";
import { ReviewLevelHeader } from "./ReviewLevelHeader";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeName } from "./ReviewPracticeLink";

type ResultRecord = GetPracticeReviewResultResponse;

export interface ResultLevelProps {
	nested?: boolean;
	path: LevelPath;
	result: PanelState<{ result: ResultRecord }>;
	/**
	 * Invalidates (`false`) or restores (`true`) the result, with the admin's reason. Settles when the
	 * server has answered; the form keeps the reason until it succeeded.
	 */
	onChangeValidity: (valid: boolean, reason: string) => Promise<unknown>;
	isChangingValidity: boolean;
}

/** The correction in force, if any: the newest one, until it is restored. */
function correctionInForce(result: ResultRecord) {
	const latest = result.invalidations.at(0);
	return latest !== undefined && latest.restoredAt === undefined ? latest : undefined;
}

/**
 * The one flag the status line has room for, most consequential first: marked incorrect, then
 * disputed, then no longer current.
 */
function resultFlag(result: ResultRecord): ReactNode {
	if (correctionInForce(result) !== undefined) {
		return <StatusBadge def={MARKED_INCORRECT_DEF} />;
	}
	if (result.disputes.length > 0) {
		return <StatusBadge def={DEVELOPER_RESPONSE_DEFS.DISPUTED} />;
	}
	if (result.claimCurrentness !== "CURRENT") {
		const config = NON_CURRENT[result.claimCurrentness];
		return <Badge variant={config.badgeVariant}>{config.badge}</Badge>;
	}
	return undefined;
}

/**
 * One result's sheet, and only the result, in the order every sheet has: the status line (its
 * outcome with its number of problems and its severity, the one flag that changes how it reads,
 * when it was recorded), what it is, its history — recorded, disputed in the developer's own words,
 * marked incorrect and restored, no longer current — why it was raised, the problems it located,
 * the questions it left open, the passages it cites, and the technical facts. Its one decision,
 * mark it incorrect or restore it, is the footer.
 *
 * The dispute is shown in full, because it answers this result. The developer's ratings and
 * private notes are not: they stay theirs. The example a review may record is for the developer's
 * own pages, so it is not shown here. The feedback written from it and when it was read are its
 * trace.
 */
export function ResultLevel({
	nested,
	path,
	result: state,
	onChangeValidity,
	isChangingValidity,
}: ResultLevelProps) {
	const result = state.status === "ready" ? state.result : undefined;
	const inForce = result && correctionInForce(result);
	const header = (
		<ReviewLevelHeader
			nested={nested}
			path={path}
			kind="result"
			loading={state.status === "loading"}
			chips={
				result && (
					<SheetStatusLine
						status={<ResultOutcome result={result} problems={result.observations.length} />}
						flag={resultFlag(result)}
						at={{ label: "recorded", date: result.observedAt }}
					/>
				)
			}
			title={result?.summary}
			description={result && <ReviewArtifactLink reviewedWork={result.reviewedWork} />}
		/>
	);

	if (state.status !== "ready") {
		return (
			<>
				{header}
				<DrawerBody className="pt-2">
					{state.status === "error" ? (
						<QueryErrorAlert
							error={state.error}
							title="We could not load this result"
							onRetry={state.onRetry}
						/>
					) : (
						<LevelBodySkeleton />
					)}
				</DrawerBody>
			</>
		);
	}

	const { result: record } = state;
	const problems = [...record.observations].sort((a, b) => a.position - b.position);
	const citations = record.evidence?.citations ?? [];
	const openQuestions = record.evidence?.undecidability ?? [];

	return (
		<>
			{header}
			<DrawerBody className="flex flex-col gap-8 pt-2">
				<SheetSection part="what">
					<ReviewFactGrid>
						<ReviewFact label="Practice">
							<ReviewPracticeName practiceName={record.practiceName} group={record.group} />
						</ReviewFact>
						<ReviewFact label="Developer">
							<ReviewPerson person={record.subject} />
						</ReviewFact>
						{hasText(record.reviewedWork.title) && (
							<ReviewFact label="Reviewed work">
								<span className="break-words">{record.reviewedWork.title}</span>
							</ReviewFact>
						)}
						<ReviewFact label="Origin">
							<span className="space-y-0.5">
								<span className="block">{RESULT_ORIGIN_DEFS[record.origin].label}</span>
								<span className="block text-muted-foreground">
									{RESULT_ORIGIN_DEFS[record.origin].description}
								</span>
							</span>
						</ReviewFact>
					</ReviewFactGrid>
				</SheetSection>

				<SheetSection part="state">
					<SheetHistory events={resultHistory(record)} now={resultNow(record)} />
				</SheetSection>

				<SheetSection part="details">
					<div className="space-y-6">
						{hasText(record.evidenceRationale) && (
							<Section level={4} size="sm" title="Why this was raised">
								{/* The reviewer writes its rationale in Markdown, as the developer's own row shows it. */}
								<div className={cn(UNTRUSTED_MARKDOWN_PROSE, "text-sm")}>
									<UntrustedMarkdown>{record.evidenceRationale}</UntrustedMarkdown>
								</div>
							</Section>
						)}
						{problems.length > 0 && (
							<Section level={4} size="sm" title="Problems">
								<ol aria-label="Problems" className="space-y-8">
									{problems.map((problem) => (
										<ProblemItem key={problem.observationId} problem={problem} />
									))}
								</ol>
							</Section>
						)}
						{openQuestions.length > 0 && (
							<Section level={4} size="sm" title="Open questions">
								<ul aria-label="Open questions" className="space-y-4 text-sm">
									{openQuestions.map((question, index) => (
										<OpenQuestionItem key={`${question.openQuestion}:${index}`} question={question} />
									))}
								</ul>
							</Section>
						)}
						{/* A not met result's passages are its problems'. Its own section stands only when it
						    cites something of its own, or when nothing else on the sheet would say that
						    nothing was quoted. */}
						{(citations.length > 0 || problems.length === 0) && (
							<Section level={4} size="sm" title="Evidence">
								<ResultEvidence citations={citations} />
							</Section>
						)}
					</div>
				</SheetSection>

				<SheetTechnical
					facts={[
						{ label: "Result ID", value: <code>{record.id}</code>, copy: record.id },
						{
							label: "Review ID",
							value: <code>{record.agentJobId}</code>,
							copy: record.agentJobId,
						},
						{
							label: "Practice revision",
							value:
								record.practiceRevisionId === undefined ? "—" : String(record.practiceRevisionId),
						},
						{ label: "Currentness", value: <code>{record.claimCurrentness}</code> },
					]}
				/>
			</DrawerBody>
			<DrawerFooter>
				<ValidityPopover
					invalidated={inForce !== undefined}
					disabled={isChangingValidity}
					onSubmit={async (reason) => onChangeValidity(inForce !== undefined, reason)}
				/>
			</DrawerFooter>
		</>
	);
}

/**
 * Recorded, each dispute in the developer's own words, each correction and restore with who and
 * why, and when a later review superseded it: one list, oldest first.
 */
export function resultHistory(result: ResultRecord): SheetEvent[] {
	const DisputedIcon = DEVELOPER_RESPONSE_DEFS.DISPUTED.icon;
	const events: SheetEvent[] = [{ key: "recorded", at: result.observedAt, title: "Recorded" }];
	for (const dispute of result.disputes) {
		events.push({
			key: `disputed-${dispute.feedbackId}`,
			at: dispute.disputedAt,
			icon: <DisputedIcon aria-hidden className="size-3.5 text-warning" />,
			title: `Disputed by the developer, on feedback ${DELIVERY_PLACE_DEFS[dispute.channel].label.toLowerCase()}`,
			detail: (
				<blockquote className="mt-1 border-l-2 pl-3 break-words whitespace-pre-wrap text-foreground">
					{dispute.explanation}
				</blockquote>
			),
		});
	}
	for (const correction of result.invalidations) {
		events.push({
			key: correction.id,
			at: correction.invalidatedAt,
			title: "Marked incorrect",
			by: correction.invalidatedBy ?? ERASED_ACCOUNT,
			detail: `Reason: “${correction.reason}”`,
		});
		if (correction.restoredAt !== undefined) {
			const note = PROVIDER_COPY_RESTORED[correction.providerCopy];
			events.push({
				key: `${correction.id}-restored`,
				at: correction.restoredAt,
				title: "Restored",
				by: correction.restoredBy ?? ERASED_ACCOUNT,
				detail: (
					<>
						{hasText(correction.restorationReason) && (
							<span className="block">Reason: “{correction.restorationReason}”</span>
						)}
						{note !== undefined && <span className="block">{note}</span>}
					</>
				),
			});
		}
	}
	if (result.supersededAt !== undefined) {
		events.push({
			key: "superseded",
			at: result.supersededAt,
			title: "No longer current",
			detail: "A later change to the reviewed work superseded it.",
		});
	}
	return events;
}

/** What the current state means, in one or two sentences, in place of an alert. */
function resultNow(result: ResultRecord): ReactNode {
	const inForce = correctionInForce(result);
	if (inForce !== undefined) {
		return (
			<>
				It no longer counts toward the developer’s standing, their practice pages or Heph, and
				feedback about it that had not reached anyone was stopped.{" "}
				{PROVIDER_COPY_IN_FORCE[inForce.providerCopy]}
			</>
		);
	}
	if (result.disputes.length > 0) {
		return "While the dispute stands, a later review of the same work does not raise the same point again. Mark the result incorrect if it was wrong.";
	}
	if (result.claimCurrentness !== "CURRENT") {
		return NON_CURRENT[result.claimCurrentness].description;
	}
	return undefined;
}

/**
 * One located problem: its own severity, what it is, why, the passages that place it and what would
 * fix it. The explanation is the review's text, rendered as the rationale is.
 */
function ProblemItem({ problem }: { problem: Observation }) {
	return (
		<li className="space-y-3">
			<div className="flex flex-wrap items-start gap-2">
				<StatusBadge def={SEVERITY_DEFS[problem.severity]} />
				<p className="min-w-0 flex-1 basis-48 text-sm font-medium break-words">{problem.summary}</p>
			</div>
			<div className={cn(UNTRUSTED_MARKDOWN_PROSE, "text-sm")}>
				<UntrustedMarkdown>{problem.explanation}</UntrustedMarkdown>
			</div>
			<ResultEvidence citations={problem.citations} />
			{hasText(problem.nextStep) && (
				<div className="space-y-1 text-sm">
					<p className="font-medium">Next step</p>
					<div className={UNTRUSTED_MARKDOWN_PROSE}>
						<UntrustedMarkdown>{problem.nextStep}</UntrustedMarkdown>
					</div>
				</div>
			)}
		</li>
	);
}

/** What the review could not settle: never a claim about the work, so never feedback. */
function OpenQuestionItem({ question }: { question: OpenQuestion }) {
	return (
		<li className="space-y-1 break-words">
			<p className="font-medium">{question.openQuestion}</p>
			<p className="text-muted-foreground">Would settle it: {question.wouldSettleIt}</p>
			{question.citation !== undefined && (
				<p className="text-xs text-muted-foreground">
					At <code className="break-all">{citationLocation(question.citation)}</code>
				</p>
			)}
		</li>
	);
}

function ValidityPopover({
	invalidated,
	disabled,
	onSubmit,
}: {
	invalidated: boolean;
	disabled: boolean;
	onSubmit: (reason: string) => Promise<unknown>;
}) {
	return (
		<CorrectionReasonPopover
			copy={
				invalidated
					? {
							trigger: "Restore result",
							title: "Restore this result",
							description:
								"It counts again from now on. Feedback that was stopped stays stopped, and nothing is re-sent.",
							placeholder: "Why the result was right after all…",
						}
					: {
							trigger: "Mark as incorrect",
							title: "Mark this result as incorrect",
							description:
								"The review record stays as it was. The developer sees your reason next to the result.",
							placeholder: "What the result got wrong…",
						}
			}
			icon={invalidated ? Undo2Icon : CircleSlashIcon}
			destructive={!invalidated}
			name="result-validity-reason"
			disabled={disabled}
			onSubmit={onSubmit}
		/>
	);
}
