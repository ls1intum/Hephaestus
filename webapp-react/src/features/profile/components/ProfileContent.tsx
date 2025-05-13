import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { InfoIcon } from "lucide-react";
import { ReviewActivityCard } from "./ReviewActivityCard";
import { IssueCard } from "./IssueCard";
import type { ProfileContentProps } from "../types";

export function ProfileContent({
  reviewActivity = [],
  openPullRequests = [],
  isLoading,
  username
}: ProfileContentProps) {
  // Generate skeleton arrays for loading state
  const skeletonReviews = isLoading ? Array.from({ length: 3 }, (_, i) => ({ id: i })) : [];
  const skeletonPullRequests = isLoading ? Array.from({ length: 2 }, (_, i) => ({ id: i })) : [];
  
  // Helper function to calculate scroll height based on number of items
  const calcScrollHeight = (arr: any[] = [], elHeight = 100) => {
    return `min(400px, calc(${arr.length * elHeight}px + ${8 * arr.length}px))`;
  };

  // Use skeleton data during loading state
  const displayReviews = isLoading ? skeletonReviews : reviewActivity;
  const displayPullRequests = isLoading ? skeletonPullRequests : openPullRequests;

  return (
    <div className="flex flex-col lg:flex-row gap-y-8 border-t border-border pt-6">
      {/* Latest Review Activity */}
      <div className="flex flex-col flex-1 gap-4 ml-3">
        <h2 className="text-xl font-semibold">Latest Review Activity</h2>
        <ScrollArea 
          className="rounded-md pr-4" 
          style={{ height: calcScrollHeight(displayReviews) }}
        >
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
              <div className="w-full h-20 flex justify-center items-center gap-4 border border-border bg-card rounded-lg p-4">
                <InfoIcon className="text-muted-foreground" />
                <span className="text-muted-foreground font-normal">No activity found</span>
              </div>
            )}
          </div>
        </ScrollArea>
      </div>

      {/* Open Pull Requests */}
      <div className="flex flex-col flex-1 gap-4 ml-3">
        <span className="flex justify-between items-center pr-6">
          <h2 className="text-xl font-semibold">Open Pull Requests</h2>
          <Button variant="secondary" asChild>
            <a href={`/user/${username}/best-practices`}>
              Best practices
            </a>
          </Button>
        </span>
        <ScrollArea 
          className="rounded-md pr-4" 
          style={{ height: calcScrollHeight(displayPullRequests, 200) }}
        >
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
              <div className="w-full h-20 flex justify-center items-center gap-4 border border-border bg-card rounded-lg p-4">
                <InfoIcon className="text-muted-foreground" />
                <span className="text-muted-foreground font-normal">No open pull requests found</span>
              </div>
            )}
          </div>
        </ScrollArea>
      </div>
    </div>
  );
}
