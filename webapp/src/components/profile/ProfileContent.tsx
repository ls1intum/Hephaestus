import { CodeReviewIcon, GitPullRequestIcon } from "@primer/octicons-react";
import { Link } from "@tanstack/react-router";
import type {
	ProfileActivityStats,
	ProfileReviewActivity,
	PullRequestBaseInfo,
	PullRequestInfo,
} from "@/api/types.gen";
import { ActivityBadges } from "@/components/leaderboard/ActivityBadges";
import { Button } from "@/components/ui/button";
import type { LeaderboardSchedule } from "@/lib/timeframe";
import type { ReviewedPullRequest } from "../leaderboard/ReviewsPopover";
import { EmptyState } from "../shared/EmptyState";
import { IssueCard } from "../shared/IssueCard";
import { ProfileTimeframePicker } from "./ProfileTimeframePicker";
import { ReviewActivityCard } from "./ReviewActivityCard";

export interface ProfileContentProps {
	reviewActivity?: ProfileReviewActivity[];
	openPullRequests?: PullRequestInfo[];
	/** Server-computed activity stats; falls back to client computation if not provided */
	activityStats?: ProfileActivityStats;
	/** Server-provided list of reviewed pull requests */
	reviewedPullRequests?: PullRequestInfo[];
	isLoading: boolean;
	username: string;
	displayName?: string;
	currUserIsDashboardUser: boolean;
	workspaceSlug: string;
	afterDate?: string;
	beforeDate?: string;
	onTimeframeChange?: (afterDate: string, beforeDate?: string) => void;
	/** Leaderboard schedule for proper week calculations */
	schedule?: LeaderboardSchedule;
}

export function ProfileContent({
	reviewActivity = [],
	openPullRequests = [],
	activityStats,
	reviewedPullRequests,
	isLoading,
	username,
	displayName,
	currUserIsDashboardUser,
	workspaceSlug,
	afterDate,
	beforeDate,
	onTimeframeChange,
	schedule,
}: ProfileContentProps) {
	// Generate skeleton arrays for loading state
	const skeletonReviews = isLoading ? Array.from({ length: 3 }, (_, i) => ({ id: i })) : [];

	const skeletonPullRequests = isLoading ? Array.from({ length: 2 }, (_, i) => ({ id: i })) : [];

	const filteredReviewActivity = isLoading ? skeletonReviews : (reviewActivity ?? []);

	const displayPullRequests = isLoading ? skeletonPullRequests : openPullRequests;

	// Normalized stats interface for ActivityBadges
	interface NormalizedStats {
		approvals: number;
		changeRequests: number;
		comments: number;
		codeComments: number;
	}

	// Compute stats from activity data as fallback when server stats not available
	const computeStatsFromActivity = (activities: ProfileReviewActivity[]): NormalizedStats =>
		activities.reduce(
			(acc, activity) => {
				acc.codeComments += activity.codeComments ?? 0;
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
				approvals: 0,
				changeRequests: 0,
				comments: 0,
				codeComments: 0,
			},
		);

	// Convert server stats to normalized format, or fall back to client computation
	const normalizeStats = (serverStats?: ProfileActivityStats): NormalizedStats => {
		if (serverStats) {
			return {
				approvals: serverStats.numberOfApprovals ?? 0,
				changeRequests: serverStats.numberOfChangeRequests ?? 0,
				comments: (serverStats.numberOfComments ?? 0) + (serverStats.numberOfIssueComments ?? 0),
				codeComments: serverStats.numberOfCodeComments ?? 0,
			};
		}
		return computeStatsFromActivity(reviewActivity ?? []);
	};

	const reviewStats = normalizeStats(activityStats);

	// Use server-provided reviewed PRs if available, otherwise extract from review activity
	const reviewedPRsForPopover: ReviewedPullRequest[] =
		reviewedPullRequests && reviewedPullRequests.length > 0
			? reviewedPullRequests
			: (reviewActivity ?? [])
					.map((activity) => activity.pullRequest)
					.filter((pr): pr is PullRequestBaseInfo => Boolean(pr));

	return (
		<div className="flex flex-col gap-4">
			<ProfileTimeframePicker
				afterDate={afterDate}
				beforeDate={beforeDate}
				onTimeframeChange={onTimeframeChange}
				schedule={schedule}
				enableAllActivity
			/>
			<div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
				{/* Latest Review Activity */}
				<div className="flex flex-col gap-4">
					<div className="flex flex-col gap-1">
						<div className="flex flex-wrap items-center gap-3">
							<h3 className="text-lg font-semibold">Review activity</h3>
							<ActivityBadges
								reviewedPullRequests={reviewedPRsForPopover}
								approvals={reviewStats.approvals}
								changeRequests={reviewStats.changeRequests}
								comments={reviewStats.comments}
								codeComments={reviewStats.codeComments}
								isLoading={isLoading}
							/>
						</div>
					</div>
					<div className="flex flex-col gap-2 m-1">
						{filteredReviewActivity.length > 0 ? (
							(filteredReviewActivity as ProfileReviewActivity[]).map((activity) => (
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
							))
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
						<Button
							variant="secondary"
							render={
								<Link
									to="/w/$workspaceSlug/user/$username/best-practices"
									params={{ username, workspaceSlug }}
								/>
							}
						>
							Best practices
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
