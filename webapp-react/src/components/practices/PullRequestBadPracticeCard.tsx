import React, { useState } from "react";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
import { BadPracticeCard } from "./BadPracticeCard";
import type { PullRequestBadPractice, LabelInfo } from "@/api/types.gen";
import { formatTitle } from "./utils";
import { Collapsible, CollapsibleTrigger, CollapsibleContent } from "@/components/ui/collapsible";
import { 
  RefreshCw, 
  GitPullRequest, 
  GitPullRequestDraft, 
  GitPullRequestClosed, 
  GitMerge,
  FoldVertical
} from "lucide-react";
import { GitHubLabel } from "./GitHubLabel";

import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { format, parseISO } from "date-fns";

interface PullRequestBadPracticeCardProps {
  id: number;
  title?: string;
  number?: number;
  additions?: number;
  deletions?: number;
  htmlUrl?: string;
  repositoryName?: string;
  createdAt?: string;
  updatedAt?: string;
  state?: string;
  isDraft?: boolean;
  isMerged?: boolean;
  pullRequestLabels?: Array<LabelInfo>;
  badPractices?: Array<PullRequestBadPractice>;
  oldBadPractices?: Array<PullRequestBadPractice>;
  badPracticeSummary?: string;
  isLoading?: boolean;
  openCard?: boolean;
  currUserIsDashboardUser?: boolean;
  onDetectBadPractices?: (id: number) => void;
}

