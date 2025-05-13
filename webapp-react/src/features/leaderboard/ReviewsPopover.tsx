import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { GitPullRequestIcon, CheckIcon, CopyIcon } from "@primer/octicons-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import type { ReviewsPopoverProps } from "./types";
import { ScrollArea } from "@/components/ui/scroll-area";
import { CardTitle } from "@/components/ui/card";

export function ReviewsPopover({ reviewedPRs, highlight = false }: ReviewsPopoverProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [showCopySuccess, setShowCopySuccess] = useState(false);
  const hasReviews = reviewedPRs?.length > 0;

  // Sort reviewed PRs by repository name and PR number
  const sortedReviewedPRs = reviewedPRs?.sort((a, b) => {
    if (a.repository?.name === b.repository?.name) {
      return a.number - b.number;
    }
    return (a.repository?.name ?? '').localeCompare(b.repository?.name ?? '');
  }) || [];

  // Helper function to copy PR URLs to clipboard
  const copyPullRequests = () => {
    if (!hasReviews) return;
    
    // Create HTML for clipboard
    const htmlList = `<ul>
      ${sortedReviewedPRs
        .map((pullRequest) => `<li><a href="${pullRequest.htmlUrl}">${pullRequest.repository?.name ?? ''} #${pullRequest.number}</a></li>`)
        .join('\n')}
    </ul>`;

    // Create markdown text
    const plainText = sortedReviewedPRs
      .map((pullRequest) => `[${pullRequest.repository?.name ?? ''} #${pullRequest.number}](${pullRequest.htmlUrl})`)
      .join('\n');
    
    try {
      // Try to use the ClipboardItem API (modern browsers)
      const clipboardItem = new ClipboardItem({
        'text/html': new Blob([htmlList], { type: 'text/html' }),
        'text/plain': new Blob([plainText], { type: 'text/plain' })
      });
      
      navigator.clipboard.write([clipboardItem]).catch(() => {
        // Fallback to plain text if html copying fails
        navigator.clipboard.writeText(plainText);
      });
    } catch (e) {
      // Basic fallback for older browsers
      navigator.clipboard.writeText(plainText);
    }

    // Show success indication
    setShowCopySuccess(true);
    
    // Reset the success indicator after 2 seconds
    setTimeout(() => {
      setShowCopySuccess(false);
    }, 2000);
  };

  // Clear timeout when component unmounts
  useEffect(() => {
    return () => {
      if (showCopySuccess) {
        setShowCopySuccess(false);
      }
    };
  }, []);

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
              ? "text-github-muted-foreground" 
              : "border-primary bg-accent hover:bg-primary hover:text-background"
          )}
          onClick={(e) => e.stopPropagation()}
        >
          <GitPullRequestIcon size={16} />
          {reviewedPRs?.length || 0}
        </Button>
      </PopoverTrigger>
      <PopoverContent 
        className="space-y-2 w-60" 
        sideOffset={5} 
        onClick={(e) => e.stopPropagation()}>
        <div className="flex flex-wrap justify-between items-center gap-4">
          <CardTitle className="flex items-center gap-2">
            <GitPullRequestIcon size={20} />
            <h4 className="font-medium leading-none">Reviewed PRs</h4>
          </CardTitle>
          <Button variant="outline" size="icon" onClick={copyPullRequests}>
            {showCopySuccess ? (
              <CheckIcon className="text-green-600 size-4" />
            ) : (
              <CopyIcon className="size-4" />
            )}
          </Button>
        </div>
        {hasReviews && (
          <ScrollArea 
            className="rounded-md" 
            style={{ 
              height: `min(200px, ${36 * sortedReviewedPRs.length}px)` 
            }}
          >
            <div className="flex flex-col rounded-md text-muted-foreground text-sm pr-2.5">
              {sortedReviewedPRs.map((pullRequest) => (
                <a 
                  key={pullRequest.id}
                  href={pullRequest.htmlUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={cn(
                    "px-3 py-2 hover:bg-accent rounded-md justify-start",
                    "transition-colors duration-200"
                  )}
                  title={pullRequest.title}
                >
                  {pullRequest.repository?.name ?? ''} #{pullRequest.number}
                </a>
              ))}
            </div>
          </ScrollArea>
        )}
      </PopoverContent>
    </Popover>
  );
}