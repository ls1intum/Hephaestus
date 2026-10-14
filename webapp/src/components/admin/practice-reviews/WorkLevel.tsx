import type { ReactNode } from "react";

import type { ReviewWorkFacts, TracedArtifact, TracedSignal } from "@/api/types.gen";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { RefusalFixLink } from "@/components/practice-trace/RefusalFixLink";
import {
	DISCOVERED_VIA_DESCRIPTIONS,
	DISCOVERED_VIA_LABELS,
	SIGNAL_STATE_LABELS,
} from "@/components/practice-trace/trace-format";
import {
	REVIEWED_WORK_CHECK_DEFS,
	REVIEWED_WORK_STATE_DEFS,
} from "@/components/practice-vocabulary/reviewed-work-state-defs";
import {
	TRACED_WORK_DEFS,
	tracedWorkState,
} from "@/components/practice-vocabulary/traced-work-defs";
import { DrawerBody, DrawerFooter } from "@/components/ui/drawer";
import { itemKindLabel, itemName } from "@/lib/item-kinds";
import { hasText } from "@/lib/text";

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
import { ReviewArtifactLink } from "./ReviewArtifact";
import { ReviewDayTime } from "./ReviewDayTime";
import { ReviewFact, ReviewFactGrid } from "./ReviewFactGrid";
import { ReviewLevelHeader } from "./ReviewLevelHeader";
import { ReviewPerson } from "./ReviewPerson";

export interface WorkLevelProps {
	workspaceSlug: string;
	nested?: boolean;
	path: LevelPath;
	/** The work as the work list has it; `undefined` for work nothing was recorded about. */
	work: PanelState<{ work: TracedArtifact | undefined }>;
	/** Every moment recorded on the work, as its trace has them. */
	moments?: PanelState<{ moments: TracedSignal[] }>;
	/**
	 * What the provider reported about a pull request, merge request or issue; `undefined` for
	 * other work and for work in a repository the workspace does not monitor, which also sets
	 * `unmonitored` so the sheet says why the facts are missing, and `answer`, what the server
	 * answered, so an admin sees the reply itself: "204 No Content from GET …".
	 */
	facts?: PanelState<{
		facts: ReviewWorkFacts | undefined;
		unmonitored?: boolean;
		answer?: string;
	}>;
	/** Asks for a review of the work; offered only for the kinds a person can ask to have reviewed. */
	onRequestReview?: () => void;
	requesting?: boolean;
	/** Why no review can be asked for now, such as one of this work already running. */
	requestBlocked?: string;
	/** Why the last ask about this work started nothing, drawn by the route. */
	refusal?: ReactNode;
}

/**
 * One piece of work's sheet, and only the work, in the order every sheet has: the status line
 * (whether any of its moments started a review, where it stands on its provider, how many moments,
 * the latest), what it is (its kind, author, branches, size and checks), its history — opened,
 * every moment recorded on it with what happened, how we came to know, in which state it stopped and
 * why, with the setting that would let it through, then merged or closed — and its technical
 * facts. Asking for a review is the footer. The reviews of the work are its trace.
 */
