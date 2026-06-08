import { CodeReviewIcon } from "@primer/octicons-react";
import { ArrowRightIcon, FilterIcon } from "lucide-react";
import { useState } from "react";
import type { ProfileActivityMonitor, PullRequestBaseInfo } from "@/api/types.gen";
import { ActivityBadges } from "@/components/leaderboard/ActivityBadges";
import { Button } from "@/components/ui/button";
import { type ActivityMonitorFilters, MAX_ACTIVITY_MONITOR_LIMIT } from "@/lib/activity-monitor";
import { getProviderTerms, getPullRequestStateIcon, type ProviderType } from "@/lib/provider";
import type { LeaderboardSchedule } from "@/lib/timeframe";
import type { ReviewedPullRequest } from "../leaderboard/ReviewsPopover";
import { EmptyState } from "../shared/EmptyState";
import { IssueCard } from "../shared/IssueCard";
import { ActivityMonitorConfiguration } from "./ActivityMonitorConfiguration";
import { ProfileTimeframePicker } from "./ProfileTimeframePicker";
import { ReviewActivityCard } from "./ReviewActivityCard";

export interface ProfileContentProps {
	providerType?: ProviderType;
	activityMonitorData?: ProfileActivityMonitor;
	activityMonitorFilters: ActivityMonitorFilters;
	onActivityMonitorFiltersChange: (filters: ActivityMonitorFilters) => void;
	isLoading: boolean;
	username: string;
	displayName?: string;
	currUserIsDashboardUser: boolean;
	workspaceSlug: string;
	afterDate?: string;
	beforeDate?: string;
	onTimeframeChange?: (afterDate: string, beforeDate?: string) => void;
	schedule?: LeaderboardSchedule;
}

