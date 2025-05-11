import { useState } from 'react';
import { UserSelector } from './UserSelector';
import type { TeamMember } from './types';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog';

interface TeamAddMemberDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  allUsers: TeamMember[];
  teamId: number;
  teamName: string;
  teamMembers: TeamMember[];
  onAddMember: (teamId: number, userId: number) => void;
  isSearching?: boolean;
  onSearchUsers?: (query: string) => void;
}

export function TeamAddMemberDialog({
  open,
  onOpenChange,
  allUsers,
  teamId,
  teamName,
  teamMembers,
  onAddMember,
  isSearching = false,
  onSearchUsers
}: TeamAddMemberDialogProps) {
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
  
  // Filter out users who are already team members
  const availableUsers = allUsers.filter(user => 
    !teamMembers.some(member => member.id === user.id)
  );
  
  const teamMemberIds = teamMembers.map(member => member.id);
  
  const handleAddMember = () => {
    if (selectedUserId) {
      onAddMember(teamId, selectedUserId);
      setSelectedUserId(null);
      onOpenChange(false);
    }
  };

  const handleUserSelect = (userId: number) => {
    setSelectedUserId(userId);
  };

  const handleClose = () => {
    setSelectedUserId(null);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Add Team Member</DialogTitle>
          <DialogDescription>
            Add a new member to the team "{teamName}".
          </DialogDescription>
        </DialogHeader>
        
        <div className="py-4">
          <UserSelector
            users={availableUsers}
            selectedUserIds={teamMemberIds}
            onSelect={handleUserSelect}
            isLoading={isSearching}
            onSearch={onSearchUsers}
            canSearchExternal={!!onSearchUsers}
            placeholder="Search for a GitHub user..."
          />
        </div>
        
        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>
            Cancel
          </Button>
          <Button onClick={handleAddMember} disabled={!selectedUserId}>
            Add Member
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}