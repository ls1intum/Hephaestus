import { EyeOffIcon, Undo2Icon } from "lucide-react";
import type { ReactNode } from "react";

import type {
	FeedbackCopy,
	FeedbackDecision,
	FeedbackSend,
	ReviewFeedbackDetail,
} from "@/api/types.gen";
import { accountName } from "@/components/admin/users/account-labels";
import { InlineLink } from "@/components/common/InlineLink";
import type { PanelState } from "@/components/common/panel-state";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { Section } from "@/components/layout/Section";
import { FeedbackChecks } from "@/components/practice-trace/FeedbackChecks";
import { UNVERIFIED_HISTORY_NOTE } from "@/components/practice-vocabulary/claim-currentness";
import {
	DELIVERY_STATE_DEFS,
	deliveryOutcome,
} from "@/components/practice-vocabulary/delivery-state-defs";
import {
	DEVELOPER_ANSWER_DEFS,
	developerAnswer,
} from "@/components/practice-vocabulary/developer-answer-defs";
import { codeCitationLocator } from "@/components/practice-vocabulary/evidence-source-defs";
import {
	copyCountsSentence,
	FEEDBACK_COPY_KIND_DEFS,
	FEEDBACK_COPY_STATE_DEFS,
	isPartialPackage,
} from "@/components/practice-vocabulary/feedback-copy-defs";
import {
	FEEDBACK_DECISION_DEFS,
	type FeedbackDecisionReason,
	feedbackDecisionReasonLabel,
} from "@/components/practice-vocabulary/feedback-decision-defs";
import {
	ADMIN_WITHHELD_TEXT,
	type FeedbackDestination,
} from "@/components/practice-vocabulary/feedback-destination-defs";
import { FEEDBACK_RESOLUTION_DEFS } from "@/components/practice-vocabulary/feedback-resolution-defs";
import {
	FEEDBACK_SEND_ACTION_LABELS,
	FEEDBACK_SEND_STATE_DEFS,
} from "@/components/practice-vocabulary/feedback-send-defs";
import { FEEDBACK_USEFULNESS_DEFS } from "@/components/practice-vocabulary/feedback-usefulness-defs";
import { WITHHOLDING_REASON_DEFS } from "@/components/practice-vocabulary/withholding-defs";
import { DrawerBody, DrawerFooter } from "@/components/ui/drawer";
import { hasText } from "@/lib/text";

import { CorrectionReasonPopover, type CorrectionReasonCopy } from "./CorrectionReason";
import { feedbackOpeningLine } from "./feedback-preview";
import { FeedbackBody } from "./FeedbackBody";
import { FeedbackLocationLabel } from "./FeedbackLocationLabel";
import { LevelBodySkeleton } from "./LevelBodySkeleton";
import { type ApprovalQueue, ProposalDecision, ProposalPackage } from "./ProposalDecision";
import {
	ERASED_ACCOUNT,
	type SheetEvent,
	SheetHistory,
	SheetSection,
	SheetStatusLine,
	SheetTechnical,
	type TechnicalFact,
} from "./RecordSheet";
import { developerLabel } from "./review-format";
import { ReviewStatusMark } from "./ReviewBadges";
import { ReviewDayTime } from "./ReviewDayTime";
import { ReviewFact, ReviewFactGrid } from "./ReviewFactGrid";
import { ReviewItemLink } from "./ReviewItem";
import { ReviewLevelHeader } from "./ReviewLevelHeader";
import { ReviewPackage } from "./ReviewPackage";
import { ReviewPerson } from "./ReviewPerson";

type FeedbackRecord = ReviewFeedbackDetail;

export interface FeedbackLevelProps {
	nested?: boolean;
	path: LevelPath;
	feedback: PanelState<{ feedback: FeedbackRecord }>;
	isDeciding: boolean;
	onApprove: () => void;
	onReject: (reason: FeedbackDecisionReason, note?: string) => void;
	/** Where this feedback sits among all awaiting approval; absent when it awaits none. */
	queue?: ApprovalQueue;
	/**
	 * Withdraws (`true`) or restores (`false`) the feedback, with the admin's note. Settles when the
	 * server has answered; the form keeps the note until it succeeded.
	 */
	onChangeWithdrawal: (withdrawn: boolean, note: string) => Promise<unknown>;
	isChangingWithdrawal: boolean;
}

