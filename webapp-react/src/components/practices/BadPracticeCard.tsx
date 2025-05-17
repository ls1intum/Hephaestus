import type { PullRequestBadPractice } from "@/api/types.gen";
import { stateConfig } from "./utils";
import { 
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useState } from "react";

interface BadPracticeCardProps {
  id: number;
  title: string;
  description: string;
  state: PullRequestBadPractice["state"];
  currUserIsDashboardUser?: boolean;
  onResolveBadPracticeAsFixed?: (id: number) => void;
}

export function BadPracticeCard({
  id,
  title,
  description,
  state,
  currUserIsDashboardUser = false,
  onResolveBadPracticeAsFixed,
}: BadPracticeCardProps) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const stateInfo = stateConfig[state];
  const Icon = stateInfo.icon;

  const handleResolveAsFixed = () => {
    onResolveBadPracticeAsFixed?.(id);
  };

  const handleProvideFeedback = () => {
    setDialogOpen(false);
    // In the future, add implementation for feedback
  };

  return (
    <div className="flex flex-row justify-between items-center gap-2">
      <div className="flex flex-row justify-start items-center gap-4">
        <div>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger>
                <Icon className={`size-5 ${stateInfo.color}`} />
              </TooltipTrigger>
              <TooltipContent>
                <span>{stateInfo.text}</span>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
        <div className="flex flex-col">
          <h3 className="text-md font-semibold">{title}</h3>
          <p className="text-sm text-pretty">{description}</p>
        </div>
      </div>
      {currUserIsDashboardUser && (
        <div className="justify-self-end">
          <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline">Resolve</Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent className="w-56">
                <DropdownMenuGroup>
                  <DropdownMenuItem onClick={handleResolveAsFixed}>
                    Resolve as fixed
                  </DropdownMenuItem>
                </DropdownMenuGroup>
                <DropdownMenuGroup>
                  <DropdownMenuItem>
                    Resolve as won't fix
                  </DropdownMenuItem>
                </DropdownMenuGroup>
                <DropdownMenuGroup>
                  <DropdownMenuItem onClick={() => setDialogOpen(true)}>
                    Resolve as wrong
                  </DropdownMenuItem>
                </DropdownMenuGroup>
                <DropdownMenuSeparator />
                <DropdownMenuGroup>
                  <DropdownMenuItem onClick={() => setDialogOpen(true)}>
                    Provide feedback
                  </DropdownMenuItem>
                </DropdownMenuGroup>
              </DropdownMenuContent>
            </DropdownMenu>

            <DialogContent>
              <DialogHeader>
                <DialogTitle>Provide feedback</DialogTitle>
                <DialogDescription>
                  Mark this bad practice with feedback that helps us improve the bad practice detection.
                </DialogDescription>
              </DialogHeader>
              <div className="py-4 grid gap-4">
                <div className="items-center grid grid-cols-4 gap-4">
                  {/* Feedback form controls would go here */}
                </div>
                <div className="items-start grid grid-cols-4 gap-4 h-40">
                  {/* Additional form controls would go here */}
                </div>
              </div>
              <DialogFooter>
                <Button type="submit" onClick={handleProvideFeedback}>
                  Submit feedback
                </Button>
              </DialogFooter>
              <div className="py-4 grid gap-4">
                <div className="items-center grid grid-cols-4 gap-4">
                  {/* Future form elements would go here */}
                </div>
                <div className="items-start grid grid-cols-4 gap-4 h-40">
                  {/* Future text area would go here */}
                </div>
              </div>
              <DialogFooter>
                <Button type="submit" onClick={handleProvideFeedback}>
                  Submit feedback
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      )}
    </div>
  );
}
