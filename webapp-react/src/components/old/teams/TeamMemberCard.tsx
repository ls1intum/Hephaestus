import type { TeamMember } from './types';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { Trash2 } from 'lucide-react';

interface TeamMemberCardProps {
  member: TeamMember;
  onRemove?: (memberId: number) => void;
  isRemoving?: boolean;
}

export function TeamMemberCard({ 
  member, 
  onRemove,
  isRemoving = false
}: TeamMemberCardProps) {
  const handleRemove = () => {
    if (onRemove) {
      onRemove(member.id);
    }
  };

  // Create initials from name or login
  const getInitials = () => {
    if (member.name) {
      const nameParts = member.name.split(' ');
      if (nameParts.length >= 2) {
        return `${nameParts[0][0]}${nameParts[1][0]}`.toUpperCase();
      }
      return member.name[0].toUpperCase();
    }
    return member.login[0].toUpperCase();
  };

  return (
    <Card className="w-full">
      <CardHeader className="p-4 pb-2 flex flex-row items-center justify-between">
        <div className="flex items-center gap-2">
          <Avatar>
            <AvatarImage src={member.avatarUrl} alt={member.name || member.login} />
            <AvatarFallback>{getInitials()}</AvatarFallback>
          </Avatar>
          <CardTitle className="text-sm font-medium">{member.name || member.login}</CardTitle>
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
            <span className="sr-only">Remove member</span>
          </Button>
        )}
      </CardHeader>
      <CardContent className="p-4 pt-0">
        <p className="text-xs text-muted-foreground">@{member.login}</p>
      </CardContent>
    </Card>
  );
}