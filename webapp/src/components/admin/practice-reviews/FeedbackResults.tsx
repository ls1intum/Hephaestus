import { Link } from "@tanstack/react-router";
import { ChevronRightIcon, MessageSquareTextIcon } from "lucide-react";
import type { ReviewFeedback } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
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
import { Skeleton } from "@/components/ui/skeleton";
import {
	Table,
	TableBody,
	TableCaption,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { ReviewArtifact, ReviewArtifactLink } from "./ReviewArtifact";
import { FeedbackStateBadge } from "./ReviewBadges";
import { CHANNEL_LABELS, SUPPRESSION_REASON_LABELS, subjectLabel } from "./review-format";

export type FeedbackResultsState =
	| { status: "loading" }
	| { status: "empty"; filtered: boolean }
	| { status: "ready"; feedback: ReviewFeedback[] };

export interface FeedbackResultsProps {
	workspaceSlug: string;
	state: FeedbackResultsState;
}

export function FeedbackResults({ workspaceSlug, state }: FeedbackResultsProps) {
	if (state.status === "loading") return <FeedbackListSkeleton />;
	if (state.status === "empty") {
		return (
			<Empty className="border">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<MessageSquareTextIcon />
					</EmptyMedia>
					<EmptyTitle>
						{state.filtered ? "No messages match these filters" : "No messages yet"}
					</EmptyTitle>
					<EmptyDescription>
						{state.filtered
							? "Try removing a filter to broaden the results."
							: "Delivered and withheld messages appear here after reviews run."}
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
					<TableCaption className="sr-only">Feedback messages, newest first</TableCaption>
					<TableHeader>
						<TableRow>
							<TableHead scope="col">Message</TableHead>
							<TableHead scope="col">Delivery</TableHead>
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
										Message for {subjectLabel(item.recipient)}
									</Link>
									{item.bodyPreview && (
										<p className="mt-1 line-clamp-2 text-sm">{item.bodyPreview}</p>
									)}
									<div className="mt-2 text-xs text-muted-foreground">
										<span>
											{item.findingCount} {item.findingCount === 1 ? "finding" : "findings"}
										</span>
									</div>
								</TableCell>
								<TableCell className="whitespace-normal align-top">
									<FeedbackStateBadge state={item.deliveryState} />
									<p className="mt-1 max-w-52 text-xs text-muted-foreground">
										{item.suppressionReason
											? SUPPRESSION_REASON_LABELS[item.suppressionReason]
											: CHANNEL_LABELS[item.channel]}
									</p>
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
									Message for {subjectLabel(item.recipient)}
								</ItemTitle>
								{item.bodyPreview && (
									<ItemDescription className="line-clamp-2 text-sm text-foreground">
										{item.bodyPreview}
									</ItemDescription>
								)}
								<p className="text-xs text-muted-foreground">
									{item.findingCount} {item.findingCount === 1 ? "finding" : "findings"}
								</p>
								<div className="mt-1 flex flex-wrap items-center gap-2">
									<FeedbackStateBadge state={item.deliveryState} />
									<RelativeTime value={item.createdAt} />
								</div>
								<p className="text-xs text-muted-foreground">
									{item.suppressionReason
										? SUPPRESSION_REASON_LABELS[item.suppressionReason]
										: CHANNEL_LABELS[item.channel]}
								</p>
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

function FeedbackListSkeleton() {
	return (
		<div className="space-y-2 rounded-lg border p-4" role="status">
			<span className="sr-only">Loading messages</span>
			{Array.from({ length: 5 }, (_, index) => (
				<div key={index} className="flex items-center gap-4 py-3">
					<Skeleton className="h-4 flex-1" />
					<Skeleton className="h-5 w-24" />
					<Skeleton className="hidden h-4 w-48 md:block" />
				</div>
			))}
		</div>
	);
}
