import { Progress } from "@/components/ui/progress";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

export interface LevelBadgeProps {
  level: number;
  className?: string;
}

export function LevelBadge({ level, className }: LevelBadgeProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center min-w-18 p-3 rounded-xl bg-secondary/10 backdrop-blur-sm",
        className,
      )}
    >
      <span className="text-[0.65rem] uppercase text-muted-foreground font-bold tracking-wider">
        LEVEL
      </span>
      <span className="text-3xl font-black text-foreground leading-none tracking-tight">
        {level}
      </span>
    </div>
  );
}

export interface XpProgressProps {
  level: number;
  currentXP: number;
  xpNeeded: number;
  totalXP?: number;
  className?: string;
  showBadge?: boolean;
}

export function XpProgress({
  level,
  currentXP,
  xpNeeded,
  totalXP,
  className,
  showBadge = true,
}: XpProgressProps) {
  // Calculate percentage, guarding against division by zero
  const percentage =
    xpNeeded > 0 ? Math.min(100, Math.max(0, (currentXP / xpNeeded) * 100)) : 0;

  return (
    <div className={cn("w-full", className)}>
      {/* Level Badge - Optional legacy support */}
      {showBadge && <LevelBadge level={level} className="mb-2" />}

      {/* Game-style Status Bar */}
      <div className="flex flex-col gap-1">
        <div className="flex justify-between items-end px-1">
          <span className="text-xs font-bold text-muted-foreground uppercase tracking-wider">
            Developer
          </span>
          <span className="text-xs font-mono font-medium text-muted-foreground">
            {currentXP} / {xpNeeded} XP
          </span>
        </div>

        {/* Bar Container with "metallic" look */}
        <Tooltip>
          <TooltipTrigger asChild>
            <div className="relative h-5 w-full bg-secondary/80 rounded-sm border border-border/50 shadow-inner overflow-hidden cursor-help">
              {/* Animated Gloss Effect */}
              <div className="absolute inset-0 z-10 bg-linear-to-b from-white/10 to-transparent pointer-events-none" />

              <Progress
                className="h-full w-full rounded-none bg-transparent"
                indicatorClassName="bg-gradient-to-r from-primary/80 to-primary rounded-none transition-all duration-500"
                value={percentage}
              />
            </div>
          </TooltipTrigger>
          {totalXP !== undefined && (
            <TooltipContent>
              <p className="font-mono font-medium">Total XP: {totalXP}</p>
            </TooltipContent>
          )}
        </Tooltip>
      </div>
    </div>
  );
}