export function WorkLevel({
	workspaceSlug,
	nested,
	path,
	work: state,
	moments,
	facts: factsState,
	onRequestReview,
	requesting = false,
	requestBlocked,
	refusal,
}: WorkLevelProps) {
	const work = state.status === "ready" ? state.work : undefined;
	const facts = factsState?.status === "ready" ? factsState.facts : undefined;
	const header = (
		<ReviewLevelHeader
			nested={nested}
			path={path}
			kind="work"
			loading={state.status === "loading"}
			chips={
				work && (
					<SheetStatusLine
						status={<StatusBadge def={TRACED_WORK_DEFS[tracedWorkState(work)]} />}
						flag={facts && <StatusBadge def={REVIEWED_WORK_STATE_DEFS[facts.state]} />}
						figure={momentsFigure(work)}
						at={{ label: "latest moment", date: work.lastSignalAt }}
					/>
				)
			}
			title={workTitle(work, state.status === "ready")}
			description={work && <ReviewArtifactLink reviewedWork={work.reviewedWork} />}
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
							title="We could not load this work"
							onRetry={state.onRetry}
						/>
					) : (
						<LevelBodySkeleton />
					)}
				</DrawerBody>
			</>
		);
	}
	if (work === undefined) {
		return (
			<>
				{header}
				<DrawerBody className="pt-2">
					<p className="text-sm text-muted-foreground">
						Nothing was recorded about this work, so there is no moment to show.
					</p>
				</DrawerBody>
			</>
		);
	}
	const all =
		moments?.status === "ready" && moments.moments.length > 0 ? moments.moments : [work.lastSignal];
	return (
		<>
			{header}
			<DrawerBody className="flex flex-col gap-8 pt-2">
				{refusal}
				<SheetSection part="what">
					<ReviewFactGrid>
						<ReviewFact label="Kind">
							{itemKindLabel(work.artifactKind, 1, work.reviewedWork.provider)}
						</ReviewFact>
						{hasText(work.reviewedWork.container) &&
							!work.reviewedWork.label.includes(work.reviewedWork.container) && (
								<ReviewFact label="Where">{work.reviewedWork.container}</ReviewFact>
							)}
						{facts !== undefined && <WorkFactsWhat facts={facts} />}
					</ReviewFactGrid>
					{factsState?.status === "ready" && factsState.unmonitored === true && (
						<div className="flex flex-col gap-1">
							<p className="text-sm text-muted-foreground">
								Work details such as author, branches and checks are not available, because this
								workspace does not monitor the repository or the work was deleted.
							</p>
							{hasText(factsState.answer) && (
								<p className="font-mono text-xs break-all text-muted-foreground">
									Server answer: {factsState.answer}
								</p>
							)}
						</div>
					)}
					{factsState?.status === "error" && (
						<QueryErrorAlert
							error={factsState.error}
							title="We could not load what the provider reported about this work"
							onRetry={factsState.onRetry}
						/>
					)}
				</SheetSection>
				<SheetSection
					part="state"
					description="Every moment recorded on the work and what each did, with when it was opened and merged or closed."
				>
					{moments?.status === "loading" && <LevelBodySkeleton />}
					{moments?.status === "error" && (
						<QueryErrorAlert
							error={moments.error}
							title="We could not load the moments on this work"
							onRetry={moments.onRetry}
						/>
					)}
					<SheetHistory
						events={[
							...all.map((moment) => momentEvent(moment, workspaceSlug)),
							...providerEvents(facts),
						]}
					/>
				</SheetSection>
				<SheetTechnical
					facts={[
						{ label: "Artifact ID", value: String(work.artifactId), copy: String(work.artifactId) },
						{ label: "Kind", value: <code>{work.artifactKind}</code> },
						{
							label: "Latest revision",
							value: hasText(work.lastSignal.revision) ? (
								<code>{work.lastSignal.revision}</code>
							) : (
								"—"
							),
							copy: work.lastSignal.revision,
						},
						...technicalFacts(facts),
					]}
				/>
			</DrawerBody>
			{onRequestReview !== undefined && (
				<DrawerFooter>
					<RequestReviewAction
						label="Request review"
						request={{
							work: itemName(work.reviewedWork),
							onRequest: onRequestReview,
							requesting,
							blocked: requestBlocked,
						}}
					/>
				</DrawerFooter>
			)}
		</>
	);
}

/** The work's title, or "Unknown work" for work nothing was recorded about. */
function workTitle(work: TracedArtifact | undefined, ready: boolean): string | undefined {
	if (work !== undefined) {
		return work.reviewedWork.title ?? work.reviewedWork.label;
	}
	return ready ? "Unknown work" : undefined;
}

/** "3 moments, none started a review", "3 moments, 1 started a review". */
function momentsFigure(work: TracedArtifact): string {
	const moments = work.signalCount === 1 ? "1 moment" : `${work.signalCount} moments`;
	const started = work.reviewedSignalCount === 0 ? "none" : String(work.reviewedSignalCount);
	return `${moments}, ${started} started a review`;
}

