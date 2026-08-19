import {
	ArrowUpRightIcon,
	CheckIcon,
	CircleXIcon,
	Clock3Icon,
	FileCode2Icon,
	ShieldCheckIcon,
} from "lucide-react";
import { useState } from "react";
import type { DecideFeedbackProposalRequest } from "@/api/types.gen";
import {
	DELIVERY_PLACE_DEFS,
	type DeliveryPlace,
} from "@/components/practice-vocabulary/delivery-place-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Item,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemMedia,
	ItemTitle,
} from "@/components/ui/item";
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
import { FeedbackBody } from "./FeedbackBody";

export type ProposalRejectionReason = NonNullable<DecideFeedbackProposalRequest["rejectionReason"]>;

export interface FeedbackProposal {
	id: string;
	practiceNames: string[];
	recipientName: string;
	body: string;
	deliveryPlace: DeliveryPlace;
	placements: string[];
	artifact?: {
		label: string;
		title: string;
		repositoryName: string;
		url?: string;
	};
	evidence: Array<{
		id: string;
		practiceName: string;
		excerpt: string;
		url?: string;
	}>;
}

export interface ProposalReviewPageProps {
	proposal: FeedbackProposal;
	isDeciding?: boolean;
	onApprove: (proposalId: string) => void;
	onReject: (proposalId: string, reason?: ProposalRejectionReason) => void;
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
	proposal,
	isDeciding = false,
	onApprove,
	onReject,
}: ProposalReviewPageProps) {
	const place = DELIVERY_PLACE_DEFS[proposal.deliveryPlace];
	return (
		<article className="mx-auto max-w-5xl space-y-6">
			<header className="space-y-3 border-b pb-5">
				<div className="flex flex-wrap items-center gap-2">
					<Badge variant="warning">
						<Clock3Icon />
						Awaiting approval
					</Badge>
					<StatusBadge def={place} />
				</div>
				<div className="space-y-1.5">
					<h2 className="text-2xl font-semibold tracking-tight">
						Review feedback for {proposal.recipientName}
					</h2>
					<p className="max-w-3xl text-sm text-muted-foreground">
						Approve or reject this complete {place.label.toLowerCase()} feedback unit. Approval
						sends exactly what appears below; it does not approve other feedback from the same
						review.
					</p>
				</div>
			</header>

			<div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_19rem]">
				<div className="space-y-7">
					<section aria-labelledby="proposal-feedback-heading" className="space-y-3">
						<div className="space-y-2">
							<h3 id="proposal-feedback-heading" className="text-lg font-semibold">
								Feedback to send
							</h3>
							<div className="flex flex-wrap gap-1.5" role="group" aria-label="Planned placements">
								{proposal.placements.map((placement) => (
									<Badge key={placement} variant="outline">
										{placement}
									</Badge>
								))}
							</div>
						</div>
						<FeedbackBody
							feedback={{
								body: proposal.body,
								channel: proposal.deliveryPlace,
								deliveryState: "AWAITING_APPROVAL",
							}}
						/>
					</section>

					<section aria-labelledby="proposal-evidence-heading" className="space-y-3">
						<div className="flex items-center gap-2">
							<ShieldCheckIcon className="size-4 text-muted-foreground" aria-hidden />
							<h3 id="proposal-evidence-heading" className="text-lg font-semibold">
								Evidence to verify
							</h3>
						</div>
						<ItemGroup aria-label="Observations behind this feedback">
							{proposal.evidence.map((evidence) => (
								<Item key={evidence.id} variant="outline" role="listitem">
									<ItemMedia variant="icon">
										<FileCode2Icon />
									</ItemMedia>
									<ItemContent>
										<ItemTitle>{evidence.practiceName}</ItemTitle>
										<ItemDescription className="line-clamp-none">
											{evidence.excerpt}
										</ItemDescription>
										{evidence.url && (
											<a
												className="w-fit text-sm font-medium underline underline-offset-4"
												href={evidence.url}
											>
												Inspect observation and source evidence
											</a>
										)}
									</ItemContent>
								</Item>
							))}
						</ItemGroup>
					</section>
				</div>

				<aside className="space-y-5 lg:sticky lg:top-5 lg:self-start">
					{proposal.artifact && (
						<section aria-labelledby="reviewed-work-heading" className="space-y-2">
							<h3 id="reviewed-work-heading" className="text-sm font-semibold">
								Reviewed work
							</h3>
							<div className="space-y-1 text-sm">
								<p className="font-medium">{proposal.artifact.label}</p>
								<p className="text-muted-foreground">{proposal.artifact.title}</p>
								<p className="text-muted-foreground">{proposal.artifact.repositoryName}</p>
								{proposal.artifact.url && (
									<a
										href={proposal.artifact.url}
										target="_blank"
										rel="noreferrer"
										className="inline-flex items-center gap-1 font-medium underline underline-offset-4"
									>
										Open reviewed work
										<ArrowUpRightIcon className="size-3.5" aria-hidden />
									</a>
								)}
							</div>
						</section>
					)}
					<section aria-labelledby="proposal-practices-heading" className="space-y-2 border-t pt-4">
						<h3 id="proposal-practices-heading" className="text-sm font-semibold">
							{proposal.practiceNames.length === 1 ? "Practice" : "Practices"}
						</h3>
						<ul className="space-y-1 text-sm text-muted-foreground">
							{proposal.practiceNames.map((practiceName) => (
								<li key={practiceName}>{practiceName}</li>
							))}
						</ul>
					</section>
				</aside>
			</div>

			<footer className="sticky bottom-0 z-10 flex flex-col-reverse gap-2 border-t bg-background/95 py-3 backdrop-blur sm:flex-row sm:justify-end">
				<RejectProposalPopover proposal={proposal} disabled={isDeciding} onReject={onReject} />
				<Button disabled={isDeciding} onClick={() => onApprove(proposal.id)}>
					{isDeciding ? <Spinner /> : <CheckIcon />} Approve and send
				</Button>
			</footer>
		</article>
	);
}

function RejectProposalPopover({
	proposal,
	disabled,
	onReject,
}: {
	proposal: FeedbackProposal;
	disabled: boolean;
	onReject: ProposalReviewPageProps["onReject"];
}) {
	const [open, setOpen] = useState(false);
	const [reason, setReason] = useState<ProposalRejectionReason | "">("");
	return (
		<Popover open={open} onOpenChange={setOpen}>
			<PopoverTrigger render={<Button variant="outline" disabled={disabled} />}>
				<CircleXIcon />
				Reject feedback
			</PopoverTrigger>
			<PopoverContent align="end" side="top" className="w-80 gap-3 p-3">
				<PopoverHeader>
					<PopoverTitle>Why should this not be sent?</PopoverTitle>
					<PopoverDescription>
						A category is optional. It helps distinguish quality problems from duplicate or
						misplaced feedback.
					</PopoverDescription>
				</PopoverHeader>
				<RadioGroup
					value={reason}
					onValueChange={(value) => setReason(value as ProposalRejectionReason)}
					aria-label="Rejection reason"
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
				<div className="flex justify-end gap-2 border-t pt-3">
					<Button variant="ghost" size="sm" onClick={() => setOpen(false)}>
						Cancel
					</Button>
					<Button
						variant="destructive"
						size="sm"
						onClick={() => onReject(proposal.id, reason || undefined)}
					>
						Reject feedback
					</Button>
				</div>
			</PopoverContent>
		</Popover>
	);
}
