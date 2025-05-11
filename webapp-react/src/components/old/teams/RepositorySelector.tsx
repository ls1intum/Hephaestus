import { useState } from 'react';
import { Check, ChevronsUpDown, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem } from '@/components/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { cn } from '@/lib/utils';
import type { RepositoryInfo } from './types';

interface RepositorySelectorProps {
  repositories: RepositoryInfo[];
  selectedRepositoryIds?: number[];
  onSelect: (repositoryId: number) => void;
  isLoading?: boolean;
  disabled?: boolean;
  placeholder?: string;
}

export function RepositorySelector({
  repositories,
  selectedRepositoryIds = [],
  onSelect,
  isLoading = false,
  disabled = false,
  placeholder = 'Select a repository'
}: RepositorySelectorProps) {
  const [open, setOpen] = useState(false);
  const [searchValue, setSearchValue] = useState('');

  // Filter repos based on search
  const filteredRepositories = repositories.filter(repo => 
    repo.nameWithOwner.toLowerCase().includes(searchValue.toLowerCase())
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
              <span>Loading repositories...</span>
            </div>
          ) : placeholder}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[400px] p-0">
        <Command onValueChange={setSearchValue}>
          <CommandInput placeholder="Search repositories..." />
          <CommandEmpty>No repositories found.</CommandEmpty>
          <CommandGroup className="max-h-[300px] overflow-auto">
            {filteredRepositories.map((repo) => (
              <CommandItem
                key={repo.id}
                value={repo.nameWithOwner}
                onSelect={() => {
                  onSelect(repo.id);
                  setOpen(false);
                }}
                disabled={selectedRepositoryIds.includes(repo.id)}
                className={cn(
                  selectedRepositoryIds.includes(repo.id) ? 'bg-muted cursor-not-allowed' : '',
                  'flex items-center justify-between'
                )}
              >
                <span>{repo.nameWithOwner}</span>
                {selectedRepositoryIds.includes(repo.id) && (
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