function inOrder(copies: readonly FeedbackCopy[]): FeedbackCopy[] {
	return [...copies].sort((a, b) => a.position - b.position);
}

// Private feedback belongs to the developer; identify it by destination rather than its text.
function feedbackTitle(feedback: FeedbackRecord): string {
	const withheld = ADMIN_WITHHELD_TEXT[feedback.destination];
	if (withheld !== undefined) {
		return withheld.title;
	}
	if (feedback.textHidden) {
		return "Feedback text hidden";
	}
	const body =
		feedback.body ?? inOrder(feedback.copies).find((copy) => hasText(copy.body))?.body ?? undefined;
	return (
		ADMIN_WITHHELD_TEXT[feedback.destination]?.title ??
		feedbackOpeningLine(body) ??
		(hasText(body) ? "Opens with a quote from the work…" : "No recorded feedback text")
	);
}

export function FeedbackLevel({
	nested,
	path,
	feedback: state,
	isDeciding,
	onApprove,
	onReject,
	queue,
	onChangeWithdrawal,
	isChangingWithdrawal,
}: FeedbackLevelProps) {
	const feedback = state.status === "ready" ? state.feedback : undefined;
	let flag: ReactNode;
	if (feedback?.withdrawn === true) {
		flag = <StatusBadge def={FEEDBACK_DECISION_DEFS.WITHDRAW} />;
	} else if (feedback !== undefined && feedback.disagreedAt !== null) {
		flag = <StatusBadge def={DEVELOPER_ANSWER_DEFS.DISAGREED} />;
	}
	const header = (
		<ReviewLevelHeader
			nested={nested}
			path={path}
			kind="feedback"
			loading={state.status === "loading"}
			chips={
				feedback && (
					<SheetStatusLine
						status={<ReviewStatusMark def={deliveryOutcome(feedback)} />}
						flag={flag}
						figure={
							isPartialPackage(feedback.copyCounts)
								? copyCountsSentence(feedback.copyCounts)
								: undefined
						}
						at={
							feedback.deliveredAt === null
								? { label: "composed", date: feedback.createdAt }
								: { label: "delivered", date: feedback.deliveredAt }
						}
					/>
				)
			}
			title={feedback && feedbackTitle(feedback)}
			description={feedback?.item && <ReviewItemLink item={feedback.item} />}
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
							title="We could not load this feedback"
							onRetry={state.onRetry}
						/>
					) : (
						<LevelBodySkeleton />
					)}
				</DrawerBody>
			</>
		);
	}

	const { feedback: record } = state;
	const awaitingApproval = record.state === "AWAITING_APPROVAL";
	return (
		<>
			{header}
			<DrawerBody className="flex flex-col gap-8 pt-2">
				{record.unverifiedHistory && (
					<p className="text-sm text-muted-foreground">{UNVERIFIED_HISTORY_NOTE}</p>
				)}
				<SheetSection part="what">
					<FeedbackWhat feedback={record} />
				</SheetSection>
				<SheetSection part="state">
					<SheetHistory events={feedbackHistory(record)} now={feedbackNow(record)} />
				</SheetSection>
				{awaitingApproval ? (
					<ProposalPackage feedback={record} />
				) : (
					<SheetSection part="details" title="What it says">
						{record.copies.length > 0 ? (
							<ReviewPackage feedback={record} />
						) : (
							<FeedbackBody feedback={record} />
						)}
					</SheetSection>
				)}
				{record.checks.length > 0 && (
					<Section level={3} title="Checks" className="min-w-0">
						<FeedbackChecks checks={record.checks} />
					</Section>
				)}
				<SheetTechnical facts={feedbackTechnical(record)} />
			</DrawerBody>
			{awaitingApproval ? (
				// The decision is the footer like every sheet's actions, so it stays in reach while the
				// package above it is read; approving it unread is what the expanded package designs out.
				<DrawerFooter className="sm:flex-wrap sm:items-center">
					<p className="text-xs text-muted-foreground sm:mr-auto">
						Approval authorizes every copy above. Feedback checks still run before anything is
						posted.
					</p>
					<div
						role="group"
						aria-label="Decide on this feedback"
						className="flex flex-wrap items-center justify-end gap-2"
					>
						<ProposalDecision
							canApprove={record.copies.length > 0}
							isDeciding={isDeciding}
							onApprove={onApprove}
							onReject={onReject}
							queue={queue}
						/>
					</div>
				</DrawerFooter>
			) : (
				canChangeWithdrawal(record) && (
					<DrawerFooter>
						<CorrectionReasonPopover
							copy={withdrawalCopy(record)}
							icon={record.withdrawn ? Undo2Icon : EyeOffIcon}
							destructive={!record.withdrawn}
							name="feedback-withdrawal-reason"
							disabled={isChangingWithdrawal}
							onSubmit={async (note) => onChangeWithdrawal(!record.withdrawn, note)}
						/>
					</DrawerFooter>
				)
			)}
		</>
	);
}

