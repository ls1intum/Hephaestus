import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Award, CheckCircle, FileText, GitPullRequest, MessageSquare } from "lucide-react";
import type { LeaderboardLegendProps } from "./types";

export function LeaderboardLegend({}: LeaderboardLegendProps) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-lg">Legend</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="space-y-2">
          <h3 className="text-sm font-medium">League Tiers</h3>
          <div className="flex flex-col space-y-2 border rounded-md p-2">
            <div className="flex items-center gap-2">
              <Award className="h-5 w-5 text-[hsl(var(--league-bronze))] fill-current" />
              <span className="text-sm">Bronze: &lt; 500 points</span>
            </div>
            <div className="flex items-center gap-2">
              <Award className="h-5 w-5 text-[hsl(var(--league-silver))] fill-current" />
              <span className="text-sm">Silver: 500-999 points</span>
            </div>
            <div className="flex items-center gap-2">
              <Award className="h-5 w-5 text-[hsl(var(--league-gold))] fill-current" />
              <span className="text-sm">Gold: 1000-1499 points</span>
            </div>
            <div className="flex items-center gap-2">
              <Award className="h-5 w-5 text-[hsl(var(--league-diamond))] fill-current" />
              <span className="text-sm">Diamond: 1500-1999 points</span>
            </div>
            <div className="flex items-center gap-2">
              <Award className="h-5 w-5 text-[hsl(var(--league-master))] fill-current" />
              <span className="text-sm">Master: 2000+ points</span>
            </div>
          </div>
        </div>
        <div className="space-y-2">
          <h3 className="text-sm font-medium">Activity Icons</h3>
          <div className="flex flex-col space-y-2 border rounded-md p-2">
            <div className="flex items-center gap-2">
              <GitPullRequest className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm">Reviewed PRs</span>
            </div>
            <div className="flex items-center gap-2">
              <FileText className="h-4 w-4 text-destructive" />
              <span className="text-sm">Changes Requested</span>
            </div>
            <div className="flex items-center gap-2">
              <CheckCircle className="h-4 w-4 text-success" />
              <span className="text-sm">Approvals</span>
            </div>
            <div className="flex items-center gap-2">
              <MessageSquare className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm">Comments</span>
            </div>
            <div className="flex items-center gap-2">
              <GitPullRequest className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm">Code Comments</span>
            </div>
          </div>
        </div>
        <div className="space-y-2">
          <h3 className="text-sm font-medium">Score Calculation</h3>
          <p className="text-xs text-muted-foreground">
            The score is calculated based on review activities: 
            3 points for approvals, 2 points for changes requested, 
            and 1 point each for comments and code comments.
          </p>
        </div>
      </CardContent>
    </Card>
  );
}