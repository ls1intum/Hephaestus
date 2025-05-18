import type { Activity } from "@/api/types.gen";
import { PullRequestBadPracticeCard } from "./PullRequestBadPracticeCard";
import { BadPracticeLegendCard } from "./BadPracticeLegendCard";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { InfoIcon, RefreshCcw } from "lucide-react";
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
      <div className="w-full max-w-[1400px]">
        <h1 className="text-3xl font-bold mb-4">Best Practices Dashboard</h1>
        <div className="grid grid-cols-1 xl:grid-cols-4 gap-y-4 xl:gap-8">
          {/* Left Column - Summary & Controls */}
          <div className="space-y-4 col-span-1">
            <div className="xl:sticky xl:top-4 xl:self-start xl:max-h-[calc(100vh-2rem)] xl:overflow-auto">
              {/* Activity Summary Card */}
              <Card className="mb-4">
                <CardHeader>
                  <CardTitle>Activity Summary</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="space-y-2">
                    <div className="flex justify-between">
                      <span>Pull Requests</span>
                      <span className="font-semibold">{numberOfPullRequests}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Good Practices</span>
                      <span className="font-semibold text-github-success-foreground">{numberOfGoodPractices}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Areas for Improvement</span>
                      <span className="font-semibold text-github-attention-foreground">{numberOfBadPractices}</span>
                    </div>

                    {currUserIsDashboardUser && (
                      <Button 
                        variant="outline" 
                        className="w-full mt-4 gap-2" 
                        onClick={onDetectBadPractices}
                        disabled={isDetectingBadPractices}
                      >
                        {isDetectingBadPractices ? (
                          <Spinner className="size-4" />
                        ) : (
                          <RefreshCcw className="size-4" />
                        )}
                        <span>Analyze Pull Requests</span>
                      </Button>
                    )}
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>

          {/* Center Column - Pull Requests List */}
          <div className="col-span-2 space-y-4">
            <h2 className="text-2xl font-semibold">Pull Requests</h2>
            <div className="mt-3 pt-2 flex items-center gap-2 text-sm bg-muted/50 p-2.5 rounded-md">
              <InfoIcon className="h-4 w-4 text-blue-500 flex-shrink-0" />
              <p className="text-muted-foreground">
                AI-powered insights. <Button variant="link" size="none">Help us improve</Button> by flagging any misdetections.
              </p>
            </div>
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

          {/* Right Column - Legend */}
          <div className="col-span-1 xl:sticky xl:top-4 xl:self-start xl:max-h-[calc(100vh-2rem)] xl:overflow-auto">
            <BadPracticeLegendCard />
          </div>
        </div>
      </div>
    </div>
  );
}
