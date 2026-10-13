import { CircleSlashIcon, Undo2Icon } from "lucide-react";
import type { ReactNode } from "react";
import { observationSummary, OBSERVATION_WORDS_UNAVAILABLE } from "./observation-words";

import { cn } from "cn";
import type { GetPracticeReviewObservationResponse } from "@/api/types.gen";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import { UNTRUSTED_MARKDOWN_PROSE, UntrustedMarkdown } from "@/components/common/UntrustedMarkdown";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { Section } from "@/components/layout/Section";
import { NON_CURRENT } from "@/components/practice-vocabulary/claim-currentness";
import { DELIVERY_PLACE_DEFS } from "@/components/practice-vocabulary/delivery-place-defs";
import { DEVELOPER_RESPONSE_DEFS } from "@/components/practice-vocabulary/observation-dispute-defs";
import {
	MARKED_INCORRECT_DEF,
	PROVIDER_COPY_IN_FORCE,
	PROVIDER_COPY_RESTORED,
} from "@/components/practice-vocabulary/observation-invalidation-defs";
import { OBSERVATION_ORIGIN_DEFS } from "@/components/practice-vocabulary/observation-origin-defs";
import { Badge } from "@/components/ui/badge";
import { DrawerBody, DrawerFooter } from "@/components/ui/drawer";
import { hasText } from "@/lib/text";

import { CorrectionReasonPopover } from "./CorrectionReason";
import { LevelBodySkeleton } from "./LevelBodySkeleton";
import { ObservationEvidence } from "./ObservationEvidence";
import {
	ERASED_ACCOUNT,
	type SheetEvent,
	SheetHistory,
	SheetSection,
	SheetStatusLine,
	SheetTechnical,
} from "./RecordSheet";
import { ReviewArtifactLink } from "./ReviewArtifact";
import { ObservationResult } from "./ReviewBadges";
import { ReviewFact, ReviewFactGrid } from "./ReviewFactGrid";
import { ReviewLevelHeader } from "./ReviewLevelHeader";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeName } from "./ReviewPracticeLink";

type ObservationRecord = GetPracticeReviewObservationResponse;

export interface ObservationLevelProps {
	nested?: boolean;
	path: LevelPath;
	observation: PanelState<{ observation: ObservationRecord }>;
	/**
	 * Invalidates (`false`) or restores (`true`) the observation, with the admin's reason. Settles when the
	 * server has answered; the form keeps the reason until it succeeded.
	 */
	onChangeValidity: (valid: boolean, reason: string) => Promise<unknown>;
	isChangingValidity: boolean;
}

/** The correction in force, if any: the newest one, until it is restored. */
function correctionInForce(observation: ObservationRecord) {
	const latest = observation.invalidations.at(0);
	return latest !== undefined && latest.restoredAt === undefined ? latest : undefined;
}

/**
 * The one flag the status line has room for, most consequential first: marked incorrect, then
 * disputed, then no longer current.
 */
function observationFlag(observation: ObservationRecord): ReactNode {
	if (correctionInForce(observation) !== undefined) {
		return <StatusBadge def={MARKED_INCORRECT_DEF} />;
	}
	if (observation.disputes.length > 0) {
		return <StatusBadge def={DEVELOPER_RESPONSE_DEFS.DISPUTED} />;
	}
	if (observation.claimCurrentness !== "CURRENT") {
		const config = NON_CURRENT[observation.claimCurrentness];
		return <Badge variant={config.badgeVariant}>{config.badge}</Badge>;
	}
	return undefined;
}

/**
 * One observation's sheet, and only the observation, in the order every sheet has: the status
 * line (its outcome and severity, the one flag that changes how it reads, when it was observed),
 * what it is, its history — observed, disputed in the developer's own words, marked incorrect and
 * restored, no longer current — why it was raised with every passage of evidence, and the technical
 * facts. Its one decision, mark it incorrect or restore it, is the footer.
 *
 * The dispute is shown in full, because it answers this observation. The developer's ratings and
 * private notes are not: they stay theirs. The feedback written from it and when it was read are
 * its trace.
 */
