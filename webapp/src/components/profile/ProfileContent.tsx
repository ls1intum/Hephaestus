import { CodeReviewIcon } from "@primer/octicons-react";
import type {
	ProfileActivityStats,
	ProfileReviewActivity,
	PullRequestBaseInfo,
	PullRequestInfo,
} from "@/api/types.gen";
import { ActivityBadges } from "@/components/leaderboard/ActivityBadges";
import { getProviderTerms, getPullRequestStateIcon, type ProviderType } from "@/lib/provider";
import type { LeaderboardSchedule } from "@/lib/timeframe";
import type { ReviewedPullRequest } from "../leaderboard/ReviewsPopover";
import { EmptyState } from "../shared/EmptyState";
import { IssueCard } from "../shared/IssueCard";
import { ProfileTimeframePicker } from "./ProfileTimeframePicker";
import { ReviewActivityCard } from "./ReviewActivityCard";

export interface ProfileContentProps {
	providerType?: ProviderType;
	reviewActivity?: ProfileReviewActivity[];
	openPullRequests?: PullRequestInfo[];
	/** Server-computed activity stats */
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
	providerType = "GITHUB",
	reviewActivity = [],
	openPullRequests = [],
	activityStats,
	reviewedPullRequests,
	isLoading,
	username,
	displayName,
	currUserIsDashboardUser,
	afterDate,
	beforeDate,
	onTimeframeChange,
	schedule,
}: ProfileContentProps) {
	// Generate skeleton arrays for loading state
	const skeletonReviews = isLoading ? Array.from({ length: 3 }, (_, i) => ({ id: i })) : [];

	const skeletonPullRequests = isLoading ? Array.from({ length: 2 }, (_, i) => ({ id: i })) : [];

	const filteredReviewActivity = isLoading
		? skeletonReviews
		: (reviewActivity ?? []).filter((activity) => (activity.score ?? 0) > 0);

	const displayPullRequests = isLoading ? skeletonPullRequests : openPullRequests;

	const terms = getProviderTerms(providerType);
	const { icon: PrIcon } = getPullRequestStateIcon(providerType, "OPEN");

	const reviewedPullRequestsForPopover: ReviewedPullRequest[] =
		reviewedPullRequests && reviewedPullRequests.length > 0
			? reviewedPullRequests
			: (reviewActivity ?? [])
					.filter((activity) => (activity.score ?? 0) > 0)
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
				{/* Latest Pull Request Activity */}
				<div className="flex flex-col gap-4">
					<div className="flex flex-col gap-1">
						<div className="flex flex-wrap items-center gap-3">
							<h3 className="text-lg font-semibold">Review activity</h3>
							<ActivityBadges
								reviewedPullRequests={reviewedPullRequestsForPopover}
								approvals={activityStats?.numberOfApprovals ?? 0}
								changeRequests={activityStats?.numberOfChangeRequests ?? 0}
								comments={activityStats?.numberOfComments ?? 0}
								codeComments={activityStats?.numberOfCodeComments ?? 0}
								ownReplies={activityStats?.numberOfOwnReplies ?? 0}
								openPullRequests={activityStats?.numberOfOpenPullRequests ?? 0}
								mergedPullRequests={activityStats?.numberOfMergedPullRequests ?? 0}
								closedPullRequests={activityStats?.numberOfClosedPullRequests ?? 0}
								openedIssues={activityStats?.numberOfOpenedIssues ?? 0}
								closedIssues={activityStats?.numberOfClosedIssues ?? 0}
								isLoading={isLoading}
								providerType={providerType}
							/>
						</div>
						<p className="text-sm text-provider-muted-foreground">
							This list shows review work that affects score. The badges also show other activity.
						</p>
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
									providerType={providerType}
								/>
							))
						) : (
							<EmptyState
								icon={CodeReviewIcon}
								title="No review activity"
								description={
									currUserIsDashboardUser
										? `No review activity that counts yet. Try a wider timeframe.`
										: `${displayName || username} has no review activity that counts in this timeframe.`
								}
							/>
						)}
					</div>
				</div>

				{/* Open Pull Requests / Merge Requests */}
				<div className="flex flex-col gap-2">
					<h3 className="text-lg font-semibold">Open {terms.pullRequests.toLowerCase()}</h3>
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
									providerType={providerType}
								/>
							))
						) : (
							<EmptyState
								icon={PrIcon}
								title={`No open ${terms.pullRequests.toLowerCase()}`}
								description={
									currUserIsDashboardUser
										? `${terms.pullRequests} you create will appear here.`
										: `${displayName || username} doesn't have any open ${terms.pullRequests.toLowerCase()}.`
								}
							/>
						)}
					</div>
				</div>
			</div>
		</div>
	);
}