function FeedbackWhat({ feedback }: { feedback: FeedbackRecord }) {
	return (
		<ReviewFactGrid>
			<ReviewFact label="Developer">
				<ReviewPerson person={feedback.developer} />
			</ReviewFact>
			<ReviewFact label="Destination">
				<FeedbackLocationLabel feedback={feedback} />
			</ReviewFact>
			{hasText(feedback.item?.title) && (
				<ReviewFact label="Work">
					<span className="break-words">{feedback.item.title}</span>
				</ReviewFact>
			)}
		</ReviewFactGrid>
	);
}

function copyAnchor(copy: FeedbackCopy): string | undefined {
	if (!hasText(copy.path)) {
		return undefined;
	}
	if (copy.startLine === null) {
		return copy.path;
	}
	return codeCitationLocator({
		path: copy.path,
		startLine: copy.startLine,
		endLine: copy.endLine ?? undefined,
	});
}

function LastError({ error }: { error: string }) {
	return (
		<div className="mt-1">
			Last error:
			<pre className="mt-1 max-h-48 overflow-auto rounded-md bg-muted/50 p-2 text-xs break-words whitespace-pre-wrap">
				{error}
			</pre>
		</div>
	);
}

function feedbackHistory(feedback: FeedbackRecord): SheetEvent[] {
	const events: SheetEvent[] = [
		{ key: "composed", at: feedback.createdAt, title: "Composed" },
		...feedback.decisions.map(decisionEvent),
	];
	const { stopReason } = feedback;
	if (stopReason !== null && feedback.state !== "AWAITING_APPROVAL") {
		const stop = feedback.checks.find((check) => !check.allowed && check.reason === stopReason);
		events.push({
			key: "stopped",
			at: stop?.checkedAt,
			title: DELIVERY_STATE_DEFS[feedback.state].label,
			detail: WITHHOLDING_REASON_DEFS[stopReason],
		});
	}
	const onWork = feedback.destination === "ON_WORK";
	const receipts = onWork
		? inOrder(feedback.copies).flatMap((copy) => copyEvent(copy, stopReason) ?? [])
		: [];
	events.push(...receipts);
	if (feedback.deliveredAt !== null && receipts.length === 0) {
		events.push({ key: "delivered", at: feedback.deliveredAt, title: "Delivered" });
	}
	const failed = feedback.copies.some((copy) => copy.state === "FAILED");
	events.push(...feedback.sends.flatMap((send) => sendEvent(send, failed) ?? []));
	const response = responseEvent(feedback);
	if (response !== undefined) {
		events.push(response);
	}
	return events;
}

function decisionEvent(decision: FeedbackDecision): SheetEvent {
	const { reasonCode, note } = decision;
	return {
		key: decision.id,
		at: decision.decidedAt,
		title: FEEDBACK_DECISION_DEFS[decision.action].label,
		by: decision.actor === null ? ERASED_ACCOUNT : accountName(decision.actor),
		detail:
			reasonCode !== null || hasText(note) ? (
				<>
					{reasonCode !== null && (
						<span className="block">Reason: {feedbackDecisionReasonLabel(reasonCode)}</span>
					)}
					{hasText(note) && <span className="block whitespace-pre-wrap">Note: “{note}”</span>}
				</>
			) : undefined,
	};
}

