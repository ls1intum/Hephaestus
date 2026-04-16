import {
	CheckIcon,
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
	const hasScoredActivity =
		reviewedPullRequests.length > 0 ||
		changeRequests > 0 ||
		approvals > 0 ||
		comments > 0 ||
		codeComments > 0;

	const hasActivity = hasScoredActivity || hasVisibleOnlyActivity;

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
				Activity badges. Reviews affect score. The rest is shown for context.
			</span>
			{reviewedPullRequests.length > 0 && (
				<ReviewsPopover
					reviewedPullRequests={reviewedPullRequests}
					highlight={highlightReviews}
					providerType={providerType}
				/>
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
					<TooltipContent>Review comments</TooltipContent>
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
						Inline comments on someone else&apos;s {terms.pullRequests.toLowerCase()}. Counts toward
						score.
					</TooltipContent>
				</Tooltip>
			)}
			{hasScoredActivity && hasVisibleOnlyActivity && (
				<>
					<div className="mx-1 h-4 border-l border-border" aria-hidden="true" />
					<span className="text-xs text-muted-foreground">Also shown</span>
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
						Replies on your own {terms.pullRequests.toLowerCase()}. Doesn&apos;t affect score.
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
						Your open {terms.pullRequests.toLowerCase()}. Doesn&apos;t affect score.
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
						Your merged {terms.pullRequests.toLowerCase()}. Doesn&apos;t affect score.
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
						Your closed {terms.pullRequests.toLowerCase()}. Doesn&apos;t affect score.
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
					<TooltipContent>Issues you opened. Doesn&apos;t affect score.</TooltipContent>
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
					<TooltipContent>Issues you closed. Doesn&apos;t affect score.</TooltipContent>
				</Tooltip>
			)}
		</div>
	);
}
