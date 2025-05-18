import { 
  Card, 
  CardContent, 
  CardHeader, 
  CardTitle,
  CardDescription 
} from "@/components/ui/card";
import { 
  CheckIcon, 
  CommentDiscussionIcon, 
  CommentIcon, 
  FileDiffIcon, 
  GitPullRequestIcon,
  InfoIcon
} from "@primer/octicons-react";
import { Button } from "@/components/ui/button";
import { ScoringExplanationDialog } from "./ScoringExplanationDialog";
import { useState } from "react";

export function LeaderboardLegend() {
  const [showScoringModal, setShowScoringModal] = useState(false);

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>
            <InfoIcon className="inline mr-2 h-4 w-4" /> Activity Legend
          </CardTitle>
          <CardDescription>
            Understanding the leaderboard activity indicators
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            <div className="grid grid-cols-1 gap-2">
              <div className="flex items-center gap-2 text-github-muted-foreground">
                <GitPullRequestIcon className="h-4 w-4" />
                <span>Reviewed pull requests</span>
              </div>
              <div className="flex items-center gap-2 text-github-danger-foreground">
                <FileDiffIcon className="h-4 w-4" />
                <span>Changes requested</span>
              </div>
              <div className="flex items-center gap-2 text-github-success-foreground">
                <CheckIcon className="h-4 w-4" />
                <span>Approvals</span>
              </div>
              <div className="flex items-center gap-2 text-github-muted-foreground">
                <CommentIcon className="h-4 w-4" />
                <span>Comments</span>
              </div>
              <div className="flex items-center gap-2 text-github-muted-foreground">
                <CommentDiscussionIcon className="h-4 w-4" />
                <span>Code comments</span>
              </div>
            </div>
            
            <div className="pt-2 border-t">
              <p className="text-sm text-github-muted-foreground mb-2">
                Your score combines your review activity with the complexity of the pull requests you've reviewed.
                Score calculation weighs change requests highest, followed by approvals and comments.
              </p>
              <Button 
                variant="outline" 
                size="sm" 
                className="text-github-link-foreground"
                onClick={() => setShowScoringModal(true)}
              >
                View scoring formula
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <ScoringExplanationDialog 
        open={showScoringModal}
        onOpenChange={setShowScoringModal}
      />
    </>
  );
}