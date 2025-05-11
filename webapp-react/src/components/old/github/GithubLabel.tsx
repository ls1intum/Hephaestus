import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

export interface GithubLabelProps {
  label: {
    name: string;
    color: string;
    description?: string;
  };
  className?: string;
}

export function GithubLabel({ label, className }: GithubLabelProps) {
  // Calculate contrasting text color based on background color
  const getContrastColor = (hexColor: string): string => {
    // Convert hex to RGB
    const r = parseInt(hexColor.substring(0, 2), 16);
    const g = parseInt(hexColor.substring(2, 4), 16);
    const b = parseInt(hexColor.substring(4, 6), 16);
    
    // Calculate luminance - this is a better method than simple brightness
    const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    
    return luminance > 0.5 ? '#000000' : '#FFFFFF';
  };

  const backgroundColor = `#${label.color}`;
  const textColor = getContrastColor(label.color);
  
  const labelContent = (
    <Badge 
      className={cn(
        "rounded-full text-xs font-medium px-2 py-0.5 border-none",
        className
      )}
      style={{ 
        backgroundColor, 
        color: textColor
      }}
    >
      {label.name}
    </Badge>
  );

  // If there's a description, wrap in tooltip
  if (label.description) {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            {labelContent}
          </TooltipTrigger>
          <TooltipContent>
            {label.description}
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }
  
  return labelContent;
}