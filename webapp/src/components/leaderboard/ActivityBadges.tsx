import {
	CheckIcon,
	ChevronLeftIcon,
	CommentDiscussionIcon,
	CommentIcon,
	FileDiffIcon,
	IssueClosedIcon,
	IssueOpenedIcon,
} from "@primer/octicons-react";
import { MessageSquareReply } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { getProviderTerms, getPullRequestStateIcon, type ProviderType } from "@/lib/provider";
import { cn } from "@/lib/utils";
import type { ReviewedPullRequest } from "./ReviewsPopover";
import { ReviewsPopover } from "./ReviewsPopover";

export interface ActivityBadgesProps {
	reviewedPullRequests?: readonly ReviewedPullRequest[];
	changeRequests: number;
	approvals: number;
	comments: number;
	codeComments: number;
	ownReplies?: number;
	openPullRequests?: number;
	mergedPullRequests?: number;
	closedPullRequests?: number;
	openedIssues?: number;
	closedIssues?: number;
	highlightReviews?: boolean;
	className?: string;
	isLoading?: boolean;
	providerType?: ProviderType;
}

export function ActivityBadges({
	reviewedPullRequests = [],
	changeRequests,
	approvals,
	comments,
	codeComments,
	ownReplies = 0,
	openPullRequests = 0,
	mergedPullRequests = 0,
	closedPullRequests = 0,
	openedIssues = 0,
	closedIssues = 0,
	highlightReviews = false,
	className,
	isLoading = false,
	providerType = "GITHUB",
}: ActivityBadgesProps) {
	const terms = getProviderTerms(providerType);
	const { icon: OpenPrIcon, colorClass: openPrColorClass } = getPullRequestStateIcon(
		providerType,
		"OPEN",
	);
	const { icon: MergedPrIcon, colorClass: mergedPrColorClass } = getPullRequestStateIcon(
		providerType,
		"MERGED",
	);
	const { icon: ClosedPrIcon, colorClass: closedPrColorClass } = getPullRequestStateIcon(
		providerType,
		"CLOSED",
	);
	const hasVisibleOnlyActivity =
		ownReplies > 0 ||
		openPullRequests > 0 ||
		mergedPullRequests > 0 ||
		closedPullRequests > 0 ||
		openedIssues > 0 ||
		closedIssues > 0;

	const hasActivity =
		reviewedPullRequests.length > 0 ||
		changeRequests > 0 ||
		approvals > 0 ||
		comments > 0 ||
		codeComments > 0 ||
		hasVisibleOnlyActivity;

	if (!hasActivity && !isLoading) return null;

	if (isLoading) {
		return (
			<div className={cn("flex items-center gap-2", className)}>
				{[40, 15, 25, 35, 30, 30, 30].map((width, index) => (
					<Skeleton key={`activity-badge-skeleton-${index}`} className="h-4" style={{ width }} />
				))}
			</div>
		);
	}

	return (
		<div className={cn("flex items-center gap-2", className)}>
			<span className="sr-only">
				Activity badges. Review badges affect score. Badges after the divider are visible only.
			</span>
			{reviewedPullRequests.length > 0 && (
				<>
					<ReviewsPopover
						reviewedPullRequests={reviewedPullRequests}
						highlight={highlightReviews}
						providerType={providerType}
					/>
					<div className="flex items-center text-provider-muted-foreground">
						<ChevronLeftIcon className="h-4 w-4" />
					</div>
				</>
			)}
			{changeRequests > 0 && (
				<Tooltip>
					<TooltipTrigger
						className="cursor-help"
						aria-label={`${changeRequests} changes requested. Counts toward score.`}
						render={<div className="flex items-center gap-1 text-provider-danger-foreground" />}
					>
						<FileDiffIcon className="h-4 w-4" />
						<span>{changeRequests}</span>
					</TooltipTrigger>
					<TooltipContent>Changes Requested</TooltipContent>
				</Tooltip>
			)}
			{approvals > 0 && (
				<Tooltip>
					<TooltipTrigger
						className="cursor-help"
						aria-label={`${approvals} approvals. Counts toward score.`}
						render={<div className="flex items-center gap-1 text-provider-success-foreground" />}
					>
						<CheckIcon className="h-4 w-4" />
						<span>{approvals}</span>
					</TooltipTrigger>
					<TooltipContent>Approvals</TooltipContent>
				</Tooltip>
			)}
			{comments > 0 && (
				<Tooltip>
					<TooltipTrigger
						className="cursor-help"
						aria-label={`${comments} comment reviews. Counts toward score.`}
						render={<div className="flex items-center gap-1 text-provider-muted-foreground" />}
					>
						<CommentIcon className="h-4 w-4" />
						<span>{comments}</span>
					</TooltipTrigger>
					<TooltipContent>Scored. Comment-only review submissions.</TooltipContent>
				</Tooltip>
			)}
			{codeComments > 0 && (
				<Tooltip>
					<TooltipTrigger
						className="cursor-help"
						aria-label={`${codeComments} scored inline feedback comments on pull requests authored by someone else. Counts toward score.`}
						render={<div className="flex items-center gap-1 text-provider-muted-foreground" />}
					>
						<CommentDiscussionIcon className="h-4 w-4" />
						<span>{codeComments}</span>
					</TooltipTrigger>
					<TooltipContent>
						Scored. Inline feedback on {terms.pullRequests.toLowerCase()} authored by someone else.
					</TooltipContent>
				</Tooltip>
			)}
			{hasVisibleOnlyActivity && (
				<>
					<div className="mx-1 h-4 border-l border-border" aria-hidden="true" />
					<span className="text-xs text-muted-foreground">Visible only</span>
				</>
			)}
			{ownReplies > 0 && (
				<Tooltip>
					<TooltipTrigger
						className="cursor-help"
						aria-label={`${ownReplies} own pull request discussion or inline replies. Visible only and does not affect score.`}
						render={<div className="flex items-center gap-1 text-provider-muted-foreground" />}
					>
						<MessageSquareReply className="h-4 w-4" />
						<span>{ownReplies}</span>
					</TooltipTrigger>
					<TooltipContent>
						Visible only. Replies in {terms.pullRequestShort} discussion or inline threads on{" "}
						{terms.pullRequests.toLowerCase()} you authored. Does not affect score.
					</TooltipContent>
				</Tooltip>
			)}
			{openPullRequests > 0 && (
				<Tooltip>
					<TooltipTrigger
						className="cursor-help"
						aria-label={`${openPullRequests} open ${terms.pullRequests.toLowerCase()} you authored. Visible only and does not affect score.`}
						render={<div className={cn("flex items-center gap-1", openPrColorClass)} />}
					>
						<OpenPrIcon className="h-4 w-4" />
						<span>{openPullRequests}</span>
					</TooltipTrigger>
					<TooltipContent>
						Visible only. {terms.pullRequests} you authored in this timeframe that are still open.
						Does not affect score.
					</TooltipContent>
				</Tooltip>
			)}
			{mergedPullRequests > 0 && (
				<Tooltip>
					<TooltipTrigger
						className="cursor-help"
						aria-label={`${mergedPullRequests} merged ${terms.pullRequests.toLowerCase()} you authored. Visible only and does not affect score.`}
						render={<div className={cn("flex items-center gap-1", mergedPrColorClass)} />}
					>
						<MergedPrIcon className="h-4 w-4" />
						<span>{mergedPullRequests}</span>
					</TooltipTrigger>
					<TooltipContent>
						Visible only. {terms.pullRequests} you authored that were merged in this timeframe. Does
						not affect score.
					</TooltipContent>
				</Tooltip>
			)}
			{closedPullRequests > 0 && (
				<Tooltip>
					<TooltipTrigger
						className="cursor-help"
						aria-label={`${closedPullRequests} closed ${terms.pullRequests.toLowerCase()} you authored. Visible only and does not affect score.`}
						render={<div className={cn("flex items-center gap-1", closedPrColorClass)} />}
					>
						<ClosedPrIcon className="h-4 w-4" />
						<span>{closedPullRequests}</span>
					</TooltipTrigger>
					<TooltipContent>
						Visible only. {terms.pullRequests} you authored that were closed without merge in this
						timeframe. Does not affect score.
					</TooltipContent>
				</Tooltip>
			)}
			{openedIssues > 0 && (
				<Tooltip>
					<TooltipTrigger
						className="cursor-help"
						aria-label={`${openedIssues} opened issues. Visible only and does not affect score.`}
						render={<div className="flex items-center gap-1 text-provider-open-foreground" />}
					>
						<IssueOpenedIcon className="h-4 w-4" />
						<span>{openedIssues}</span>
					</TooltipTrigger>
					<TooltipContent>
						Visible only. Issues you opened in this timeframe. Does not affect score.
					</TooltipContent>
				</Tooltip>
			)}
			{closedIssues > 0 && (
				<Tooltip>
					<TooltipTrigger
						className="cursor-help"
						aria-label={`${closedIssues} closed issues. Visible only and does not affect score.`}
						render={<div className="flex items-center gap-1 text-provider-closed-foreground" />}
					>
						<IssueClosedIcon className="h-4 w-4" />
						<span>{closedIssues}</span>
					</TooltipTrigger>
					<TooltipContent>
						Visible only. Issues you closed in this timeframe. Does not affect score.
					</TooltipContent>
				</Tooltip>
			)}
		</div>
	);
}