export function PullRequestBadPracticeCard({
  id,
  title = "",
  number = 0,
  additions = 0,
  deletions = 0,
  htmlUrl = "",
  repositoryName = "",
  createdAt = "",
  updatedAt = "",
  state = "OPEN",
  isDraft = false,
  isMerged = false,
  pullRequestLabels = [],
  badPractices = [],
  oldBadPractices = [],
  badPracticeSummary = "",
  isLoading = false,
  openCard = false,
  currUserIsDashboardUser = false,
  onDetectBadPractices,
}: PullRequestBadPracticeCardProps) {
  const [isOpen, setIsOpen] = useState(openCard);
  
  const displayCreated = createdAt ? format(parseISO(createdAt), 'MMM d') : null;
  const displayUpdated = updatedAt ? format(parseISO(updatedAt), 'MMM d, HH:mm') : null;
  const formattedTitle = formatTitle(title);
  const expandEnabled = badPractices.length > 0;
  
  // Get the appropriate icon and color based on PR state
  const getIssueIconAndColor = () => {
    if (isDraft) {
      return { icon: GitPullRequestDraft, color: "text-github-muted-foreground" };
    } else if (isMerged) {
      return { icon: GitMerge, color: "text-purple-500" };
    } else if (state === "CLOSED") {
      return { icon: GitPullRequestClosed, color: "text-github-danger-foreground" };
    } else {
      return { icon: GitPullRequest, color: "text-github-success-foreground" };
    }
  };
  
  // Sort practices by severity
  const orderedBadPractices = [...badPractices].sort((a, b) => {
    const severityOrder = {
      CRITICAL_ISSUE: 0,
      NORMAL_ISSUE: 1,
      MINOR_ISSUE: 2,
      GOOD_PRACTICE: 3,
      FIXED: 4,
      WONT_FIX: 5,
      WRONG: 6
    };
    return severityOrder[a.state as keyof typeof severityOrder] - severityOrder[b.state as keyof typeof severityOrder];
  });

  const orderedOldBadPractices = [...oldBadPractices || []].sort((a, b) => {
    const severityOrder = {
      CRITICAL_ISSUE: 0,
      NORMAL_ISSUE: 1,
      MINOR_ISSUE: 2,
      GOOD_PRACTICE: 3,
      FIXED: 4,
      WONT_FIX: 5,
      WRONG: 6
    };
    return severityOrder[a.state as keyof typeof severityOrder] - severityOrder[b.state as keyof typeof severityOrder];
  });

  const handleDetectClick = () => {
    if (onDetectBadPractices) {
      onDetectBadPractices(id);
    }
  };

  const detectedString = badPractices.length > 0
    ? `${badPractices.length} practice${badPractices.length !== 1 ? "s" : ""} detected`
    : "No practices detected";

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <Card>
        <div className="flex flex-col gap-1 pt-2 pl-6 pr-2">
          <div className="flex justify-between items-center text-sm text-github-muted-foreground h-10">
            <span>
              {isLoading ? (
                <>
                  <Skeleton className="size-5 bg-green-500/30" />
                  <Skeleton className="h-4 w-16 lg:w-36" />
                </>
              ) : (
                <span className="font-medium flex justify-center items-center space-x-1">
                  {React.createElement(getIssueIconAndColor().icon, { 
                    className: `mr-1 size-[18px] ${getIssueIconAndColor().color}` 
                  })}
                  <a href={htmlUrl} className="hover:underline">
                    {repositoryName} #{number}
                  </a>
                  <span>
                    on {displayCreated}. Updated on {displayUpdated}
                  </span>
                </span>
              )}
            </span>
            <span className="font-medium flex justify-center items-center gap-2">
              {isLoading ? (
                <>
                  <Skeleton className="h-4 w-16 lg:w-36" />
                  <Skeleton className="size-5" />
                </>
              ) : (
                <>
                  <span className="pr-2">{detectedString}</span>
                  {currUserIsDashboardUser && (
                    <Button 
                      variant="outline"
                      size="sm"
                      className="gap-1"
                      onClick={handleDetectClick}
                    >
                      {/* This would ideally use a loading state like in Angular */}
                      <RefreshCw className="size-4" />
                      <span>Detect</span>
                    </Button>
                  )}
                  {expandEnabled && (
                    <CollapsibleTrigger asChild>
                      <Button variant="ghost" size="icon">
                        <FoldVertical className="size-[18px] text-github-muted-foreground" />
                      </Button>
                    </CollapsibleTrigger>
                  )}
                </>
              )}
            </span>
          </div>
          <div className="flex justify-between font-medium contain-inline-size gap-2">
            <span>
              {isLoading ? (
                <Skeleton className="h-6 w-3/4" />
              ) : (
                <a
                  href={htmlUrl}
                  rel="noopener noreferrer"
                  className="hover:underline"
                  dangerouslySetInnerHTML={{ __html: formattedTitle }}
                />
              )}
            </span>
            <span className="flex items-center space-x-2 pr-4">
              {isLoading ? (
                <>
                  <Skeleton className="h-4 w-8 bg-green-500/30" />
                  <Skeleton className="h-4 w-8 bg-destructive/20" />
                </>
              ) : (
                <>
                  <span className="text-github-success-foreground font-bold">+{additions}</span>
                  <span className="text-github-danger-foreground font-bold">-{deletions}</span>
                </>
              )}
            </span>
          </div>
          {!isLoading && pullRequestLabels && pullRequestLabels.length > 0 && (
            <div className="flex flex-wrap pb-1 gap-2 space-x-0">
              {pullRequestLabels.map(label => (
                <GitHubLabel key={label.id} label={label} />
              ))}
            </div>
          )}
        </div>
        {!isLoading && (
          <div className="gap-2 space-x-0 text-left px-6 pb-2">
            <CollapsibleContent>
              <Separator />
              {badPracticeSummary && (
                <p className="text-sm text-pretty">{badPracticeSummary}</p>
              )}
              
              {orderedBadPractices.map((badpractice) => (
                <React.Fragment key={badpractice.id}>
                  <Separator />
                  <BadPracticeCard
                    id={badpractice.id}
                    title={badpractice.title}
                    description={badpractice.description}
                    state={badpractice.state}
                    currUserIsDashboardUser={currUserIsDashboardUser}
                  />
                </React.Fragment>
              ))}
              
              {orderedOldBadPractices.length > 0 && (
                <>
                  <Separator />
                  <Accordion type="single" collapsible className="w-full">
                    <AccordionItem value="old-practices" className="border-none">
                      <AccordionTrigger>
                        Old good and bad practices
                      </AccordionTrigger>
                      <AccordionContent>
                        {orderedOldBadPractices.map(badpractice => (
                          <React.Fragment key={`old-${badpractice.id}`}>
                            <Separator />
                            <BadPracticeCard
                              id={badpractice.id}
                              title={badpractice.title}
                              description={badpractice.description}
                              state={badpractice.state}
                              currUserIsDashboardUser={currUserIsDashboardUser}
                            />
                          </React.Fragment>
                        ))}
                      </AccordionContent>
                    </AccordionItem>
                  </Accordion>
                </>
              )}
            </CollapsibleContent>
          </div>
        )}
      </Card>
    </Collapsible>
  );
}
