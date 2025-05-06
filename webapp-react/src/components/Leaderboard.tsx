import type { LeaderboardEntry, PullRequestInfo } from '@/lib/api/models';
import { 
  TrophyIcon,
  CheckCircleIcon,
  CodeIcon,
  GitPullRequestIcon,
  CommentIcon,
  ChevronLeftIcon,
  DiffIcon
} from "@primer/octicons-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

interface LeaderboardProps {
  leaderboard: LeaderboardEntry[];
  isLoading?: boolean;
}

export default function Leaderboard({ leaderboard, isLoading = false }: LeaderboardProps) {
  if (isLoading) {
    return <LeaderboardSkeleton />;
  }

  return (
    <table className="w-full">
      <thead>
        <tr className="border-b">
          <th className="text-center p-2">Rank</th>
          <th className="text-center p-2 px-0.5">League</th>
          <th className="text-left p-2">Contributor</th>
          <th className="flex justify-center items-center gap-1 text-amber-600 p-2">
            <TrophyIcon className="w-4 h-4" />
            Score
          </th>
          <th className="text-left p-2">Activity</th>
        </tr>
      </thead>
      <tbody>
        {leaderboard.length === 0 ? (
          <tr className="border-b">
            <td colSpan={5}>
              <div className="flex flex-col items-center justify-center gap-2 mt-1 py-6">
                <CodeIcon className="w-8 h-8 text-destructive" />
                <span className="font-semibold text-muted-foreground">No entries found</span>
              </div>
            </td>
          </tr>
        ) : (
          leaderboard.map((entry) => (
            <tr 
              key={entry.user.login} 
              id={`rank-${entry.rank}`}
              className="border-b hover:bg-muted/50 cursor-pointer"
            >
              <td className="text-center p-2">{entry.rank}</td>
              <td className="text-center p-2 px-0.5">
                <div className="flex flex-col items-center">
                  <LeagueIcon leaguePoints={entry.user.leaguePoints} />
                  <span className="text-xs font-semibold text-muted-foreground">{entry.user.leaguePoints}</span>
                </div>
              </td>
              <td className="p-2">
                <span className="flex items-center gap-2 font-medium">
                  <Avatar>
                    <AvatarImage src={entry.user.avatarUrl} alt={`${entry.user.name}'s avatar`} />
                    <AvatarFallback>{entry.user.name?.slice(0, 2)?.toUpperCase() || '??'}</AvatarFallback>
                  </Avatar>
                  <span className="text-muted-foreground">{entry.user.name}</span>
                </span>
              </td>
              <td className="text-center p-2">{entry.score}</td>
              <td className="p-2">
                <div className="flex items-center gap-2">
                  {entry.numberOfReviewedPRs > 0 && (
                    <>
                      <ReviewsPopover reviewedPRs={entry.reviewedPullRequests} />
                      <div className="flex items-center text-github-muted-foreground">
                        <ChevronLeftIcon className="w-4 h-4" />
                      </div>
                    </>
                  )}
                  <div className="flex items-center gap-2">
                    {entry.numberOfChangeRequests > 0 && (
                      <TooltipProvider>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <div className="flex items-center gap-1 text-github-danger-foreground">
                              <DiffIcon className="w-4 h-4" />
                              {entry.numberOfChangeRequests}
                            </div>
                          </TooltipTrigger>
                          <TooltipContent>
                            <p>Changes Requested</p>
                          </TooltipContent>
                        </Tooltip>
                      </TooltipProvider>
                    )}
                    {entry.numberOfApprovals > 0 && (
                      <TooltipProvider>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <div className="flex items-center gap-1 text-github-success-foreground">
                              <CheckCircleIcon className="w-4 h-4" />
                              {entry.numberOfApprovals}
                            </div>
                          </TooltipTrigger>
                          <TooltipContent>
                            <p>Approvals</p>
                          </TooltipContent>
                        </Tooltip>
                      </TooltipProvider>
                    )}
                    {(entry.numberOfComments + entry.numberOfUnknowns) > 0 && (
                      <TooltipProvider>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <div className="flex items-center gap-1 text-github-muted-foreground">
                              <CommentIcon className="w-4 h-4" />
                              {entry.numberOfComments + entry.numberOfUnknowns}
                            </div>
                          </TooltipTrigger>
                          <TooltipContent>
                            <p>Comments</p>
                          </TooltipContent>
                        </Tooltip>
                      </TooltipProvider>
                    )}
                    {entry.numberOfCodeComments > 0 && (
                      <TooltipProvider>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <div className="flex items-center gap-1 text-github-muted-foreground">
                              <CodeIcon className="w-4 h-4" />
                              {entry.numberOfCodeComments}
                            </div>
                          </TooltipTrigger>
                          <TooltipContent>
                            <p>Code comments</p>
                          </TooltipContent>
                        </Tooltip>
                      </TooltipProvider>
                    )}
                  </div>
                </div>
              </td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}

// Reviews Popover component
function ReviewsPopover({ reviewedPRs }: { reviewedPRs: PullRequestInfo[] }) {
  const limitedPRs = reviewedPRs.slice(0, 5); // Show only first 5 PRs
  
  return (
    <Popover>
      <PopoverTrigger asChild>
        <div className="flex items-center gap-1 text-github-muted-foreground cursor-pointer hover:text-foreground">
          <GitPullRequestIcon className="w-4 h-4" />
          {reviewedPRs.length}
        </div>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-0">
        <div className="border-b px-3 py-2 font-medium">
          Reviewed Pull Requests
        </div>
        <div className="max-h-80 overflow-auto">
          {limitedPRs.map((pr) => (
            <div key={pr.id} className="px-3 py-2 flex items-start gap-2 hover:bg-muted text-sm border-b last:border-0">
              <div className="pt-0.5">
                <GitPullRequestIcon className={`w-4 h-4 ${getPrStateColor(pr)}`} />
              </div>
              <div className="flex-1 flex flex-col">
                <a 
                  href={pr.htmlUrl} 
                  target="_blank" 
                  rel="noopener noreferrer" 
                  className="text-foreground hover:underline font-medium line-clamp-2"
                >
                  {pr.title}
                </a>
                <div className="text-xs text-muted-foreground">
                  #{pr.number} • +{pr.additions} −{pr.deletions}
                </div>
              </div>
            </div>
          ))}
          {reviewedPRs.length > 5 && (
            <div className="px-3 py-2 text-sm text-muted-foreground text-center">
              +{reviewedPRs.length - 5} more pull requests
            </div>
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}

// Helper function to get color based on PR state
function getPrStateColor(pr: PullRequestInfo): string {
  if (pr.isMerged) return "text-purple-500";
  if (pr.isDraft) return "text-muted-foreground";
  if (pr.state === 'OPEN') return "text-github-success-foreground";
  return "text-github-danger-foreground";
}

// League icon based on points
function LeagueIcon({ leaguePoints = 0 }: { leaguePoints?: number }) {
  let color = "text-zinc-500"; // Default
  
  if (leaguePoints >= 2000) {
    color = "text-yellow-500"; // Gold
  } else if (leaguePoints >= 1500) {
    color = "text-purple-500"; // Diamond
  } else if (leaguePoints >= 1000) {
    color = "text-blue-500"; // Platinum
  } else if (leaguePoints >= 500) {
    color = "text-green-500"; // Silver
  }
  
  return <TrophyIcon className={`w-5 h-5 ${color}`} />;
}

// Skeleton loading state for the leaderboard
function LeaderboardSkeleton() {
  return (
    <table className="w-full">
      <thead>
        <tr className="border-b">
          <th className="text-center p-2">Rank</th>
          <th className="text-center p-2">League</th>
          <th className="text-left p-2">Contributor</th>
          <th className="text-center p-2">Score</th>
          <th className="text-left p-2">Activity</th>
        </tr>
      </thead>
      <tbody>
        {Array.from({ length: 10 }).map((_, idx) => (
          <tr key={idx} className="border-b">
            <td className="p-2">
              <div className="h-5 bg-muted rounded animate-pulse" style={{ width: `${20 + idx}px` }}></div>
            </td>
            <td className="p-2">
              <div className="h-5 w-5 bg-muted rounded-full animate-pulse mx-auto"></div>
            </td>
            <td className="p-2">
              <div className="flex items-center gap-2">
                <div className="h-10 w-10 bg-muted rounded-full animate-pulse"></div>
                <div className="h-5 w-24 bg-muted rounded animate-pulse"></div>
              </div>
            </td>
            <td className="p-2">
              <div className="h-5 w-8 bg-muted rounded animate-pulse mx-auto"></div>
            </td>
            <td className="p-2">
              <div className="flex items-center gap-2">
                <div className="h-5 w-6 bg-muted rounded animate-pulse"></div>
                <div className="h-5 w-6 bg-muted rounded animate-pulse"></div>
                <div className="h-5 w-6 bg-muted rounded animate-pulse"></div>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}