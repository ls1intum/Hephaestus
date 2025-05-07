import { useState } from 'react';
import { Check, ChevronsUpDown, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem } from '@/components/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { cn } from '@/lib/utils';
import type { TeamMember } from './types';

interface UserSelectorProps {
  users: TeamMember[];
  selectedUserIds?: number[];
  onSelect: (userId: number) => void;
  isLoading?: boolean;
  disabled?: boolean;
  placeholder?: string;
  onSearch?: (query: string) => void;
  canSearchExternal?: boolean;
}

export function UserSelector({
  users,
  selectedUserIds = [],
  onSelect,
  isLoading = false,
  disabled = false,
  placeholder = 'Select a user',
  onSearch,
  canSearchExternal = false
}: UserSelectorProps) {
  const [open, setOpen] = useState(false);
  const [searchValue, setSearchValue] = useState('');

  // Filter users based on search
  const filteredUsers = users.filter(user => 
    user.login.toLowerCase().includes(searchValue.toLowerCase()) ||
    (user.name && user.name.toLowerCase().includes(searchValue.toLowerCase()))
  );

  const handleSearchChange = (value: string) => {
    setSearchValue(value);
    if (onSearch && value.length >= 3) {
      onSearch(value);
    }
  };

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
              <span>Loading users...</span>
            </div>
          ) : placeholder}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[400px] p-0">
        <Command onValueChange={handleSearchChange}>
          <CommandInput placeholder="Search users..." />
          <CommandEmpty>
            {canSearchExternal && searchValue.length >= 3 ? (
              isLoading ? (
                <div className="flex flex-col items-center justify-center p-4 gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <p className="text-sm text-muted-foreground">Searching GitHub...</p>
                </div>
              ) : (
                <p className="p-2 text-sm text-muted-foreground">No users found.</p>
              )
            ) : searchValue.length < 3 ? (
              <p className="p-2 text-sm text-muted-foreground">Enter at least 3 characters to search.</p>
            ) : (
              <p className="p-2 text-sm text-muted-foreground">No users found.</p>
            )}
          </CommandEmpty>
          <CommandGroup className="max-h-[300px] overflow-auto">
            {filteredUsers.map((user) => (
              <CommandItem
                key={user.id}
                value={user.login}
                onSelect={() => {
                  onSelect(user.id);
                  setOpen(false);
                }}
                disabled={selectedUserIds.includes(user.id)}
                className={cn(
                  selectedUserIds.includes(user.id) ? 'bg-muted cursor-not-allowed' : '',
                  'flex items-center justify-between'
                )}
              >
                <div className="flex items-center gap-2">
                  <Avatar className="h-6 w-6">
                    <AvatarImage src={user.avatarUrl} alt={user.login} />
                    <AvatarFallback>{user.login.slice(0, 2).toUpperCase()}</AvatarFallback>
                  </Avatar>
                  <div className="flex flex-col">
                    <span className="font-medium">{user.login}</span>
                    {user.name && <span className="text-xs text-muted-foreground">{user.name}</span>}
                  </div>
                </div>
                {selectedUserIds.includes(user.id) && (
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