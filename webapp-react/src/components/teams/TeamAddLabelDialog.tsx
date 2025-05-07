import { useState } from 'react';
import { Button } from '@/components/ui/button';
import type { LabelInfo } from './types';
import { LabelSelector } from './LabelSelector';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog';

interface TeamAddLabelDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  labels: LabelInfo[];
  teamId: number;
  teamName: string;
  teamLabels: LabelInfo[];
  onAddLabel: (teamId: number, labelId: number) => void;
  isLoading?: boolean;
}

export function TeamAddLabelDialog({
  open,
  onOpenChange,
  labels,
  teamId,
  teamName,
  teamLabels,
  onAddLabel,
  isLoading = false
}: TeamAddLabelDialogProps) {
  const [selectedLabelId, setSelectedLabelId] = useState<number | null>(null);
  
  // Filter out labels that are already added to the team
  const availableLabels = labels.filter(label => 
    !teamLabels.some(teamLabel => teamLabel.id === label.id)
  );
  
  const teamLabelIds = teamLabels.map(label => label.id);
  
  const handleAddLabel = () => {
    if (selectedLabelId) {
      onAddLabel(teamId, selectedLabelId);
      setSelectedLabelId(null);
      onOpenChange(false);
    }
  };

  const handleLabelSelect = (labelId: number) => {
    setSelectedLabelId(labelId);
  };

  const handleClose = () => {
    setSelectedLabelId(null);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Add Label</DialogTitle>
          <DialogDescription>
            Add a label to the team "{teamName}".
          </DialogDescription>
        </DialogHeader>
        
        <div className="py-4">
          <LabelSelector
            labels={availableLabels}
            selectedLabelIds={teamLabelIds}
            onSelect={handleLabelSelect}
            isLoading={isLoading}
            placeholder="Select a label..."
          />
        </div>
        
        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>
            Cancel
          </Button>
          <Button onClick={handleAddLabel} disabled={!selectedLabelId}>
            Add Label
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}