function momentEvent(moment: TracedSignal, workspaceSlug: string): SheetEvent {
	const stopped = hasText(moment.stateReasonDescription) || moment.stateReason !== undefined;
	return {
		key: moment.id,
		at: moment.occurredAt,
		title: moment.displayName,
		detail: (
			<>
				<span className="block">
					<span title={DISCOVERED_VIA_DESCRIPTIONS[moment.discoveredVia]}>
						{DISCOVERED_VIA_LABELS[moment.discoveredVia]}
					</span>
					{" · "}
					{hasText(moment.reviewId) ? "Started a review" : SIGNAL_STATE_LABELS[moment.state]}
				</span>
				{stopped && (
					<span className="block">
						{hasText(moment.stateReasonDescription) && (
							<span>{moment.stateReasonDescription} </span>
						)}
						{moment.stateReason !== undefined && (
							<RefusalFixLink
								workspaceSlug={workspaceSlug}
								reason={moment.stateReason}
								canAdminister
							/>
						)}
					</span>
				)}
			</>
		),
	};
}

/** "+120 −30", "7 files", "3 commits": the size of a change, as far as the provider reported it. */
function sizeText(facts: ReviewWorkFacts): string | undefined {
	const parts: string[] = [];
	if (facts.additions !== undefined && facts.deletions !== undefined) {
		parts.push(`+${facts.additions.toLocaleString()} −${facts.deletions.toLocaleString()}`);
	}
	if (facts.changedFiles !== undefined) {
		parts.push(
			facts.changedFiles === 1 ? "1 file" : `${facts.changedFiles.toLocaleString()} files`,
		);
	}
	if (facts.commits !== undefined) {
		parts.push(facts.commits === 1 ? "1 commit" : `${facts.commits.toLocaleString()} commits`);
	}
	return parts.length > 0 ? parts.join(" · ") : undefined;
}

/** Who opened the work, where its changes go, how large they are and how its checks stand. */
function WorkFactsWhat({ facts }: { facts: ReviewWorkFacts }) {
	const size = sizeText(facts);
	return (
		<>
			{facts.author !== undefined && (
				<ReviewFact label="Author">
					<ReviewPerson person={facts.author} />
				</ReviewFact>
			)}
			{facts.draft === true && <ReviewFact label="Draft">Marked as a draft</ReviewFact>}
			{hasText(facts.sourceBranch) && hasText(facts.targetBranch) && (
				<ReviewFact label="Branches">
					<span className="break-all">
						<code>{facts.sourceBranch}</code> into <code>{facts.targetBranch}</code>
					</span>
				</ReviewFact>
			)}
			{size !== undefined && (
				<ReviewFact label="Size">
					<span className="tabular-nums">{size}</span>
				</ReviewFact>
			)}
			{facts.checkState !== undefined && (
				<ReviewFact label="Checks">
					<StatusBadge def={REVIEWED_WORK_CHECK_DEFS[facts.checkState]} />
				</ReviewFact>
			)}
		</>
	);
}

/** Opened, last updated, and merged or closed, as the provider reported the work's times. */
function providerEvents(facts: ReviewWorkFacts | undefined): SheetEvent[] {
	if (facts === undefined) {
		return [];
	}
	const events: SheetEvent[] = [];
	if (facts.createdAt !== undefined) {
		events.push({ key: "opened", at: facts.createdAt, title: "Opened" });
	}
	if (facts.mergedAt !== undefined) {
		events.push({ key: "merged", at: facts.mergedAt, title: "Merged" });
	} else if (facts.closedAt !== undefined) {
		events.push({ key: "closed", at: facts.closedAt, title: "Closed" });
	}
	const ended = facts.mergedAt ?? facts.closedAt;
	if (facts.updatedAt !== undefined && facts.updatedAt.getTime() !== ended?.getTime()) {
		events.push({ key: "updated", at: facts.updatedAt, title: "Last updated on the provider" });
	}
	return events;
}

/** The head commit and how fresh the provider's facts are. */
function technicalFacts(facts: ReviewWorkFacts | undefined): TechnicalFact[] {
	if (facts === undefined) {
		return [];
	}
	const technical: TechnicalFact[] = [
		{
			label: "Head revision",
			value: hasText(facts.headRevision) ? <code>{facts.headRevision}</code> : "—",
			copy: facts.headRevision,
		},
	];
	if (facts.checkObservedAt !== undefined) {
		technical.push({ label: "Checks read", value: <ReviewDayTime at={facts.checkObservedAt} /> });
	}
	if (facts.lastSyncAt !== undefined) {
		technical.push({
			label: "Last read from the provider",
			value: <ReviewDayTime at={facts.lastSyncAt} />,
		});
	}
	return technical;
}
