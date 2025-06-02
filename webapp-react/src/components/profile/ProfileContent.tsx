import type { PullRequestInfo, PullRequestReviewInfo } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import { CodeReviewIcon, GitPullRequestIcon } from "@primer/octicons-react";
import { Link } from "@tanstack/react-router";
import { useMemo } from "react";
import { EmptyState } from "../shared/EmptyState";
import { IssueCard } from "../shared/IssueCard";
import { ReviewActivityCard } from "./ReviewActivityCard";

export interface ProfileContentProps {
	reviewActivity?: PullRequestReviewInfo[];
	openPullRequests?: PullRequestInfo[];
	isLoading: boolean;
	username: string;
	displayName?: string;
	currUserIsDashboardUser: boolean;
}

export function ProfileContent({
	reviewActivity = [],
	openPullRequests = [],
	isLoading,
	username,
	displayName,
	currUserIsDashboardUser,
}: ProfileContentProps) {
	// Generate skeleton arrays for loading state
	const skeletonReviews = useMemo(
		() => (isLoading ? Array.from({ length: 3 }, (_, i) => ({ id: i })) : []),
		[isLoading],
	);

	const skeletonPullRequests = useMemo(
		() => (isLoading ? Array.from({ length: 2 }, (_, i) => ({ id: i })) : []),
		[isLoading],
	);

	// Use skeleton data during loading state
	const displayReviews = useMemo(
		() => (isLoading ? skeletonReviews : reviewActivity),
		[isLoading, skeletonReviews, reviewActivity],
	);

	const displayPullRequests = useMemo(
		() => (isLoading ? skeletonPullRequests : openPullRequests),
		[isLoading, skeletonPullRequests, openPullRequests],
	);

	return (
		<div className="grid grid-cols-1 lg:grid-cols-2 gap-2 border-t border-border pt-6">
			{/* Latest Review Activity */}
			<div className="flex flex-col gap-4">
				<h2 className="text-xl font-semibold">Latest Review Activity</h2>
				<div className="flex flex-col gap-2 m-1">
					{displayReviews.length > 0 ? (
						(displayReviews as PullRequestReviewInfo[]).map((activity) => (
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
							icon={<CodeReviewIcon size={24} />}
							title="No review activity"
							description={
								currUserIsDashboardUser
									? "When you review pull requests, they will appear here."
									: `${displayName || username} hasn't reviewed any pull requests yet.`
							}
						/>
					)}
				</div>
			</div>

			{/* Open Pull Requests */}
			<div className="flex flex-col gap-2">
				<span className="flex justify-between items-center">
					<h2 className="text-xl font-semibold">Open Pull Requests</h2>
					<Button variant="secondary" asChild>
						<Link to="/user/$username/best-practices" params={{ username }}>
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
							icon={<GitPullRequestIcon size={24} />}
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
	);
}
