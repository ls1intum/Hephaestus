import { TeamForm } from './TeamForm';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import type { TeamInfo } from './types';

interface TeamEditDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  team: TeamInfo;
  onSave: (team: TeamInfo) => void;
  isSaving?: boolean;
}

export function TeamEditDialog({
  open,
  onOpenChange,
  team,
  onSave,
  isSaving = false
}: TeamEditDialogProps) {
  // Convert the onSave handler to match TeamForm's partial TeamInfo type
  const handleSubmit = (editedTeam: Partial<TeamInfo>) => {
    // Merge the edited team with the original team data to ensure all required properties are present
    onSave({
      ...team,
      name: editedTeam.name || team.name,
      color: editedTeam.color || team.color,
      hidden: editedTeam.hidden !== undefined ? editedTeam.hidden : team.hidden
    });
  };

  const handleCancel = () => {
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Edit Team</DialogTitle>
          <DialogDescription>
            Make changes to the team "{team.name}".
          </DialogDescription>
        </DialogHeader>
        
        <TeamForm 
          team={team}
          onSubmit={handleSubmit}
          isSubmitting={isSaving}
          onCancel={handleCancel}
        />
      </DialogContent>
    </Dialog>
  );
}