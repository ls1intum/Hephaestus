import { useState } from 'react';
import { RepositorySelector } from './RepositorySelector';
import type { RepositoryInfo } from './types';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog';

interface TeamAddRepositoryDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  repositories: RepositoryInfo[];
  teamId: number;
  teamName: string;
  teamRepositories: RepositoryInfo[];
  onAddRepository: (teamId: number, repositoryId: number) => void;
  isLoading?: boolean;
}

export function TeamAddRepositoryDialog({
  open,
  onOpenChange,
  repositories,
  teamId,
  teamName,
  teamRepositories,
  onAddRepository,
  isLoading = false
}: TeamAddRepositoryDialogProps) {
  const [selectedRepositoryId, setSelectedRepositoryId] = useState<number | null>(null);
  
  // Filter out repositories that are already added to the team
  const availableRepositories = repositories.filter(repo => 
    !teamRepositories.some(teamRepo => teamRepo.id === repo.id)
  );
  
  const teamRepositoryIds = teamRepositories.map(repo => repo.id);
  
  const handleAddRepository = () => {
    if (selectedRepositoryId) {
      onAddRepository(teamId, selectedRepositoryId);
      setSelectedRepositoryId(null);
      onOpenChange(false);
    }
  };

  const handleRepositorySelect = (repoId: number) => {
    setSelectedRepositoryId(repoId);
  };

  const handleClose = () => {
    setSelectedRepositoryId(null);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Add Repository</DialogTitle>
          <DialogDescription>
            Add a repository to the team "{teamName}".
          </DialogDescription>
        </DialogHeader>
        
        <div className="py-4">
          <RepositorySelector
            repositories={availableRepositories}
            selectedRepositoryIds={teamRepositoryIds}
            onSelect={handleRepositorySelect}
            isLoading={isLoading}
            placeholder="Select a repository..."
          />
        </div>
        
        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>
            Cancel
          </Button>
          <Button onClick={handleAddRepository} disabled={!selectedRepositoryId}>
            Add Repository
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}