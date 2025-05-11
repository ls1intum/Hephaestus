import { TeamForm } from './TeamForm';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import type { TeamInfo } from './types';

interface TeamCreateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreate: (team: Omit<TeamInfo, 'id' | 'createdAt' | 'updatedAt' | 'memberCount' | 'repositoryCount' | 'labelCount'>) => void;
  isCreating?: boolean;
}

export function TeamCreateDialog({
  open,
  onOpenChange,
  onCreate,
  isCreating = false
}: TeamCreateDialogProps) {
  // Create empty team for the form
  const emptyTeam: Partial<TeamInfo> = {
    name: '',
    color: '3b82f6', // Default blue
    hidden: true,
    repositories: [],
    labels: [],
    members: []
  };

  const handleSubmit = (team: Partial<TeamInfo>) => {
    onCreate({
      name: team.name || '',
      color: team.color || '3b82f6',
      hidden: team.hidden || false,
      repositories: [],
      labels: [],
      members: []
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Create New Team</DialogTitle>
          <DialogDescription>
            Create a new team to organize your GitHub repositories, labels and members.
          </DialogDescription>
        </DialogHeader>
        
        <TeamForm 
          team={emptyTeam}
          onSubmit={handleSubmit}
          isSubmitting={isCreating}
          onCancel={() => onOpenChange(false)}
        />
      </DialogContent>
    </Dialog>
  );
}