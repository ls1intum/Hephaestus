import { format, parseISO } from "date-fns";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  GitPullRequestIcon,
  GitPullRequestDraftIcon,
  GitMergeIcon,
  GitPullRequestClosedIcon,
} from "@primer/octicons-react";
import type { IssueCardProps } from ".";
import { cn } from "@/lib/utils";
import { GithubBadge } from "@/components/profile/GithubBadge";

export function IssueCard({
  isLoading,
  title,
  number,
  additions,
  deletions,
  htmlUrl,
  repositoryName,
  createdAt,
  state,
  isDraft,
  isMerged,
  pullRequestLabels = [],
}: IssueCardProps) {
  // Determine the PR state icon and color
  const getIssueIconAndColor = () => {
    if (state === "OPEN") {
      if (isDraft) {
        return { icon: GitPullRequestDraftIcon, color: "text-github-muted-foreground" };
      } else {
        return { icon: GitPullRequestIcon, color: "text-github-open-foreground" };
      }
    } else {
      if (isMerged) {
        return { icon: GitMergeIcon, color: "text-github-done-foreground" };
      } else {
        return { icon: GitPullRequestClosedIcon, color: "text-github-closed-foreground" };
      }
    }
  };

  const { icon: StateIcon, color } = getIssueIconAndColor();
  
  // Format the date as MMM D (e.g., "Jan 15")
  const formattedDate = createdAt
    ? format(parseISO(createdAt), "MMM d")
    : "";

  // Format the title, replacing code blocks with styled code
  const formattedTitle = title
    ? title.replace(/`([^`]+)`/g, '<code class="bg-accent/50 px-1 py-0.5 rounded font-mono">$1</code>')
    : "";

  // Use CSS to style the card as a clickable link with hover effects
  return (
    <a 
      href={htmlUrl} 
      target="_blank" 
      rel="noopener noreferrer"
      className="block w-full"
    >
      <Card className="rounded-lg border border-border bg-card text-card-foreground shadow-sm hover:bg-accent/50 cursor-pointer py-0 gap-0">
        <div className={cn("flex flex-col gap-1 p-6", { "pb-0": isLoading || pullRequestLabels.length > 0 })}>
          <div className="flex justify-between gap-2 items-center text-sm text-github-muted-foreground">
            <span className="font-medium flex justify-center items-center space-x-1">
              {isLoading ? (
                <>
                  <Skeleton className="size-5 bg-green-500/30" />
                  <Skeleton className="h-4 w-16 lg:w-36" />
                </>
              ) : (
                <>
                  <StateIcon className={`mr-2 ${color}`} size={18} />
                  <span className="whitespace-nowrap">{repositoryName} #{number} on {formattedDate}</span>
                </>
              )}
            </span>
            <span className="flex items-center space-x-2">
              {isLoading ? (
                <>
                  <Skeleton className="h-4 w-8 bg-green-500/30" />
                  <Skeleton className="h-4 w-8 bg-destructive/20" />
                </>
              ) : (
                <>
                  {additions !== undefined && (
                    <span className="text-github-success-foreground font-bold">+{additions}</span>
                  )}
                  {deletions !== undefined && (
                    <span className="text-github-danger-foreground font-bold">-{deletions}</span>
                  )}
                </>
              )}
            </span>
          </div>

          <span className="flex justify-between font-medium contain-inline-size">
            {isLoading ? (
              <Skeleton className="h-6 w-3/4 mb-6" />
            ) : (
              <div className="leading-normal" dangerouslySetInnerHTML={{ __html: formattedTitle }} />
            )}
          </span>
        </div>
        
        {pullRequestLabels.length > 0 && (
          <div className="flex flex-row items-center flex-wrap gap-2 p-6 pt-2">
            {pullRequestLabels.map((label) => (
              <GithubBadge
                key={label.id}
                label={label.name}
                color={label.color}
              />
            ))}
          </div>
        )}
      </Card>
    </a>
  );
}