function copyEvent(
	copy: FeedbackCopy,
	feedbackStopReason: FeedbackRecord["stopReason"],
): SheetEvent | undefined {
	const kind = FEEDBACK_COPY_KIND_DEFS[copy.kind].label;
	const anchor = copyAnchor(copy);
	const where = anchor !== undefined && <code className="break-all">{anchor}</code>;
	switch (copy.state) {
		case "PLANNED": {
			return undefined;
		}
		case "POSTED":
		case "RETRACTED": {
			return {
				key: copy.id,
				at: copy.postedAt ?? undefined,
				title: `${kind} posted`,
				detail: (
					<span className="flex flex-wrap items-center gap-x-2">
						{where}
						{hasText(copy.externalUrl) && (
							<InlineLink href={copy.externalUrl} external>
								Open the comment
							</InlineLink>
						)}
					</span>
				),
			};
		}
		case "WRITE_STARTED":
		case "FAILED": {
			const def = FEEDBACK_COPY_STATE_DEFS[copy.state];
			return {
				key: copy.id,
				at: copy.writeStartedAt ?? undefined,
				title: `${kind} ${def.label.toLowerCase()}`,
				detail: (
					<>
						{where}
						<span className="block">{def.description}</span>
						{hasText(copy.lastError) && <LastError error={copy.lastError} />}
					</>
				),
			};
		}
		case "WITHHELD": {
			const reason = copy.stopReason;
			return {
				key: copy.id,
				at: undefined,
				title: `${kind} withheld`,
				detail: (
					<>
						{where}
						{reason !== null && reason !== feedbackStopReason && (
							<span className="block">{WITHHOLDING_REASON_DEFS[reason]}</span>
						)}
					</>
				),
			};
		}
	}
}

function sendEvent(send: FeedbackSend, copiesFailed: boolean): SheetEvent | undefined {
	const done = send.state === "DONE";
	let title: string;
	if (!done) {
		title = FEEDBACK_SEND_STATE_DEFS[send.state].label;
	} else if (send.action === "RETRACT") {
		title = FEEDBACK_COPY_STATE_DEFS.RETRACTED.label;
	} else if (copiesFailed) {
		title = "Stopped trying";
	} else {
		return undefined;
	}
	return {
		key: `send-${send.action}`,
		at: send.updatedAt,
		title,
		detail: (
			<>
				<span className="block">
					{FEEDBACK_SEND_ACTION_LABELS[send.action]} · {attemptsLabel(send.attemptCount)}
				</span>
				{send.state === "UNCERTAIN" && (
					<span className="block">{FEEDBACK_SEND_STATE_DEFS.UNCERTAIN.description}</span>
				)}
				{!done && (
					<span className="block">
						Next attempt <ReviewDayTime at={send.nextAttemptAt} className="tabular-nums" />
					</span>
				)}
				{hasText(send.lastError) && <LastError error={send.lastError} />}
			</>
		),
	};
}

function attemptsLabel(count: number): string {
	if (count === 0) {
		return "not tried yet";
	}
	return count === 1 ? "1 attempt" : `${count} attempts`;
}

function responseEvent(feedback: FeedbackRecord): SheetEvent | undefined {
	const { response } = feedback;
	const answer = developerAnswer(response);
	if (answer === undefined || response === null) {
		return undefined;
	}
	const { resolution, usefulness, comment } = response;
	const facts = [
		resolution !== null && resolution !== "DISAGREE"
			? FEEDBACK_RESOLUTION_DEFS[resolution].label
			: undefined,
		usefulness === null ? undefined : FEEDBACK_USEFULNESS_DEFS[usefulness].label,
	].filter(hasText);
	return {
		key: "response",
		at: response.createdAt ?? undefined,
		title: DEVELOPER_ANSWER_DEFS[answer].label,
		by: developerLabel(feedback.developer),
		detail: (
			<>
				{facts.length > 0 && <span className="block">{facts.join(" · ")}</span>}
				{hasText(comment) && (
					<blockquote className="mt-1 border-l-2 pl-3 break-words whitespace-pre-wrap text-foreground">
						{comment}
					</blockquote>
				)}
			</>
		),
	};
}

const WITHDRAWN_NOW: Record<FeedbackDestination, string> = {
	ON_WORK:
		"It is withdrawn. Copies it posted on the work are retracted there, and their receipts stay in this history. The results behind it are unchanged.",
	PRACTICE_PAGE:
		"It is off the developer’s practice pages and out of the feedback given to later reviews and Heph. The results behind it are unchanged.",
	CONVERSATION:
		"It is withdrawn, so Heph does not raise it in a conversation. The results behind it are unchanged.",
};

