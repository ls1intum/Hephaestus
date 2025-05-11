import type { LeaderboardTableProps } from "./types";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { LeagueIcon } from "./league/LeagueIcon";
import { ReviewsPopover } from "./ReviewsPopover";
import { Award, CheckCircle, ChevronLeft, FileText, GitPullRequest, MessageSquare, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";

export function LeaderboardTable({
  leaderboard = [],
  isLoading,
  currentUser,
}: LeaderboardTableProps) {
  if (isLoading) {
    return <LeaderboardTableSkeleton />;
  }

  if (!leaderboard.length) {
    return (
      <div className="flex flex-col items-center justify-center p-8 text-center">
        <XCircle className="h-12 w-12 text-destructive mb-2" />
        <h3 className="text-lg font-medium">No entries found</h3>
        <p className="text-muted-foreground">
          There are no leaderboard entries available.
        </p>
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="text-center w-16">Rank</TableHead>
          <TableHead className="text-center w-20">League</TableHead>
          <TableHead>Contributor</TableHead>
          <TableHead className="text-center">
            <div className="flex justify-center items-center gap-1 text-primary">
              <Award className="h-4 w-4" />
              <span>Score</span>
            </div>
          </TableHead>
          <TableHead>Activity</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {leaderboard.map((entry) => {
          const isCurrentUser = currentUser?.login.toLowerCase() === entry.user.login.toLowerCase();
          
          return (
            <TableRow
              key={entry.user.login}
              className={cn(
                "cursor-pointer", 
                isCurrentUser && "bg-accent dark:bg-accent/30 dark:hover:bg-accent/50"
              )}
              onClick={() => window.location.href = `/user/${entry.user.login}`}
            >
              <TableCell className="text-center">{entry.rank}</TableCell>
              <TableCell className="px-0">
                <div className="flex flex-col justify-center items-center">
                  <LeagueIcon 
                    leaguePoints={entry.user.leaguePoints} 
                    showPoints 
                  />
                </div>
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-2 font-medium">
                  <Avatar>
                    <AvatarImage 
                      src={entry.user.avatarUrl} 
                      alt={`${entry.user.name}'s avatar`} 
                    />
                    <AvatarFallback>
                      {entry.user.name.slice(0, 2).toUpperCase()}
                    </AvatarFallback>
                  </Avatar>
                  <span className="text-muted-foreground">
                    {entry.user.name}
                  </span>
                </div>
              </TableCell>
              <TableCell className="text-center font-medium">
                {entry.score}
              </TableCell>
              <TableCell>
                <div className="flex items-center gap-2">
                  {entry.numberOfReviewedPRs > 0 && (
                    <>
                      <ReviewsPopover 
                        reviewedPRs={entry.reviewedPullRequests} 
                        highlight={isCurrentUser}
                      />
                      <div className="flex items-center text-muted-foreground">
                        <ChevronLeft className="h-4 w-4" />
                      </div>
                    </>
                  )}
                  {entry.numberOfChangeRequests > 0 && (
                    <div 
                      className="flex items-center gap-1 text-destructive" 
                      title="Changes Requested"
                    >
                      <FileText className="h-4 w-4" />
                      <span>{entry.numberOfChangeRequests}</span>
                    </div>
                  )}
                  {entry.numberOfApprovals > 0 && (
                    <div 
                      className="flex items-center gap-1 text-success" 
                      title="Approvals"
                    >
                      <CheckCircle className="h-4 w-4" />
                      <span>{entry.numberOfApprovals}</span>
                    </div>
                  )}
                  {entry.numberOfComments + (entry.numberOfUnknowns || 0) > 0 && (
                    <div 
                      className="flex items-center gap-1 text-muted-foreground" 
                      title="Comments"
                    >
                      <MessageSquare className="h-4 w-4" />
                      <span>
                        {entry.numberOfComments + (entry.numberOfUnknowns || 0)}
                      </span>
                    </div>
                  )}
                  {entry.numberOfCodeComments > 0 && (
                    <div 
                      className="flex items-center gap-1 text-muted-foreground" 
                      title="Code comments"
                    >
                      <GitPullRequest className="h-4 w-4" />
                      <span>{entry.numberOfCodeComments}</span>
                    </div>
                  )}
                </div>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

function LeaderboardTableSkeleton() {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="text-center w-16">Rank</TableHead>
          <TableHead className="text-center w-20">League</TableHead>
          <TableHead>Contributor</TableHead>
          <TableHead className="text-center">Score</TableHead>
          <TableHead>Activity</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {Array.from({ length: 10 }).map((_, idx) => (
          <TableRow key={idx}>
            <TableCell>
              <Skeleton className="h-5 w-7" style={{ width: `${20 + 1 * idx}px` }} />
            </TableCell>
            <TableCell>
              <Skeleton className="h-8 w-8 mx-auto" />
            </TableCell>
            <TableCell className="py-2">
              <div className="flex items-center gap-2">
                <Skeleton className="w-10 h-10 rounded-full" />
                <Skeleton className="h-5" style={{ width: `${100 + (idx % 3) * 75}px` }} />
              </div>
            </TableCell>
            <TableCell className="text-center">
              <Skeleton className="h-5 mx-auto" style={{ width: `${20 + (10 - idx) + (idx % 3) * 4}px` }} />
            </TableCell>
            <TableCell className="py-2">
              <Skeleton className="h-5" style={{ width: `${30 + ((idx % 4) * 20) / (idx + 1)}px` }} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}