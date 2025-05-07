import React from 'react';
import { 
  AlertCircleIcon, 
  CheckIcon,
  BanIcon,
  HelpCircleIcon,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

// Enum to match the Angular enum
export enum BadPracticeState {
  Open = 'OPEN',
  Fixed = 'FIXED',
  WontFix = 'WONT_FIX',
  Feedback = 'FEEDBACK'
}

// Config for icons and text based on state
const stateConfig: Record<BadPracticeState, { icon: React.ReactNode; text: string }> = {
  [BadPracticeState.Open]: { 
    icon: <AlertCircleIcon className="size-6 text-amber-500" />,
    text: 'Open Issue'
  },
  [BadPracticeState.Fixed]: { 
    icon: <CheckIcon className="size-6 text-green-600" />,
    text: 'Fixed'
  },
  [BadPracticeState.WontFix]: { 
    icon: <BanIcon className="size-6 text-gray-500" />,
    text: 'Won\'t Fix'
  },
  [BadPracticeState.Feedback]: { 
    icon: <HelpCircleIcon className="size-6 text-blue-600" />,
    text: 'Feedback Submitted'
  }
};

export interface BadPracticeCardProps {
  title: string;
  description: string;
  state: BadPracticeState;
  id: number;
  onResolveAsFixed?: (id: number) => void;
  onResolveAsWontFix?: (id: number) => void;
  onProvideFeedback?: (id: number, type: string, explanation: string) => void;
}

export function BadPracticeCard({
  title,
  description,
  state,
  id,
  onResolveAsFixed,
  onResolveAsWontFix,
  onProvideFeedback
}: BadPracticeCardProps) {
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [selectedType, setSelectedType] = React.useState<string>('');
  const [explanation, setExplanation] = React.useState('');
  
  const feedbackTypes = [
    'Not a bad practice', 
    'Irrelevant', 
    'Incorrect', 
    'Imprecise', 
    'Other'
  ];
  
  const handleSubmitFeedback = () => {
    if (onProvideFeedback) {
      onProvideFeedback(id, selectedType || 'Other', explanation);
    }
    setDialogOpen(false);
  };
  
  const { icon, text } = stateConfig[state];
  
  return (
    <div className="flex flex-row justify-between items-center gap-2">
      <div className="flex flex-row justify-start items-center gap-4">
        <div>
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger asChild>
                {icon}
              </TooltipTrigger>
              <TooltipContent>
                <p>{text}</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        </div>
        <div className="flex flex-col">
          <h3 className="text-md font-semibold">{title}</h3>
          <p className="text-sm text-pretty">{description}</p>
        </div>
      </div>
      
      <div className="justify-self-end">
        <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline">Resolve</Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent className="w-48">
              <DropdownMenuGroup>
                <DropdownMenuItem onClick={() => onResolveAsFixed?.(id)}>
                  Resolve as fixed
                </DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <DropdownMenuItem onClick={() => onResolveAsWontFix?.(id)}>
                  Resolve as won't fix
                </DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <DialogTrigger asChild>
                  <DropdownMenuItem>Mark as wrong</DropdownMenuItem>
                </DialogTrigger>
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
                <Label htmlFor="feedback-type" className="text-right">Feedback</Label>
                <Select 
                  value={selectedType} 
                  onValueChange={setSelectedType}
                >
                  <SelectTrigger className="col-span-3">
                    <SelectValue placeholder="Select the type of feedback" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {feedbackTypes.map(type => (
                        <SelectItem key={type} value={type}>
                          {type}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </div>
              <div className="items-start grid grid-cols-4 gap-4 h-40">
                <Label htmlFor="explanation" className="text-right">Explanation</Label>
                <Textarea 
                  id="explanation" 
                  className="col-span-3 h-full" 
                  value={explanation}
                  onChange={(e) => setExplanation(e.target.value)}
                />
              </div>
            </div>
            <DialogFooter>
              <Button type="submit" onClick={handleSubmitFeedback}>
                Submit feedback
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  );
}