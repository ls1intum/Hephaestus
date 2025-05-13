import { formatDistanceToNow, parseISO } from "date-fns";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import {
  CheckIcon,
  CommentIcon,
  FileDiffIcon,
  GitPullRequestIcon,
} from "@primer/octicons-react";
import type { ReviewActivityCardProps } from "../types";
import { cn } from "@/lib/utils";

// Define the styling for different review states
const REVIEW_STATE_STYLES: Record<
  string,
  { icon: React.ElementType; color: string; skeletonColor: string; tooltip: string }
> = {
  APPROVED: {
    icon: CheckIcon,
    color: "text-github-success-foreground",
    skeletonColor: "bg-github-success-foreground/30",
    tooltip: "Approved",
  },
  CHANGES_REQUESTED: {
    icon: FileDiffIcon,
    color: "text-github-danger-foreground",
    skeletonColor: "bg-github-danger-foreground/30",
    tooltip: "Changes Requested",
  },
  COMMENTED: {
    icon: CommentIcon,
    color: "text-github-muted-foreground",
    skeletonColor: "bg-github-muted-foreground/30",
    tooltip: "Commented",
  },
  UNKNOWN: {
    icon: GitPullRequestIcon,
    color: "text-github-muted-foreground",
    skeletonColor: "bg-github-muted-foreground/30",
    tooltip: "Reviewed",
  },
};

export function ReviewActivityCard({
  isLoading,
  state = "UNKNOWN",
  submittedAt,
  htmlUrl,
  pullRequest,
  score,
}: ReviewActivityCardProps) {
  // Get the style for the current review state
  const stateStyle = REVIEW_STATE_STYLES[state] || REVIEW_STATE_STYLES.UNKNOWN;

  // Format relative time from submission date
  const relativeTime = submittedAt
    ? formatDistanceToNow(parseISO(submittedAt), { addSuffix: true })
    : undefined;

  return (
    <Card
      className={cn(
        "overflow-hidden transition-colors border-l-4",
        isLoading
          ? `border-l-${stateStyle.skeletonColor}`
          : `border-l-${stateStyle.color.replace("text-", "")}`
      )}
    >
      <CardContent className="p-4">
        {isLoading ? (
          <div className="flex flex-col gap-2">
            <div className="flex justify-between items-center">
              <Skeleton className="h-6 w-40" />
              <Skeleton className="h-6 w-20" />
            </div>
            <Skeleton className="h-4 w-full" />
            <div className="flex justify-between items-center mt-1">
              <Skeleton className="h-5 w-32" />
              <Skeleton className="h-5 w-16" />
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-1">
            {/* Repository and PR number */}
            <div className="flex justify-between items-center">
              <div className="flex items-center gap-2">
                <Tooltip>
                  <TooltipTrigger asChild>
                    <div className={cn("flex items-center", stateStyle.color)}>
                      <stateStyle.icon size={18} />
                    </div>
                  </TooltipTrigger>
                  <TooltipContent side="top">{stateStyle.tooltip}</TooltipContent>
                </Tooltip>
                <a
                  href={htmlUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="font-medium hover:underline"
                >
                  {pullRequest?.repository?.name} #{pullRequest?.number}
                </a>
              </div>
              {score !== undefined && score > 0 && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <div className="flex items-center gap-1 text-github-accent-foreground font-semibold">
                      +{score}
                    </div>
                  </TooltipTrigger>
                  <TooltipContent side="top">Points earned for this review</TooltipContent>
                </Tooltip>
              )}
            </div>

            {/* PR title */}
            <a
              href={htmlUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              {pullRequest?.title}
            </a>

            {/* Relative time */}
            <div className="flex justify-between items-center mt-1">
              <span className="text-xs text-muted-foreground">{relativeTime}</span>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
