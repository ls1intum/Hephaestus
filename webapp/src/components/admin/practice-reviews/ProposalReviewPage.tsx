import { Link } from "@tanstack/react-router";
import { CheckIcon, CircleXIcon, Clock3Icon } from "lucide-react";
import { useState } from "react";

import type {
	DecideFeedbackProposalRequest,
	GetPracticeReviewFeedbackResponse,
	Practice,
} from "@/api/types.gen";
import { DELIVERY_PLACE_DEFS } from "@/components/practice-vocabulary/delivery-place-defs";
import { observationResult } from "@/components/practice-vocabulary/observation-result";
import { placementLabel } from "@/components/practice-vocabulary/placement-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import {
	Popover,
	PopoverContent,
	PopoverDescription,
	PopoverHeader,
	PopoverTitle,
	PopoverTrigger,
} from "@/components/ui/popover";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";

import { FeedbackBody } from "./FeedbackBody";
import { subjectLabel } from "./review-format";
import { ReviewArtifactLink } from "./ReviewArtifact";
import { ClaimCurrentnessBadge, ObservationResultBadge } from "./ReviewBadges";
import { ReviewBreadcrumbs } from "./ReviewBreadcrumbs";
import { ReviewDetailHeader, ReviewFact, ReviewFactGrid } from "./ReviewDetailHeader";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeLink } from "./ReviewPracticeLink";
import { ReviewRow, ReviewRowList, ReviewRowMeta } from "./ReviewRow";

export type ProposalRejectionReason = NonNullable<DecideFeedbackProposalRequest["rejectionReason"]>;

export interface ProposalReviewPageProps {
	workspaceSlug: string;
	feedback: GetPracticeReviewFeedbackResponse;
	practices?: Practice[];
	isDeciding?: boolean;
	onApprove: (feedbackId: string) => void;
	onReject: (feedbackId: string, reason?: ProposalRejectionReason, note?: string) => void;
}

const REJECTION_REASONS: Array<{ value: ProposalRejectionReason; label: string }> = [
	{ value: "INCORRECT", label: "Incorrect" },
	{ value: "MISSING_CONTEXT", label: "Missing important context" },
	{ value: "UNHELPFUL", label: "Not useful to the recipient" },
	{ value: "DUPLICATE", label: "Already covered elsewhere" },
	{ value: "INAPPROPRIATE_PLACEMENT", label: "Wrong delivery place" },
	{ value: "OTHER", label: "Something else" },
];

