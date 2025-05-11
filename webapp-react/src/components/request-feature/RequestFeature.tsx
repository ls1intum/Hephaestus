import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Sparkles } from "lucide-react";

interface RequestFeatureProps {
  /** Whether to show only the icon without text */
  iconOnly?: boolean;
}

export default function RequestFeature({ iconOnly = false }: RequestFeatureProps) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="outline"
            size={iconOnly ? "icon" : "default"}
            className="border-github-upsell-foreground dark:border-github-upsell-foreground text-github-upsell-foreground hover:text-github-upsell-foreground hover:bg-github-upsell-foreground/10 dark:hover:bg-github-upsell-foreground/10 flex gap-2"
            asChild
          >
            <a
              href="https://hephaestus.canny.io/feature-requests"
              data-canny-link
              aria-describedby="Request a feature"
            >
              <Sparkles className="h-4 w-4" />
              {!iconOnly && <span>Request&nbsp;a&nbsp;feature</span>}
            </a>
          </Button>
        </TooltipTrigger>
        {iconOnly && (
          <TooltipContent className="bg-purple-100 dark:bg-purple-800 dark:border-purple-500 border-purple-300">
            <span className="flex items-center">Request a feature</span>
          </TooltipContent>
        )}
      </Tooltip>
    </TooltipProvider>
  );
}