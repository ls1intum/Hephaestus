import { CodeReviewIcon, GitPullRequestIcon } from "@primer/octicons-react";
import { Link } from "@tanstack/react-router";
import type {
	LeaderboardEntry,
	PullRequestInfo,
	PullRequestReviewInfo,
} from "@/api/types.gen";
import { ActivityBadges } from "@/components/leaderboard/ActivityBadges";
import { Button } from "@/components/ui/button";
import { EmptyState } from "../shared/EmptyState";
import { IssueCard } from "../shared/IssueCard";
import { ProfileActivityFilter } from "./ProfileActivityFilter";
import { ReviewActivityCard } from "./ReviewActivityCard";

export interface ProfileContentProps {
	reviewActivity?: PullRequestReviewInfo[];
	openPullRequests?: PullRequestInfo[];
	isLoading: boolean;
	username: string;
	displayName?: string;
	currUserIsDashboardUser: boolean;
	workspaceSlug: string;
	afterDate?: string;
	beforeDate?: string;
	onTimeframeChange?: (afterDate: string, beforeDate?: string) => void;
}

const parseDate = (value?: string) => {
	if (!value) return undefined;
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? undefined : parsed;
};

const isWithinRange = (date?: Date, after?: Date, before?: Date) => {
	if (!date) return true;
	if (after && date < after) return false;
	if (before && date >= before) return false;
	return true;
};

export function ProfileContent({
	reviewActivity = [],
	openPullRequests = [],
	isLoading,
	username,
	displayName,
	currUserIsDashboardUser,
	workspaceSlug,
	afterDate,
	beforeDate,
	onTimeframeChange,
}: ProfileContentProps) {
	const parsedAfterDate = parseDate(afterDate);
	const parsedBeforeDate = parseDate(beforeDate);

	// Generate skeleton arrays for loading state
	const skeletonReviews = isLoading
		? Array.from({ length: 3 }, (_, i) => ({ id: i }))
		: [];

	const skeletonPullRequests = isLoading
		? Array.from({ length: 2 }, (_, i) => ({ id: i }))
		: [];

	const filteredReviewActivity = isLoading
		? skeletonReviews
		: (reviewActivity ?? []).filter((activity) =>
				isWithinRange(activity.submittedAt, parsedAfterDate, parsedBeforeDate),
			);

	const displayPullRequests = isLoading
		? skeletonPullRequests
		: openPullRequests;

	const reviewStatsSource = (reviewActivity ?? []).filter((activity) =>
		isWithinRange(activity.submittedAt, parsedAfterDate, parsedBeforeDate),
	);

	const reviewStats = reviewStatsSource.reduce(
		(acc, activity) => {
			acc.totalReviews += 1;
			acc.totalScore += activity.score ?? 0;
			acc.codeComments += activity.codeComments ?? 0;
			if (
				!acc.lastReviewAt ||
				(activity.submittedAt && activity.submittedAt > acc.lastReviewAt)
			) {
				acc.lastReviewAt = activity.submittedAt;
			}
			switch (activity.state) {
				case "APPROVED":
					acc.approvals += 1;
					break;
				case "CHANGES_REQUESTED":
					acc.changeRequests += 1;
					break;
				default:
					acc.comments += 1;
			}
			return acc;
		},
		{
			totalReviews: 0,
			approvals: 0,
			changeRequests: 0,
			comments: 0,
			codeComments: 0,
			totalScore: 0,
			lastReviewAt: undefined as Date | undefined,
		},
	);

	const reviewedPullRequestsForPopover = reviewStatsSource
		.map((activity) => activity.pullRequest)
		.filter((pullRequest): pullRequest is NonNullable<typeof pullRequest> =>
			Boolean(pullRequest),
		) as LeaderboardEntry["reviewedPullRequests"];

	return (
		<div className="flex flex-col gap-4">
			<div className="self-start">
				<ProfileActivityFilter
					initialAfterDate={afterDate}
					initialBeforeDate={beforeDate}
					onTimeframeChange={onTimeframeChange}
				/>
			</div>
			<div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
				{/* Latest Review Activity */}
				<div className="flex flex-col gap-4">
					<div className="flex flex-col gap-1">
						<div className="flex flex-wrap items-center gap-3">
							<h3 className="text-lg font-semibold">Review activity</h3>
							<ActivityBadges
								reviewedPullRequests={reviewedPullRequestsForPopover}
								changeRequests={reviewStats.changeRequests}
								approvals={reviewStats.approvals}
								comments={reviewStats.comments}
								codeComments={reviewStats.codeComments}
							/>
						</div>
					</div>
					<div className="flex flex-col gap-2 m-1">
						{filteredReviewActivity.length > 0 ? (
							(filteredReviewActivity as PullRequestReviewInfo[]).map(
								(activity) => (
									<ReviewActivityCard
										key={activity.id}
										isLoading={isLoading}
										state={activity.state}
										submittedAt={activity.submittedAt}
										htmlUrl={activity.htmlUrl}
										pullRequest={activity.pullRequest}
										repositoryName={activity.pullRequest?.repository?.name}
										score={activity.score}
									/>
								),
							)
						) : (
							<EmptyState
								icon={CodeReviewIcon}
								title="No review activity"
								description={
									currUserIsDashboardUser
										? "No reviews in this timeframe. Try expanding the filter."
										: `${displayName || username} has no reviews in this timeframe.`
								}
							/>
						)}
					</div>
				</div>

				{/* Open Pull Requests */}
				<div className="flex flex-col gap-2">
					<span className="flex justify-between items-center">
						<h3 className="text-lg font-semibold">Open pull requests</h3>
						<Button variant="secondary" asChild>
							<Link
								to="/w/$workspaceSlug/user/$username/best-practices"
								params={{ username, workspaceSlug }}
							>
								Best practices
							</Link>
						</Button>
					</span>
					<div className="flex flex-col gap-2 m-1">
						{displayPullRequests.length > 0 ? (
							(displayPullRequests as PullRequestInfo[]).map((pullRequest) => (
								<IssueCard
									key={pullRequest.id}
									isLoading={isLoading}
									additions={pullRequest.additions}
									deletions={pullRequest.deletions}
									number={pullRequest.number}
									repositoryName={pullRequest.repository?.name}
									title={pullRequest.title}
									htmlUrl={pullRequest.htmlUrl}
									state={pullRequest.state}
									isDraft={pullRequest.isDraft}
									isMerged={pullRequest.isMerged}
									createdAt={pullRequest.createdAt}
									pullRequestLabels={pullRequest.labels}
								/>
							))
						) : (
							<EmptyState
								icon={GitPullRequestIcon}
								title="No open pull requests"
								description={
									currUserIsDashboardUser
										? "Pull requests you create will appear here."
										: `${displayName || username} doesn't have any open pull requests.`
								}
							/>
						)}
					</div>
				</div>
			</div>
		</div>
	);
}
