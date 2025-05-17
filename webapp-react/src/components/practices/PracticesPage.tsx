import type { Activity } from "@/api/types.gen";
import { PullRequestBadPracticeCard } from "./PullRequestBadPracticeCard";
import { BadPracticeLegendCard } from "./BadPracticeLegendCard";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { RefreshCcw } from "lucide-react";
import { filterGoodAndBadPractices } from "./utils";

interface PracticesPageProps {
  activityData?: Activity;
  isLoading: boolean;
  isDetectingBadPractices: boolean;
  username: string;
  currUserIsDashboardUser: boolean;
  onDetectBadPractices: () => void;
}

export function PracticesPage({
  activityData,
  isLoading,
  isDetectingBadPractices,
  username,
  currUserIsDashboardUser,
  onDetectBadPractices,
}: PracticesPageProps) {
  // Calculate statistics
  const pullRequests = activityData?.pullRequests || [];
  const allBadPractices = pullRequests.flatMap(pr => pr.badPractices);
  const { goodPractices, badPractices } = filterGoodAndBadPractices(allBadPractices);
  
  const numberOfPullRequests = pullRequests.length;
  const numberOfGoodPractices = goodPractices.length;
  const numberOfBadPractices = badPractices.length;

  return (
    <div className="flex flex-col items-center">
      <div className="grid grid-cols-1 xl:grid-cols-5 gap-y-4 xl:gap-8 w-full">
        <div className="space-y-2 col-span-1">
          <div className="flex flex-col gap-2 mb-4">
            <h1 className="text-xl font-semibold">Activities</h1>
            <p>
              {username} currently has <span className="font-semibold">{numberOfPullRequests}</span> open pull requests,{" "}
              <span className="font-semibold">{numberOfGoodPractices}</span> detected good practices, and{" "}
              <span className="font-semibold">{numberOfBadPractices}</span> detected bad practices.
            </p>
          </div>
        </div>
        <div className="col-span-3">
          <div className="flex flex-col justify-between gap-2">
            <span className="flex flex-row justify-between items-center">
              <h1 className="text-xl font-semibold">Open pull requests</h1>
              {currUserIsDashboardUser && (
                <Button 
                  variant="outline" 
                  className="gap-2" 
                  onClick={onDetectBadPractices}
                  disabled={isDetectingBadPractices}
                >
                  {isDetectingBadPractices ? (
                    <Spinner className="size-4" />
                  ) : (
                    <RefreshCcw className="size-4" />
                  )}
                  <span>Detect bad practices</span>
                </Button>
              )}
            </span>
            <div className="flex flex-col gap-4">
              {isLoading ? (
                // Loading states
                Array.from({ length: 2 }).map((_, idx) => (
                  <PullRequestBadPracticeCard 
                    key={`skeleton-${idx}`}
                    id={idx}
                    isLoading={true}
                  />
                ))
              ) : pullRequests.length > 0 ? (
                pullRequests.map(pullRequest => (
                  <PullRequestBadPracticeCard
                    key={pullRequest.id}
                    id={pullRequest.id}
                    title={pullRequest.title}
                    number={pullRequest.number}
                    htmlUrl={pullRequest.htmlUrl}
                    state={pullRequest.state}
                    isDraft={pullRequest.isDraft}
                    isMerged={pullRequest.isMerged}
                    additions={pullRequest.additions}
                    deletions={pullRequest.deletions}
                    repositoryName={pullRequest.repository?.name}
                    createdAt={pullRequest.createdAt}
                    pullRequestLabels={pullRequest.labels}
                    badPractices={pullRequest.badPractices}
                    badPracticeSummary={pullRequest.badPracticeSummary}
                    currUserIsDashboardUser={currUserIsDashboardUser}
                  />
                ))
              ) : (
                <p className="text-muted-foreground">No pull requests found.</p>
              )}
            </div>
          </div>
        </div>
        <div className="col-span-1">
          <BadPracticeLegendCard />
        </div>
      </div>
    </div>
  );
}
