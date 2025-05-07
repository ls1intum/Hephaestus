import React, { useState, useEffect } from 'react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import type { TeamInfo } from './types';

interface TeamFormProps {
  team?: Partial<TeamInfo>;
  onSubmit: (team: Partial<TeamInfo>) => void;
  onCancel: () => void;
  isSubmitting?: boolean;
}

export function TeamForm({ 
  team, 
  onSubmit, 
  onCancel,
  isSubmitting = false
}: TeamFormProps) {
  const [formData, setFormData] = useState<Partial<TeamInfo>>({
    name: '',
    color: '3b82f6', // Default blue color
    hidden: false,
    ...team
  });

  // Update form data when team prop changes
  useEffect(() => {
    if (team) {
      setFormData({
        name: '',
        color: '3b82f6',
        hidden: false,
        ...team
      });
    }
  }, [team]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    setFormData({
      ...formData,
      [name]: type === 'checkbox' ? checked : value,
    });
  };

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    onSubmit(formData);
  };

  const isValid = formData.name && formData.name.trim() !== '' && formData.color && formData.color.trim() !== '';

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <div className="space-y-4">
        <div className="grid gap-2">
          <Label htmlFor="name">Team Name</Label>
          <Input
            id="name"
            name="name"
            placeholder="Enter team name"
            value={formData.name || ''}
            onChange={handleChange}
            required
          />
        </div>

        <div className="grid gap-2">
          <Label htmlFor="color">Color (hex)</Label>
          <div className="flex gap-2 items-center">
            <div 
              className="h-8 w-8 rounded-md"
              style={{ backgroundColor: `#${formData.color || '3b82f6'}` }}
            />
            <Input
              id="color"
              name="color"
              placeholder="Enter hex color (without #)"
              value={formData.color || ''}
              onChange={handleChange}
              pattern="^[0-9A-Fa-f]{6}$"
              required
              className="flex-1"
            />
          </div>
          <p className="text-sm text-muted-foreground">
            Enter a 6-character hex code without the # symbol (e.g., 3b82f6 for blue)
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <input
            type="checkbox"
            id="hidden"
            name="hidden"
            checked={formData.hidden || false}
            onChange={handleChange}
            className="h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
          />
          <Label htmlFor="hidden">Hide team</Label>
        </div>
      </div>

      <div className="flex justify-end gap-3">
        <Button 
          type="button" 
          variant="outline" 
          onClick={onCancel}
          disabled={isSubmitting}
        >
          Cancel
        </Button>
        <Button 
          type="submit" 
          disabled={!isValid || isSubmitting}
        >
          {isSubmitting ? 'Saving...' : team?.id ? 'Update Team' : 'Create Team'}
        </Button>
      </div>
    </form>
  );
}