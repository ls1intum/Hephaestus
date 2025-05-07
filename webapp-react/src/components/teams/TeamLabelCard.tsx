import type { LabelInfo } from './types';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Trash2, Tag } from 'lucide-react';

interface TeamLabelCardProps {
  label: LabelInfo;
  onRemove?: (labelId: number) => void;
  isRemoving?: boolean;
}

export function TeamLabelCard({
  label,
  onRemove,
  isRemoving = false
}: TeamLabelCardProps) {
  const handleRemove = () => {
    if (onRemove) {
      onRemove(label.id);
    }
  };

  // Convert hex color to a lighter background color for the badge
  const getLightColor = (hexColor: string) => {
    // If the color is already in hex format, convert it to a light background
    if (hexColor.startsWith('#')) {
      return `${hexColor}20`; // Add 20 for 12.5% opacity
    }
    // Default color
    return '#e2e8f0';
  };

  const textColor = label.color?.startsWith('#') ? label.color : '#1e293b';
  const bgColor = getLightColor(label.color || '#e2e8f0');

  return (
    <Card className="w-full">
      <CardHeader className="p-4 pb-2 flex flex-row items-center justify-between">
        <div className="flex items-center gap-2">
          <Tag className="h-5 w-5" style={{ color: textColor }} />
          <CardTitle className="text-sm font-medium">{label.name}</CardTitle>
        </div>
        
        {onRemove && (
          <Button 
            variant="ghost" 
            size="icon" 
            className="h-8 w-8 text-destructive hover:text-destructive hover:bg-destructive/10" 
            onClick={handleRemove}
            disabled={isRemoving}
          >
            <Trash2 className="h-4 w-4" />
            <span className="sr-only">Remove label</span>
          </Button>
        )}
      </CardHeader>
      <CardContent className="p-4 pt-0">
        {label.description && (
          <p className="text-xs text-muted-foreground line-clamp-2 mb-2">{label.description}</p>
        )}
        <div className="flex items-center gap-2">
          <div 
            className="w-4 h-4 rounded-full" 
            style={{ backgroundColor: label.color || '#e2e8f0' }}
          ></div>
          <span 
            className="text-xs rounded-full px-2 py-0.5"
            style={{ 
              backgroundColor: bgColor,
              color: textColor,
              border: `1px solid ${textColor}30`
            }}
          >
            {label.color || 'No color'}
          </span>
        </div>
      </CardContent>
    </Card>
  );
}