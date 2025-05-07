import { useState } from 'react';
import { Check, ChevronsUpDown, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem } from '@/components/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { cn } from '@/lib/utils';
import type { LabelInfo } from './types';
import { GithubLabel } from '../github/GithubLabel';

interface LabelSelectorProps {
  labels: LabelInfo[];
  selectedLabelIds?: number[];
  onSelect: (labelId: number) => void;
  isLoading?: boolean;
  disabled?: boolean;
  placeholder?: string;
}

export function LabelSelector({
  labels,
  selectedLabelIds = [],
  onSelect,
  isLoading = false,
  disabled = false,
  placeholder = 'Select a label'
}: LabelSelectorProps) {
  const [open, setOpen] = useState(false);
  const [searchValue, setSearchValue] = useState('');

  // Filter labels based on search
  const filteredLabels = labels.filter(label => 
    label.name.toLowerCase().includes(searchValue.toLowerCase()) ||
    (label.description && label.description.toLowerCase().includes(searchValue.toLowerCase())) ||
    (label.repository?.nameWithOwner && label.repository.nameWithOwner.toLowerCase().includes(searchValue.toLowerCase()))
  );

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between"
          disabled={disabled || isLoading}
        >
          {isLoading ? (
            <div className="flex items-center gap-2">
              <Loader2 className="h-4 w-4 animate-spin" />
              <span>Loading labels...</span>
            </div>
          ) : placeholder}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[400px] p-0">
        <Command onValueChange={setSearchValue}>
          <CommandInput placeholder="Search labels..." />
          <CommandEmpty>No labels found.</CommandEmpty>
          <CommandGroup className="max-h-[300px] overflow-auto">
            {filteredLabels.map((label) => (
              <CommandItem
                key={label.id}
                value={label.name}
                onSelect={() => {
                  onSelect(label.id);
                  setOpen(false);
                }}
                disabled={selectedLabelIds.includes(label.id)}
                className={cn(
                  selectedLabelIds.includes(label.id) ? 'bg-muted cursor-not-allowed' : '',
                  'flex items-center justify-between'
                )}
              >
                <div className="flex flex-col gap-1 py-1">
                  <div className="flex items-center gap-2">
                    <GithubLabel label={{ 
                      name: label.name, 
                      color: label.color, 
                      description: label.description 
                    }} />
                  </div>
                  {label.repository && (
                    <span className="text-xs text-muted-foreground">
                      {label.repository.nameWithOwner}
                    </span>
                  )}
                </div>
                {selectedLabelIds.includes(label.id) && (
                  <Check className="h-4 w-4 text-primary" />
                )}
              </CommandItem>
            ))}
          </CommandGroup>
        </Command>
      </PopoverContent>
    </Popover>
  );
}