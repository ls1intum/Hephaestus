import {
	ArrowUpRightIcon,
	CheckIcon,
	Clock3Icon,
	FileCode2Icon,
	ShieldCheckIcon,
	XIcon,
} from "lucide-react";
import { useState } from "react";
import type { DecideFeedbackProposalRequest } from "@/api/types.gen";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Spinner } from "@/components/ui/spinner";
import { FeedbackBody } from "./FeedbackBody";

export type ProposalRejectionReason = NonNullable<DecideFeedbackProposalRequest["rejectionReason"]>;

export interface FeedbackProposal {
	id: string;
	practiceNames: string[];
	recipientName: string;
	body: string;
	artifact?: {
		label: string;
		title: string;
		repositoryName: string;
		url?: string;
	};
	placement: string;
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
	{ value: "INCORRECT", label: "The feedback is incorrect" },
	{ value: "MISSING_CONTEXT", label: "It is missing important context" },
	{ value: "UNHELPFUL", label: "It would not be useful" },
	{ value: "DUPLICATE", label: "It repeats feedback already given" },
	{ value: "INAPPROPRIATE_PLACEMENT", label: "It is aimed at the wrong place" },
	{ value: "OTHER", label: "Another reason" },
];

export function ProposalReviewPage({
	proposal,
	isDeciding = false,
	onApprove,
	onReject,
}: ProposalReviewPageProps) {
	return (
		<article className="mx-auto max-w-5xl space-y-5">
			<header className="space-y-2 border-b pb-5">
				<div className="space-y-2">
					<div className="flex flex-wrap items-center gap-2">
						<Badge variant="secondary">
							<Clock3Icon />
							Awaiting approval
						</Badge>
						<span className="text-sm text-muted-foreground">Ready for your decision</span>
					</div>
					<h2 className="text-2xl font-semibold tracking-tight">
						Review feedback for {proposal.recipientName}
					</h2>
					<p className="text-sm text-muted-foreground">
						Check the evidence and exact message before it is sent.
					</p>
				</div>
			</header>

			<div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem]">
				<div className="space-y-5">
					<section aria-labelledby="proposal-message-heading" className="space-y-3">
						<div className="flex flex-wrap items-center justify-between gap-2">
							<h3 id="proposal-message-heading" className="font-semibold">
								Message that will be sent
							</h3>
							<Badge variant="outline">{proposal.placement}</Badge>
						</div>
						<FeedbackBody
							feedback={{
								body: proposal.body,
								channel: "IN_CONTEXT",
								deliveryState: "AWAITING_APPROVAL",
							}}
						/>
					</section>

					<section aria-labelledby="proposal-evidence-heading" className="space-y-3">
						<div className="flex items-center gap-2">
							<ShieldCheckIcon className="size-4 text-muted-foreground" aria-hidden="true" />
							<h3 id="proposal-evidence-heading" className="font-semibold">
								Evidence to verify
							</h3>
						</div>
						<ul className="space-y-3" aria-label="Observations behind this proposal">
							{proposal.evidence.map((evidence) => (
								<li key={evidence.id} className="rounded-lg border p-4">
									<div className="mb-2 flex items-center gap-2 text-sm font-medium">
										<FileCode2Icon className="size-4" aria-hidden="true" />
										<span>{evidence.practiceName}</span>
									</div>
									<blockquote className="border-l-2 pl-3 text-sm leading-6 text-muted-foreground">
										{evidence.excerpt}
									</blockquote>
									{evidence.url && (
										<a
											className="mt-2 inline-block text-sm font-medium underline underline-offset-4"
											href={evidence.url}
										>
											Inspect observation and source evidence
										</a>
									)}
								</li>
							))}
						</ul>
					</section>
				</div>

				<aside className="space-y-4 lg:sticky lg:top-5 lg:self-start">
					{proposal.artifact && (
						<Card>
							<CardHeader>
								<CardTitle className="text-base">Reviewed work</CardTitle>
							</CardHeader>
							<CardContent className="space-y-3 text-sm">
								<div>
									<p className="font-medium">{proposal.artifact.label}</p>
									<p className="mt-1 text-muted-foreground">{proposal.artifact.title}</p>
								</div>
								<p className="text-muted-foreground">{proposal.artifact.repositoryName}</p>
								{proposal.artifact.url && (
									<a
										href={proposal.artifact.url}
										target="_blank"
										rel="noreferrer"
										className="inline-flex items-center gap-1 font-medium underline underline-offset-4"
									>
										Open reviewed work
										<ArrowUpRightIcon className="size-3.5" aria-hidden="true" />
									</a>
								)}
							</CardContent>
						</Card>
					)}
					<div className="rounded-lg border p-4 text-sm">
						<p className="font-medium">
							{proposal.practiceNames.length === 1 ? "Practice" : "Practices"}
						</p>
						<ul className="mt-1 space-y-1 text-muted-foreground">
							{proposal.practiceNames.map((practiceName) => (
								<li key={practiceName}>{practiceName}</li>
							))}
						</ul>
					</div>
				</aside>
			</div>

			<footer className="sticky bottom-3 z-10 flex flex-col-reverse gap-2 rounded-xl border bg-background/95 p-3 shadow-lg backdrop-blur sm:flex-row sm:justify-end">
				<RejectProposalDialog proposal={proposal} disabled={isDeciding} onReject={onReject} />
				<Button
					aria-label="Approve and send"
					disabled={isDeciding}
					onClick={() => onApprove(proposal.id)}
				>
					{isDeciding ? <Spinner /> : <CheckIcon />} Approve and send
				</Button>
			</footer>
		</article>
	);
}

function RejectProposalDialog({
	proposal,
	disabled,
	onReject,
}: {
	proposal: FeedbackProposal;
	disabled: boolean;
	onReject: ProposalReviewPageProps["onReject"];
}) {
	const [reason, setReason] = useState<ProposalRejectionReason | "">("");
	return (
		<AlertDialog>
			<AlertDialogTrigger render={<Button variant="outline" disabled={disabled} />}>
				<XIcon />
				Reject proposal
			</AlertDialogTrigger>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>Reject this proposal?</AlertDialogTitle>
					<AlertDialogDescription>
						You can optionally choose a category to help improve this practice.
					</AlertDialogDescription>
				</AlertDialogHeader>
				<RadioGroup
					value={reason}
					onValueChange={(value) => setReason(value as ProposalRejectionReason)}
					aria-label="Rejection reason"
					className="gap-2"
				>
					{REJECTION_REASONS.map((option) => (
						<label
							key={option.value}
							htmlFor={`rejection-${option.value}`}
							className="flex cursor-pointer items-start gap-3 rounded-md border p-3 text-sm has-data-checked:border-primary has-data-checked:bg-muted/50"
						>
							<RadioGroupItem id={`rejection-${option.value}`} value={option.value} />
							<span>{option.label}</span>
						</label>
					))}
				</RadioGroup>
				<AlertDialogFooter>
					<AlertDialogCancel>Keep reviewing</AlertDialogCancel>
					<AlertDialogAction
						variant="destructive"
						onClick={() => onReject(proposal.id, reason || undefined)}
					>
						Reject proposal
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
}
