import {
	CheckIcon,
	ChevronLeftIcon,
	CommentDiscussionIcon,
	CommentIcon,
	FileDiffIcon,
} from "@primer/octicons-react";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import type { ReviewedPullRequest } from "./ReviewsPopover";
import { ReviewsPopover } from "./ReviewsPopover";

export interface ActivityBadgesProps {
	reviewedPullRequests?: readonly ReviewedPullRequest[];
	changeRequests: number;
	approvals: number;
	comments: number;
	codeComments: number;
	highlightReviews?: boolean;
	className?: string;
	isLoading?: boolean;
}

export function ActivityBadges({
	reviewedPullRequests = [],
	changeRequests,
	approvals,
	comments,
	codeComments,
	highlightReviews = false,
	className,
	isLoading = false,
}: ActivityBadgesProps) {
	const hasActivity =
		reviewedPullRequests.length > 0 ||
		changeRequests > 0 ||
		approvals > 0 ||
		comments > 0 ||
		codeComments > 0;

	if (!hasActivity && !isLoading) return null;

	if (isLoading) {
		return (
			<div className={cn("flex items-center gap-2", className)}>
				{[40, 15, 25, 35, 30].map((width, index) => (
					<Skeleton key={`activity-badge-skeleton-${index}`} className="h-4" style={{ width }} />
				))}
			</div>
		);
	}

	return (
		<div className={cn("flex items-center gap-2", className)}>
			{reviewedPullRequests.length > 0 && (
				<>
					<ReviewsPopover reviewedPRs={reviewedPullRequests} highlight={highlightReviews} />
					<div className="flex items-center text-github-muted-foreground">
						<ChevronLeftIcon className="h-4 w-4" />
					</div>
				</>
			)}
			{changeRequests > 0 && (
				<Tooltip>
					<TooltipTrigger asChild>
						<div className="flex items-center gap-1 text-github-danger-foreground">
							<FileDiffIcon className="h-4 w-4" />
							<span>{changeRequests}</span>
						</div>
					</TooltipTrigger>
					<TooltipContent>Changes Requested</TooltipContent>
				</Tooltip>
			)}
			{approvals > 0 && (
				<Tooltip>
					<TooltipTrigger asChild>
						<div className="flex items-center gap-1 text-github-success-foreground">
							<CheckIcon className="h-4 w-4" />
							<span>{approvals}</span>
						</div>
					</TooltipTrigger>
					<TooltipContent>Approvals</TooltipContent>
				</Tooltip>
			)}
			{comments > 0 && (
				<Tooltip>
					<TooltipTrigger asChild>
						<div className="flex items-center gap-1 text-github-muted-foreground">
							<CommentIcon className="h-4 w-4" />
							<span>{comments}</span>
						</div>
					</TooltipTrigger>
					<TooltipContent>Comments</TooltipContent>
				</Tooltip>
			)}
			{codeComments > 0 && (
				<Tooltip>
					<TooltipTrigger asChild>
						<div className="flex items-center gap-1 text-github-muted-foreground">
							<CommentDiscussionIcon className="h-4 w-4" />
							<span>{codeComments}</span>
						</div>
					</TooltipTrigger>
					<TooltipContent>Code comments</TooltipContent>
				</Tooltip>
			)}
		</div>
	);
}