export function ProfileContent({
	providerType = "GITHUB",
	activityMonitorData,
	activityMonitorFilters,
	onActivityMonitorFiltersChange,
	isLoading,
	username,
	displayName,
	currUserIsDashboardUser,
	afterDate,
	beforeDate,
	onTimeframeChange,
	schedule,
}: ProfileContentProps) {
	const [reviewExpanded, setReviewExpanded] = useState(false);
	const [prExpanded, setPrExpanded] = useState(false);

	const stats = activityMonitorData?.activityStats;
	const repositories = activityMonitorData?.repositories ?? [];

	const allReviewActivity = (activityMonitorData?.reviewActivity ?? []).filter(
		(activity) => (activity.score ?? 0) > 0,
	);
	const allPullRequests = activityMonitorData?.authoredPullRequests ?? [];
	const totalReviewActivityCount = activityMonitorData?.totalReviewActivityCount ?? 0;
	const totalAuthoredPullRequestCount = activityMonitorData?.totalAuthoredPullRequestCount ?? 0;

	const reviewActivity = reviewExpanded
		? allReviewActivity
		: allReviewActivity.slice(0, activityMonitorFilters.limit);
	const pullRequests = prExpanded
		? allPullRequests
		: allPullRequests.slice(0, activityMonitorFilters.limit);

	const terms = getProviderTerms(providerType);
	const { icon: PrIcon } = getPullRequestStateIcon(providerType, "OPEN");

	const canViewAllReviewActivity =
		!isLoading && !reviewExpanded && totalReviewActivityCount > reviewActivity.length;
	const canViewAllPullRequests =
		!isLoading && !prExpanded && totalAuthoredPullRequestCount > pullRequests.length;

	const reviewedPullRequestsForPopover: ReviewedPullRequest[] = allReviewActivity
		.map((activity) => activity.pullRequest)
		.filter((pr): pr is PullRequestBaseInfo => Boolean(pr));

	const expandReviewActivity = () => {
		setReviewExpanded(true);
		if (activityMonitorFilters.limit < MAX_ACTIVITY_MONITOR_LIMIT) {
			onActivityMonitorFiltersChange({
				...activityMonitorFilters,
				limit: MAX_ACTIVITY_MONITOR_LIMIT,
			});
		}
	};

	const expandPullRequests = () => {
		setPrExpanded(true);
		if (activityMonitorFilters.limit < MAX_ACTIVITY_MONITOR_LIMIT) {
			onActivityMonitorFiltersChange({
				...activityMonitorFilters,
				limit: MAX_ACTIVITY_MONITOR_LIMIT,
			});
		}
	};

	const noReposSelected =
		!isLoading && repositories.length > 0 && activityMonitorFilters.repositoryIds.length === 0;

	return (
		<div className="flex flex-col gap-4">
			<div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
				<div className="flex flex-col gap-1">
					<div className="flex flex-wrap items-center gap-3">
						<h2 className="text-xl font-semibold">Activity Monitor</h2>
						<ActivityBadges
							reviewedPullRequests={reviewedPullRequestsForPopover}
							approvals={stats?.numberOfApprovals ?? 0}
							changeRequests={stats?.numberOfChangeRequests ?? 0}
							comments={stats?.numberOfComments ?? 0}
							codeComments={stats?.numberOfCodeComments ?? 0}
							ownReplies={stats?.numberOfOwnReplies ?? 0}
							openPullRequests={stats?.numberOfOpenPullRequests ?? 0}
							mergedPullRequests={stats?.numberOfMergedPullRequests ?? 0}
							closedPullRequests={stats?.numberOfClosedPullRequests ?? 0}
							openedIssues={stats?.numberOfOpenedIssues ?? 0}
							closedIssues={stats?.numberOfClosedIssues ?? 0}
							isLoading={isLoading}
							providerType={providerType}
						/>
					</div>
					<p className="text-sm text-provider-muted-foreground">
						Activity across projects in this workspace.
					</p>
				</div>
				<div className="flex flex-wrap items-center gap-2 md:justify-end">
					<ProfileTimeframePicker
						afterDate={afterDate}
						beforeDate={beforeDate}
						onTimeframeChange={onTimeframeChange}
						schedule={schedule}
						enableAllActivity
					/>
					<ActivityMonitorConfiguration
						repositories={repositories}
						filters={activityMonitorFilters}
						onFiltersChange={onActivityMonitorFiltersChange}
					/>
				</div>
			</div>
			{noReposSelected ? (
				<EmptyState
					icon={FilterIcon}
					title="No repository selected"
					description="Select at least one repository in the filter to view activity."
				/>
			) : (
				<div className="grid grid-cols-1 gap-2 lg:grid-cols-2">
					<div className="flex flex-col gap-4">
						<h3 className="text-lg font-semibold">Review activity</h3>
						<div className="flex flex-col gap-2">
							{isLoading ? (
								Array.from({ length: 3 }, (_, i) => (
									<ReviewActivityCard key={i} isLoading providerType={providerType} />
								))
							) : reviewActivity.length > 0 ? (
								reviewActivity.map((activity) => (
									<ReviewActivityCard
										key={activity.id}
										isLoading={false}
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
						{canViewAllReviewActivity && (
							<Button
								type="button"
								variant="link"
								className="w-fit px-0 text-primary"
								onClick={expandReviewActivity}
							>
								View all review activity
								<ArrowRightIcon data-icon="inline-end" />
							</Button>
						)}
					</div>
					<div className="flex flex-col gap-4">
						<h3 className="text-lg font-semibold">Open {terms.pullRequests.toLowerCase()}</h3>
						<div className="flex flex-col gap-2">
							{isLoading ? (
								Array.from({ length: 2 }, (_, i) => (
									<IssueCard key={i} isLoading providerType={providerType} />
								))
							) : pullRequests.length > 0 ? (
								pullRequests.map((pullRequest) => (
									<IssueCard
										key={pullRequest.id}
										isLoading={false}
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
						{canViewAllPullRequests && (
							<Button
								type="button"
								variant="link"
								className="w-fit px-0 text-primary"
								onClick={expandPullRequests}
							>
								View all {terms.pullRequests.toLowerCase()}
								<ArrowRightIcon data-icon="inline-end" />
							</Button>
						)}
					</div>
				</div>
			)}
		</div>
	);
}
