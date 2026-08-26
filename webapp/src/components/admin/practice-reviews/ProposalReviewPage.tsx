import { Link } from "@tanstack/react-router";
import { CheckIcon, CircleXIcon, Clock3Icon, FileCode2Icon, ScanSearchIcon } from "lucide-react";
import { useId, useState } from "react";
import type {
	DecideFeedbackProposalRequest,
	GetPracticeReviewFeedbackResponse,
	Practice,
} from "@/api/types.gen";
import { UNTRUSTED_MARKDOWN_PROSE, UntrustedMarkdown } from "@/components/common/UntrustedMarkdown";
import { DELIVERY_PLACE_DEFS } from "@/components/practice-vocabulary/delivery-place-defs";
import { observationResult } from "@/components/practice-vocabulary/observation-result";
import { placementLabel } from "@/components/practice-vocabulary/placement-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
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
import { ReviewArtifactLink } from "./ReviewArtifact";
import { ClaimCurrentnessBadge, ObservationResultBadge } from "./ReviewBadges";
import { ReviewBreadcrumbs } from "./ReviewBreadcrumbs";
import { ReviewDetailHeader, ReviewFact, ReviewFactGrid } from "./ReviewDetailHeader";
import { ReviewPerson } from "./ReviewPerson";
import { ReviewPracticeLink } from "./ReviewPracticeLink";
import { ReviewRow, ReviewRowList, ReviewRowMeta } from "./ReviewRow";
import { subjectLabel } from "./review-format";

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
	const proposedPlacements = feedback.proposedPlacements;
	const packageUnavailable = proposedPlacements.length === 0;
	const summary = proposedPlacements.find((placement) => placement.type === "SUMMARY");
	const inline = proposedPlacements.filter((placement) => placement.type === "INLINE");
	const packageSummary = `${summary ? "1 summary" : "No summary"} and ${inline.length} ${
		inline.length === 1 ? "line comment" : "line comments"
	}`;
	const placements = Array.from(
		new Set(
			proposedPlacements.map((placement) => placementLabel(feedback.channel, placement.type)),
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
						Review the complete package before anything is sent. One decision covers the summary and
						every line comment below.
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
				{feedback.reviewedRevision && (
					<ReviewFact label="Reviewed revision">
						<code className="break-all text-xs">{feedback.reviewedRevision}</code>
					</ReviewFact>
				)}
			</ReviewFactGrid>

			<section aria-labelledby="proposal-feedback-heading" className="space-y-3">
				<div>
					<h3 id="proposal-feedback-heading" className="text-lg font-semibold">
						What will be sent
					</h3>
					<p className="text-sm text-muted-foreground">{packageSummary}</p>
				</div>
				{packageUnavailable && (
					<p role="alert" className="rounded-lg border border-destructive/40 p-3 text-sm">
						This review package is unavailable. Reject it or wait for a replacement; it cannot be
						sent safely.
					</p>
				)}
				{summary && <FeedbackBody feedback={{ ...feedback, body: summary.body }} />}
				{inline.length > 0 && (
					<Accordion
						multiple
						defaultValue={inline.map((_, index) => `inline-${index}`)}
						className="rounded-xl border px-4"
					>
						{inline.map((placement, index) => (
							<AccordionItem
								key={`${placement.path}:${placement.startLine}:${index}`}
								value={`inline-${index}`}
							>
								<AccordionTrigger className="gap-3 no-underline hover:no-underline">
									<span className="flex min-w-0 items-start gap-2">
										<FileCode2Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
										<span className="min-w-0">
											<span className="block break-all font-mono text-xs">{placement.path}</span>
											<span className="block text-xs font-normal text-muted-foreground">
												{placement.endLine && placement.endLine !== placement.startLine
													? `Lines ${placement.startLine}–${placement.endLine}`
													: `Line ${placement.startLine}`}
											</span>
										</span>
									</span>
								</AccordionTrigger>
								<AccordionContent className="min-w-0 pb-4 pl-6">
									<div className={`${UNTRUSTED_MARKDOWN_PROSE} min-w-0 break-words`}>
										<UntrustedMarkdown>{placement.body}</UntrustedMarkdown>
									</div>
								</AccordionContent>
							</AccordionItem>
						))}
					</Accordion>
				)}
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
				{feedback.observations.length === 0 ? (
					<Empty className="border">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<ScanSearchIcon />
							</EmptyMedia>
							<EmptyTitle>No observations are linked to this review</EmptyTitle>
							<EmptyDescription>
								The review package is still exact, but its supporting observations are unavailable.
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				) : (
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
												area={observation.area}
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
				)}
			</section>

			<footer className="sticky bottom-0 z-20 flex flex-col-reverse gap-2 border-t bg-background/95 py-3 backdrop-blur supports-[backdrop-filter]:bg-background/85 sm:flex-row sm:items-center sm:justify-end">
				<RejectFeedbackPopover feedbackId={feedback.id} disabled={isDeciding} onReject={onReject} />
				<Button disabled={isDeciding || packageUnavailable} onClick={() => onApprove(feedback.id)}>
					{isDeciding ? <Spinner /> : <CheckIcon />} Approve and send review
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
	const rejectionId = useId();
	const noteId = useId();
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
							htmlFor={`${rejectionId}-${option.value}`}
							className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-muted has-data-checked:bg-muted"
						>
							<RadioGroupItem id={`${rejectionId}-${option.value}`} value={option.value} />
							<span>{option.label}</span>
						</label>
					))}
				</RadioGroup>
				<Field>
					<FieldLabel htmlFor={noteId}>Note</FieldLabel>
					<Textarea
						id={noteId}
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
						disabled={disabled || !reason}
						onClick={() => reason && onReject(feedbackId, reason, note.trim() || undefined)}
					>
						Reject feedback
					</Button>
				</div>
			</PopoverContent>
		</Popover>
	);
}
