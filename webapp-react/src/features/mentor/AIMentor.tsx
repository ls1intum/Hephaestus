import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { Link } from "@tanstack/react-router";
import { BotMessageSquare } from "lucide-react";

interface AIMentorProps {
  /** Whether to show only the icon without text */
  iconOnly?: boolean;
}

export default function AIMentor({ iconOnly = false }: AIMentorProps) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            variant="outline"
            size={iconOnly ? "icon" : "default"}
            className="border-cyan-500 dark:border-cyan-500 text-cyan-500 hover:text-cyan-500 hover:bg-cyan-500/10 dark:hover:bg-cyan-500/10 flex gap-2"
            asChild
          >
            <Link
              to="/mentor"
              aria-describedby="AI Mentor"
            >
              <BotMessageSquare className="h-4 w-4" />
              {!iconOnly && <span>AI&nbsp;Mentor</span>}
            </Link>
          </Button>
        </TooltipTrigger>
        {iconOnly && (
          <TooltipContent>
            AI&nbsp;Mentor
          </TooltipContent>
        )}
      </Tooltip>
    </TooltipProvider>
  );
}