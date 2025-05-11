import { useState } from "react";
import { Button } from "@/components/ui/button";
import { GitPullRequest } from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import type { ReviewsPopoverProps } from "./types";

export function ReviewsPopover({ reviewedPRs, highlight = false }: ReviewsPopoverProps) {
  const [isOpen, setIsOpen] = useState(false);
  const hasReviews = reviewedPRs?.length > 0;

  // Helper function to copy PR URLs to clipboard
  const copyPullRequests = () => {
    if (!hasReviews) return;
    
    const prUrls = reviewedPRs.map(pr => pr.htmlUrl).join('\n');
    navigator.clipboard.writeText(prUrls);
    setIsOpen(false);
  };

  return (
    <Popover open={isOpen} onOpenChange={setIsOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          disabled={!hasReviews}
          className={cn(
            "flex items-center gap-1",
            !highlight 
              ? "text-muted-foreground" 
              : "border-primary bg-accent hover:bg-primary hover:text-background"
          )}
          onClick={(e) => e.stopPropagation()}
        >
          <GitPullRequest size={16} />
          {reviewedPRs?.length || 0}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="space-y-2 w-60">
        <div className="flex flex-wrap justify-between items-center gap-4">
          <div className="flex items-center gap-2">
            <GitPullRequest size={20} />
            <h4 className="font-medium leading-none">Reviewed PRs</h4>
          </div>
          <Button variant="outline" size="icon" onClick={copyPullRequests}>
            {/* You could add a copy icon here */}
          </Button>
        </div>
        {/* Here you could add a list of the PRs if you want to display them */}
      </PopoverContent>
    </Popover>
  );
}