/** What the current state means, in one sentence, in place of an alert. */
function feedbackNow(feedback: FeedbackRecord): ReactNode {
	if (feedback.withdrawn) {
		return WITHDRAWN_NOW[feedback.destination];
	}
	if (feedback.disagreedAt !== null) {
		return "While the developer’s disagreement stands, a later review of the same work does not raise the same point again.";
	}
	switch (feedback.state) {
		case "AWAITING_APPROVAL": {
			return "It waits for a workspace admin to approve or reject it.";
		}
		case "READY": {
			return readyLine(feedback);
		}
		case "UNCONFIRMED": {
			return "A conversation linked it, but its appearance was not recorded, so it does not count as delivered.";
		}
		case "DELIVERED":
		case "WITHHELD":
		case "REJECTED":
		case "REPLACED":
		case "FAILED": {
			return undefined;
		}
	}
}

function readyLine(feedback: FeedbackRecord): string {
	switch (feedback.destination) {
		case "PRACTICE_PAGE": {
			return "It is ready on the developer’s practice pages. It counts as delivered when they open it.";
		}
		case "CONVERSATION": {
			return "It is ready for the developer’s next conversation with Heph, which is what delivers it.";
		}
		case "ON_WORK": {
			return feedback.sends.some((send) => send.state === "UNCERTAIN")
				? "A write was not confirmed. The provider is checked before anything is posted again."
				: "Delivery is still in progress. This updates as the copies not yet posted are tried again.";
		}
	}
}

function feedbackTechnical(feedback: FeedbackRecord): TechnicalFact[] {
	const facts: TechnicalFact[] = [
		{ label: "Feedback ID", value: <code>{feedback.id}</code>, copy: feedback.id },
		{ label: "Review ID", value: <code>{feedback.reviewId}</code>, copy: feedback.reviewId },
		{ label: "Delivery state", value: <code>{feedback.state}</code> },
	];
	if (feedback.stopReason !== null) {
		facts.push({ label: "Stop reason", value: <code>{feedback.stopReason}</code> });
	}
	if (hasText(feedback.contentDigest)) {
		facts.push({
			label: "Content digest",
			value: <code>{feedback.contentDigest}</code>,
			copy: feedback.contentDigest,
		});
	}
	if (hasText(feedback.replacesId)) {
		facts.push({
			label: "Replaces",
			value: <code>{feedback.replacesId}</code>,
			copy: feedback.replacesId,
		});
	}
	const copies = inOrder(feedback.copies);
	for (const copy of copies) {
		const ref = copy.externalRef ?? copy.chatMessageId;
		if (hasText(ref)) {
			const sameKind = copies.filter((other) => other.kind === copy.kind);
			const kind = FEEDBACK_COPY_KIND_DEFS[copy.kind].label;
			const label = sameKind.length === 1 ? kind : `${kind} ${sameKind.indexOf(copy) + 1}`;
			facts.push({ label: `${label} reference`, value: <code>{ref}</code>, copy: ref });
		}
	}
	return facts;
}

function canChangeWithdrawal(feedback: FeedbackRecord): boolean {
	return (
		feedback.withdrawn ||
		feedback.state === "READY" ||
		feedback.state === "DELIVERED" ||
		feedback.state === "UNCONFIRMED"
	);
}

const WITHDRAW_DESCRIPTIONS: Record<FeedbackDestination, string> = {
	ON_WORK:
		"Copies already posted on the work are retracted there, and nothing more is posted. Their receipts stay on this sheet, and the results behind it are unchanged.",
	PRACTICE_PAGE:
		"It comes off the developer’s practice pages and out of the feedback given to later reviews and Heph. The results behind it are unchanged. A developer who already saw it sees that it was withdrawn, not your reason.",
	CONVERSATION:
		"Heph does not raise it in a conversation, and it leaves the feedback given to later reviews. The results behind it are unchanged.",
};

function withdrawalCopy(feedback: FeedbackRecord): CorrectionReasonCopy {
	if (feedback.withdrawn) {
		return {
			trigger: "Restore feedback",
			title: "Restore this feedback",
			description:
				feedback.destination === "PRACTICE_PAGE"
					? "It returns to the developer’s practice pages if its results can still be shown there. Nothing is sent again."
					: "The withdrawal ends. It is sent again only after every check passes.",
			placeholder: "Why the feedback was right after all…",
		};
	}
	return {
		trigger: "Withdraw feedback",
		title: "Withdraw this feedback",
		description: WITHDRAW_DESCRIPTIONS[feedback.destination],
		placeholder: "What the feedback got wrong…",
	};
}