export function ObservationLevel({
	nested,
	path,
	observation: state,
	onChangeValidity,
	isChangingValidity,
}: ObservationLevelProps) {
	const observation = state.status === "ready" ? state.observation : undefined;
	const inForce = observation && correctionInForce(observation);
	const header = (
		<ReviewLevelHeader
			nested={nested}
			path={path}
			kind="observation"
			loading={state.status === "loading"}
			chips={
				observation && (
					<SheetStatusLine
						status={<ObservationResult observation={observation} />}
						flag={observationFlag(observation)}
						at={{ label: "observed", date: observation.observedAt }}
					/>
				)
			}
			title={observation === undefined ? undefined : observationSummary(observation)}
			description={observation && <ReviewArtifactLink reviewedWork={observation.reviewedWork} />}
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
							title="We could not load this observation"
							onRetry={state.onRetry}
						/>
					) : (
						<LevelBodySkeleton />
					)}
				</DrawerBody>
			</>
		);
	}

	const { observation: record } = state;

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
								<span className="block">{OBSERVATION_ORIGIN_DEFS[record.origin].label}</span>
								<span className="block text-muted-foreground">
									{OBSERVATION_ORIGIN_DEFS[record.origin].description}
								</span>
							</span>
						</ReviewFact>
					</ReviewFactGrid>
				</SheetSection>

				<SheetSection part="state">
					<SheetHistory events={observationHistory(record)} now={observationNow(record)} />
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
						<Section level={4} size="sm" title="Evidence">
							{record.summary === undefined ? (
								<p className="text-sm text-muted-foreground">{OBSERVATION_WORDS_UNAVAILABLE}</p>
							) : (
								<ObservationEvidence evidence={record.evidence} />
							)}
						</Section>
					</div>
				</SheetSection>

				<SheetTechnical
					facts={[
						{ label: "Observation ID", value: <code>{record.id}</code>, copy: record.id },
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
						{
							label: "Recurrence key",
							value: hasText(record.recurrenceKey) ? <code>{record.recurrenceKey}</code> : "—",
							copy: record.recurrenceKey,
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
 * Observed, each dispute in the developer's own words, each correction and restore with who and
 * why, and when a later review superseded it: one list, oldest first.
 */
export function observationHistory(observation: ObservationRecord): SheetEvent[] {
	const DisputedIcon = DEVELOPER_RESPONSE_DEFS.DISPUTED.icon;
	const events: SheetEvent[] = [{ key: "observed", at: observation.observedAt, title: "Observed" }];
	for (const dispute of observation.disputes) {
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
	for (const correction of observation.invalidations) {
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
	if (observation.supersededAt !== undefined) {
		events.push({
			key: "superseded",
			at: observation.supersededAt,
			title: "No longer current",
			detail: "A later change to the reviewed work superseded it.",
		});
	}
	return events;
}

/** What the current state means, in one or two sentences, in place of an alert. */
function observationNow(observation: ObservationRecord): ReactNode {
	const inForce = correctionInForce(observation);
	if (inForce !== undefined) {
		return (
			<>
				It no longer counts toward the developer’s standing, their practice pages or Heph, and
				feedback about it that had not reached anyone was stopped.{" "}
				{PROVIDER_COPY_IN_FORCE[inForce.providerCopy]}
			</>
		);
	}
	if (observation.disputes.length > 0) {
		return "While the dispute stands, a later review of the same work does not raise the same point again. Mark the observation incorrect if it was wrong.";
	}
	if (observation.claimCurrentness !== "CURRENT") {
		return NON_CURRENT[observation.claimCurrentness].description;
	}
	return undefined;
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
							trigger: "Restore observation",
							title: "Restore this observation",
							description:
								"It counts again from now on. Feedback that was stopped stays stopped, and nothing is re-sent.",
							placeholder: "Why the observation was right after all…",
						}
					: {
							trigger: "Mark as incorrect",
							title: "Mark this observation as incorrect",
							description:
								"The review record stays as it was. The developer sees your reason next to the observation.",
							placeholder: "What the observation got wrong…",
						}
			}
			icon={invalidated ? Undo2Icon : CircleSlashIcon}
			destructive={!invalidated}
			name="observation-validity-reason"
			disabled={disabled}
			onSubmit={onSubmit}
		/>
	);
}
