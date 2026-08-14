import { Link } from "@tanstack/react-router";
import { ChevronRightIcon, MessageSquareTextIcon } from "lucide-react";
import type { ReviewFeedback } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import {
	type DeliveryFacts,
	deliveryOutcome,
} from "@/components/practice-vocabulary/delivery-outcome-defs";
import { DELIVERY_PLACE_DEFS } from "@/components/practice-vocabulary/delivery-place-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { withholdingReasonSentence } from "@/components/practice-vocabulary/withholding-defs";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import {
	Item,
	ItemActions,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemTitle,
} from "@/components/ui/item";
import {
	Table,
	TableBody,
	TableCaption,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { ReviewArtifact, ReviewArtifactLink } from "./ReviewArtifact";
import { ReviewResultsSkeleton } from "./ReviewResultsSkeleton";
import { subjectLabel } from "./review-format";

export type FeedbackResultsState =
	| { status: "loading" }
	| { status: "empty"; filtered: boolean }
	| { status: "ready"; feedback: ReviewFeedback[] };

export interface FeedbackResultsProps {
	workspaceSlug: string;
	state: FeedbackResultsState;
}

/**
 * The delivery of one piece of feedback, as the two axes stacked rather than folded together.
 *
 * The cell this replaces printed the withholding reason *or* the place, whichever was set — so a
 * reader could not tell whether they were being told what happened or where it would have happened,
 * and a delivered row never said where it went at all. Outcome first because it is what the column
 * is called, then where, then why if a gate had a say.
 */
function DeliveryCell({ feedback, className }: { feedback: DeliveryFacts; className?: string }) {
	return (
		<div className={cn("flex flex-col items-start gap-1", className)}>
			<StatusBadge def={deliveryOutcome(feedback)} />
			<p className="max-w-52 text-xs text-muted-foreground">
				{DELIVERY_PLACE_DEFS[feedback.channel].label}
			</p>
			{feedback.suppressionReason && (
				<p className="max-w-52 text-xs text-muted-foreground">
					{withholdingReasonSentence(feedback.suppressionReason)}
				</p>
			)}
		</div>
	);
}

export function FeedbackResults({ workspaceSlug, state }: FeedbackResultsProps) {
	if (state.status === "loading") return <ReviewResultsSkeleton label="Loading feedback" />;
	if (state.status === "empty") {
		return (
			<Empty className="border">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<MessageSquareTextIcon />
					</EmptyMedia>
					<EmptyTitle>
						{state.filtered ? "No feedback matches these filters" : "No feedback yet"}
					</EmptyTitle>
					<EmptyDescription>
						{state.filtered
							? "Try removing a filter to broaden the results."
							: "Delivered and withheld feedback appears here after reviews run."}
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}
	const { feedback } = state;

	return (
		<>
			<div className="hidden xl:block">
				<Table containerClassName="rounded-lg border">
					<TableCaption className="sr-only">Feedback, newest first</TableCaption>
					<TableHeader>
						<TableRow>
							<TableHead scope="col">Feedback</TableHead>
							<TableHead scope="col">Outcome</TableHead>
							<TableHead scope="col">Reviewed work</TableHead>
							<TableHead scope="col" className="w-32">
								Composed
							</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{feedback.map((item) => (
							<TableRow key={item.id}>
								<TableCell className="max-w-md whitespace-normal align-top">
									<Link
										to="/w/$workspaceSlug/admin/practices/reviews/delivery/$feedbackId"
										params={{ workspaceSlug, feedbackId: item.id }}
										search={(previous) => previous}
										className="line-clamp-2 font-medium hover:underline"
									>
										Feedback for {subjectLabel(item.recipient)}
									</Link>
									{item.bodyPreview && (
										<p className="mt-1 line-clamp-2 text-sm">{item.bodyPreview}</p>
									)}
									<div className="mt-2 text-xs text-muted-foreground">
										<span>
											{item.observationCount}{" "}
											{item.observationCount === 1 ? "observation" : "observations"}
										</span>
									</div>
								</TableCell>
								<TableCell className="whitespace-normal align-top">
									<DeliveryCell feedback={item} />
								</TableCell>
								<TableCell className="max-w-xs whitespace-normal align-top">
									<ReviewArtifactLink artifact={item.artifact} />
								</TableCell>
								<TableCell className="align-top text-muted-foreground">
									<RelativeTime value={item.createdAt} />
								</TableCell>
							</TableRow>
						))}
					</TableBody>
				</Table>
			</div>
			<ItemGroup className="xl:hidden">
				{feedback.map((item) => (
					<div key={item.id} role="listitem">
						<Item
							variant="outline"
							className="items-start"
							render={
								<Link
									to="/w/$workspaceSlug/admin/practices/reviews/delivery/$feedbackId"
									params={{ workspaceSlug, feedbackId: item.id }}
									search={(previous) => previous}
								/>
							}
						>
							<ItemContent className="min-w-0">
								<ItemTitle className="w-full min-w-0 line-clamp-none break-words">
									Feedback for {subjectLabel(item.recipient)}
								</ItemTitle>
								{item.bodyPreview && (
									<ItemDescription className="line-clamp-2 text-sm text-foreground">
										{item.bodyPreview}
									</ItemDescription>
								)}
								<p className="text-xs text-muted-foreground">
									{item.observationCount}{" "}
									{item.observationCount === 1 ? "observation" : "observations"} · composed{" "}
									<RelativeTime value={item.createdAt} />
								</p>
								<DeliveryCell feedback={item} className="mt-1" />
								<ReviewArtifact artifact={item.artifact} display="full" />
							</ItemContent>
							<ItemActions>
								<ChevronRightIcon className="size-4 text-muted-foreground" aria-hidden />
							</ItemActions>
						</Item>
					</div>
				))}
			</ItemGroup>
		</>
	);
}
