import { CodeReviewIcon } from "@primer/octicons-react";
import { ArrowRightIcon, Settings2Icon } from "lucide-react";
import type {
	ProfileActivityMonitor,
	ProfileActivityStats,
	ProfileReviewActivity,
	PullRequestBaseInfo,
	PullRequestInfo,
	RepositoryInfo,
} from "@/api/types.gen";
import { ActivityBadges } from "@/components/leaderboard/ActivityBadges";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import {
	Popover,
	PopoverContent,
	PopoverDescription,
	PopoverHeader,
	PopoverTitle,
	PopoverTrigger,
} from "@/components/ui/popover";
import { getProviderTerms, getPullRequestStateIcon, type ProviderType } from "@/lib/provider";
import type { LeaderboardSchedule } from "@/lib/timeframe";
import type { ReviewedPullRequest } from "../leaderboard/ReviewsPopover";
import { EmptyState } from "../shared/EmptyState";
import { IssueCard } from "../shared/IssueCard";
import { ProfileTimeframePicker } from "./ProfileTimeframePicker";
import { ReviewActivityCard } from "./ReviewActivityCard";

export interface ActivityMonitorFilters {
	repositoryIds: number[];
	limit: number;
}

export interface ProfileContentProps {
	providerType?: ProviderType;
	reviewActivity?: ProfileReviewActivity[];
	openPullRequests?: PullRequestInfo[];
	/** Server-computed activity stats */
	activityStats?: ProfileActivityStats;
	/** Server-provided list of reviewed pull requests */
	reviewedPullRequests?: PullRequestInfo[];
	activityMonitorData?: ProfileActivityMonitor;
	activityMonitorFilters?: ActivityMonitorFilters;
	onActivityMonitorFiltersChange?: (filters: ActivityMonitorFilters) => void;
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
	activityMonitorData,
	activityMonitorFilters = {
		repositoryIds: [],
		limit: 5,
	},
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
	const skeletonReviews = isLoading ? Array.from({ length: 3 }, (_, i) => ({ id: i })) : [];
	const skeletonPullRequests = isLoading ? Array.from({ length: 2 }, (_, i) => ({ id: i })) : [];

	const monitorReviewActivity = activityMonitorData?.reviewActivity ?? reviewActivity;
	const monitorPullRequests = activityMonitorData?.authoredPullRequests ?? openPullRequests;
	const monitorActivityStats = activityMonitorData?.activityStats ?? activityStats;
	const monitorRepositories = activityMonitorData?.repositories ?? [];

	const filteredReviewActivity = isLoading
		? skeletonReviews
		: (monitorReviewActivity ?? []).filter((activity) => (activity.score ?? 0) > 0);

	const displayPullRequests = isLoading ? skeletonPullRequests : monitorPullRequests;

	const terms = getProviderTerms(providerType);
	const { icon: PrIcon } = getPullRequestStateIcon(providerType, "OPEN");
	const totalReviewActivityCount =
		activityMonitorData?.totalReviewActivityCount ?? filteredReviewActivity.length;
	const totalAuthoredPullRequestCount =
		activityMonitorData?.totalAuthoredPullRequestCount ?? displayPullRequests.length;
	const canViewAllReviewActivity =
		!isLoading && totalReviewActivityCount > filteredReviewActivity.length;
	const canViewAllPullRequests =
		!isLoading && totalAuthoredPullRequestCount > displayPullRequests.length;

	const reviewedPullRequestsForPopover: ReviewedPullRequest[] =
		!activityMonitorData && reviewedPullRequests && reviewedPullRequests.length > 0
			? reviewedPullRequests
			: (monitorReviewActivity ?? [])
					.filter((activity) => (activity.score ?? 0) > 0)
					.map((activity) => activity.pullRequest)
					.filter((pr): pr is PullRequestBaseInfo => Boolean(pr));

	const toggleRepository = (repositoryId: number, checked: boolean) => {
		const nextRepositoryIds = checked
			? [...new Set([...activityMonitorFilters.repositoryIds, repositoryId])]
			: activityMonitorFilters.repositoryIds.filter((id) => id !== repositoryId);

		onActivityMonitorFiltersChange?.({
			...activityMonitorFilters,
			repositoryIds: nextRepositoryIds,
		});
	};

	const expandMonitor = () => {
		onActivityMonitorFiltersChange?.({
			...activityMonitorFilters,
			limit: 100,
		});
	};

