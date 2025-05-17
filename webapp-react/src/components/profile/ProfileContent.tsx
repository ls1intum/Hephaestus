import { Button } from "@/components/ui/button";
import { CodeReviewIcon, GitPullRequestIcon } from "@primer/octicons-react";
import { ReviewActivityCard } from "./ReviewActivityCard";
import { IssueCard } from "./IssueCard";
import type { ProfileContentProps } from ".";
import { Card, CardContent } from "@/components/ui/card";
import { useMemo } from "react";
import { Link } from "@tanstack/react-router";

export function ProfileContent({
  reviewActivity = [],
  openPullRequests = [],
  isLoading,
  username
}: ProfileContentProps) {
  // Generate skeleton arrays for loading state
  const skeletonReviews = useMemo(() => 
    isLoading ? Array.from({ length: 3 }, (_, i) => ({ id: i })) : [], 
    [isLoading]
  );
  
  const skeletonPullRequests = useMemo(() => 
    isLoading ? Array.from({ length: 2 }, (_, i) => ({ id: i })) : [],
    [isLoading]
  );
  
  // Use skeleton data during loading state
  const displayReviews = useMemo(() => 
    isLoading ? skeletonReviews : reviewActivity,
    [isLoading, skeletonReviews, reviewActivity]
  );
  
  const displayPullRequests = useMemo(() => 
    isLoading ? skeletonPullRequests : openPullRequests,
    [isLoading, skeletonPullRequests, openPullRequests]
  );

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 border-t border-border pt-6">
      {/* Latest Review Activity */}
      <div className="flex flex-col gap-4">
        <h2 className="text-xl font-semibold">Latest Review Activity</h2>
        <div className="flex flex-col gap-2 m-1">
          {displayReviews.length > 0 ? (
            displayReviews.map((activity: any) => (
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
            <Card className="border-dashed h-60">
              <CardContent className="flex flex-col items-center justify-center py-8 px-4 text-center">
                <div className="rounded-full bg-muted p-3 mb-3">
                  <CodeReviewIcon className="h-6 w-6 text-muted-foreground" size={24} />
                </div>
                <h3 className="font-medium text-lg mb-1">No review activity</h3>
                <p className="text-muted-foreground text-sm mb-4 max-w-[18rem]">
                  When you review pull requests, they will appear here.
                </p>
              </CardContent>
            </Card>
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
            displayPullRequests.map((pullRequest: any) => (
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
            <Card className="border-dashed h-60">
              <CardContent className="flex flex-col items-center justify-center py-8 px-4 text-center">
                <div className="rounded-full bg-muted p-3 mb-3">
                  <GitPullRequestIcon className="h-6 w-6 text-muted-foreground" size={24} />
                </div>
                <h3 className="font-medium text-lg mb-1">No open pull requests</h3>
                <p className="text-muted-foreground text-sm mb-4 max-w-[18rem]">
                  Pull requests you create will appear here.
                </p>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