export function ProposalReviewPage({
	workspaceSlug,
	feedback,
	practices,
	isDeciding = false,
	onApprove,
	onReject,
}: ProposalReviewPageProps) {
	const place = DELIVERY_PLACE_DEFS[feedback.channel];
	const placements = Array.from(
		new Set(
			feedback.placements.map((placement) =>
				placementLabel(feedback.channel, placement.placementType),
			),
		),
	);
	const subjectDiffers = feedback.subject && feedback.subject.id !== feedback.recipient?.id;

	return (
		<article className="min-w-0 max-w-4xl space-y-8">
			<ReviewBreadcrumbs
				workspaceSlug={workspaceSlug}
				section={{
					label: "Delivery",
					link: (
						<Link
							to="/w/$workspaceSlug/admin/practices/reviews/delivery"
							params={{ workspaceSlug }}
							search={(previous) => previous}
						/>
					),
				}}
			/>
			<ReviewDetailHeader
				chips={
					<>
						<Badge variant="warning">
							<Clock3Icon />
							Awaiting approval
						</Badge>
						<StatusBadge def={place} />
					</>
				}
				title={`Feedback for ${subjectLabel(feedback.recipient)}`}
				provenance={
					<p className="max-w-2xl text-sm text-muted-foreground">
						Review the exact feedback, the work it addresses, and every observation behind it.
						Approval sends only this feedback.
					</p>
				}
			/>

			<ReviewFactGrid>
				<ReviewFact label={subjectDiffers ? "Addressed to" : "Developer"}>
					<div className="space-y-1">
						<ReviewPerson person={feedback.recipient} />
						{subjectDiffers && <ReviewPerson person={feedback.subject} prefix="About" />}
					</div>
				</ReviewFact>
				<ReviewFact label="Reviewed work">
					<div className="space-y-1">
						<ReviewArtifactLink artifact={feedback.artifact} />
						{feedback.artifact && (
							<p className="break-words text-muted-foreground">{feedback.artifact.title}</p>
						)}
					</div>
				</ReviewFact>
				<ReviewFact label="Will appear as">
					<div className="flex flex-wrap gap-1.5">
						{placements.length > 0 ? (
							placements.map((placement) => (
								<Badge key={placement} variant="outline">
									{placement}
								</Badge>
							))
						) : (
							<span className="text-muted-foreground">{place.label}</span>
						)}
					</div>
				</ReviewFact>
			</ReviewFactGrid>

			<section aria-labelledby="proposal-feedback-heading" className="space-y-3">
				<h3 id="proposal-feedback-heading" className="text-lg font-semibold">
					Feedback to send
				</h3>
				<FeedbackBody feedback={feedback} />
			</section>

			<section aria-labelledby="proposal-observations-heading" className="space-y-3">
				<div>
					<h3 id="proposal-observations-heading" className="text-lg font-semibold">
						Observations behind this feedback
					</h3>
					<p className="text-sm text-muted-foreground">
						Open an observation to inspect its rationale, citations, and source passages.
					</p>
				</div>
				<ReviewRowList label="Observations behind this feedback">
					{feedback.observations.map((observation) => (
						<ReviewRow
							key={observation.observationId}
							status={observationResult(observation)}
							title={
								<Link
									to="/w/$workspaceSlug/admin/practices/reviews/observations/$observationId"
									params={{ workspaceSlug, observationId: observation.observationId }}
									search={(previous) => previous}
								>
									{observation.summary}
								</Link>
							}
							meta={
								<ReviewRowMeta
									items={[
										<ReviewPracticeLink
											key="practice"
											workspaceSlug={workspaceSlug}
											practiceSlug={observation.practiceSlug}
											practiceName={observation.practiceName}
											group={observation.group}
											practice={practices?.find(
												(practice) => practice.slug === observation.practiceSlug,
											)}
										/>,
										observation.role === "PRIMARY"
											? "What this feedback is about"
											: "Supporting this feedback",
									]}
								/>
							}
							chips={[
								{
									key: "result",
									node: <ObservationResultBadge observation={observation} />,
								},
								{
									key: "currentness",
									node: <ClaimCurrentnessBadge currentness={observation.claimCurrentness} />,
								},
							]}
						/>
					))}
				</ReviewRowList>
			</section>

			<footer className="sticky bottom-0 z-20 flex flex-col-reverse gap-2 border-t bg-background/95 py-3 backdrop-blur supports-[backdrop-filter]:bg-background/85 sm:flex-row sm:items-center sm:justify-end">
				<RejectFeedbackPopover feedbackId={feedback.id} disabled={isDeciding} onReject={onReject} />
				<Button disabled={isDeciding} onClick={() => onApprove(feedback.id)}>
					{isDeciding ? <Spinner /> : <CheckIcon />} Approve and send
				</Button>
			</footer>
		</article>
	);
}

function RejectFeedbackPopover({
	feedbackId,
	disabled,
	onReject,
}: {
	feedbackId: string;
	disabled: boolean;
	onReject: ProposalReviewPageProps["onReject"];
}) {
	const [open, setOpen] = useState(false);
	const [reason, setReason] = useState<ProposalRejectionReason | "">("");
	const [note, setNote] = useState("");
	return (
		<Popover open={open} onOpenChange={setOpen}>
			<PopoverTrigger render={<Button variant="outline" disabled={disabled} />}>
				<CircleXIcon />
				Reject feedback
			</PopoverTrigger>
			<PopoverContent align="end" side="top" className="w-[min(24rem,calc(100vw-2rem))] gap-4 p-4">
				<PopoverHeader>
					<PopoverTitle>Reject this feedback</PopoverTitle>
					<PopoverDescription>
						The category supports quality review. Add a note when the category alone would not
						explain the problem.
					</PopoverDescription>
				</PopoverHeader>
				<RadioGroup
					value={reason}
					onValueChange={(value) => setReason(value)}
					aria-label="Rejection category"
					className="gap-1"
				>
					{REJECTION_REASONS.map((option) => (
						<label
							key={option.value}
							htmlFor={`rejection-${option.value}`}
							className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-muted has-data-checked:bg-muted"
						>
							<RadioGroupItem id={`rejection-${option.value}`} value={option.value} />
							<span>{option.label}</span>
						</label>
					))}
				</RadioGroup>
				<Field>
					<FieldLabel htmlFor="rejection-note">Note</FieldLabel>
					<Textarea
						id="rejection-note"
						value={note}
						onChange={(event) => setNote(event.target.value)}
						maxLength={500}
						placeholder="What should be corrected or reconsidered?"
					/>
					<FieldDescription>Optional · {note.length}/500</FieldDescription>
				</Field>
				<div className="flex justify-end gap-2 border-t pt-3">
					<Button variant="ghost" size="sm" onClick={() => setOpen(false)}>
						Cancel
					</Button>
					<Button
						variant="destructive"
						size="sm"
						onClick={() => onReject(feedbackId, reason || undefined, note.trim() || undefined)}
					>
						Reject feedback
					</Button>
				</div>
			</PopoverContent>
		</Popover>
	);
}