	return (
		<div className="flex flex-col gap-4">
			<div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
				<div className="flex flex-col gap-1">
					<div className="flex flex-wrap items-center gap-3">
						<h2 className="text-xl font-semibold">Activity Monitor</h2>
						<ActivityBadges
							reviewedPullRequests={reviewedPullRequestsForPopover}
							approvals={monitorActivityStats?.numberOfApprovals ?? 0}
							changeRequests={monitorActivityStats?.numberOfChangeRequests ?? 0}
							comments={monitorActivityStats?.numberOfComments ?? 0}
							codeComments={monitorActivityStats?.numberOfCodeComments ?? 0}
							ownReplies={monitorActivityStats?.numberOfOwnReplies ?? 0}
							openPullRequests={monitorActivityStats?.numberOfOpenPullRequests ?? 0}
							mergedPullRequests={monitorActivityStats?.numberOfMergedPullRequests ?? 0}
							closedPullRequests={monitorActivityStats?.numberOfClosedPullRequests ?? 0}
							openedIssues={monitorActivityStats?.numberOfOpenedIssues ?? 0}
							closedIssues={monitorActivityStats?.numberOfClosedIssues ?? 0}
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
						repositories={monitorRepositories}
						filters={activityMonitorFilters}
						onRepositoryChange={toggleRepository}
					/>
				</div>
			</div>
			<div className="grid grid-cols-1 gap-2 lg:grid-cols-2">
				{/* Review Activity */}
				<div className="flex flex-col gap-4">
					<h3 className="text-lg font-semibold">Review activity</h3>
					<div className="flex flex-col gap-2">
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
					{canViewAllReviewActivity && (
						<Button
							type="button"
							variant="link"
							className="w-fit px-0 text-primary"
							onClick={expandMonitor}
						>
							View all review activity
							<ArrowRightIcon data-icon="inline-end" />
						</Button>
					)}
				</div>

				{/* Open Pull Requests / Merge Requests */}
				<div className="flex flex-col gap-4">
					<h3 className="text-lg font-semibold">Open {terms.pullRequests.toLowerCase()}</h3>
					<div className="flex flex-col gap-2">
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
					{canViewAllPullRequests && (
						<Button
							type="button"
							variant="link"
							className="w-fit px-0 text-primary"
							onClick={expandMonitor}
						>
							View all {terms.pullRequests.toLowerCase()}
							<ArrowRightIcon data-icon="inline-end" />
						</Button>
					)}
				</div>
			</div>
		</div>
	);
}

interface ActivityMonitorConfigurationProps {
	repositories: RepositoryInfo[];
	filters: ActivityMonitorFilters;
	onRepositoryChange: (repositoryId: number, checked: boolean) => void;
}

function ActivityMonitorConfiguration({
	repositories,
	filters,
	onRepositoryChange,
}: ActivityMonitorConfigurationProps) {
	return (
		<Popover>
			<PopoverTrigger
				render={
					<Button type="button" variant="outline" className="w-65">
						Configure activity monitor
						<Settings2Icon data-icon="inline-end" />
					</Button>
				}
			/>
			<PopoverContent align="end" className="w-80">
				<PopoverHeader>
					<PopoverTitle>Activity monitor</PopoverTitle>
					<PopoverDescription>Filter activity by repository.</PopoverDescription>
				</PopoverHeader>

				<div className="grid gap-2">
					<p className="text-sm font-medium">Repositories</p>
					{repositories.length > 0 ? (
						repositories.map((repository) => (
							<ActivityMonitorCheckbox
								key={repository.id}
								id={`activity-monitor-repository-${repository.id}`}
								label={repository.nameWithOwner}
								checked={filters.repositoryIds.includes(repository.id)}
								onCheckedChange={(checked) => onRepositoryChange(repository.id, checked)}
							/>
						))
					) : (
						<p className="text-sm text-muted-foreground">No repositories for this timeframe.</p>
					)}
				</div>
			</PopoverContent>
		</Popover>
	);
}

interface ActivityMonitorCheckboxProps {
	id: string;
	label: string;
	checked: boolean;
	onCheckedChange: (checked: boolean) => void;
}

function ActivityMonitorCheckbox({
	id,
	label,
	checked,
	onCheckedChange,
}: ActivityMonitorCheckboxProps) {
	return (
		<Label
			htmlFor={id}
			className="grid min-h-8 grid-cols-[1rem_1fr] items-center gap-2 text-sm font-normal"
		>
			<Checkbox
				id={id}
				checked={checked}
				onCheckedChange={(nextChecked) => onCheckedChange(nextChecked === true)}
			/>
			<span className="truncate">{label}</span>
		</Label>
	);
}
