import { useState } from 'react';
import { MoreHorizontal, Search, Eye, EyeOff, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { 
  Table,
  TableHeader,
  TableRow,
  TableHead,
  TableBody,
  TableCell
} from '@/components/ui/table';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { GithubLabel } from '../github/GithubLabel';
import type { TeamInfo } from './types';

interface TeamsTableProps {
  teams: TeamInfo[];
  isLoading?: boolean;
  onAddTeam?: () => void;
  onEditTeam?: (team: TeamInfo) => void;
  onDeleteTeam?: (teamId: number) => void;
  onToggleVisibility?: (teamId: number, hidden: boolean) => void;
  onAddLabel?: (teamId: number) => void;
  onAddRepository?: (teamId: number) => void;
}

export function TeamsTable({ 
  teams,
  isLoading = false,
  onAddTeam,
  onEditTeam,
  onDeleteTeam,
  onToggleVisibility,
  onAddLabel,
  onAddRepository
}: TeamsTableProps) {
  const [filter, setFilter] = useState('');

  // Filter teams based on search query
  const filteredTeams = teams.filter(team => {
    if (!filter) return true;
    const searchTerm = filter.toLowerCase();
    
    // Search in team name
    if (team.name.toLowerCase().includes(searchTerm)) return true;
    
    // Search in repositories
    if (team.repositories.some(repo => repo.nameWithOwner.toLowerCase().includes(searchTerm))) return true;
    
    // Search in labels
    if (team.labels.some(label => label.name.toLowerCase().includes(searchTerm))) return true;
    
    // Search in members
    if (team.members.some(member => 
      member.login.toLowerCase().includes(searchTerm) || 
      (member.name && member.name.toLowerCase().includes(searchTerm))
    )) return true;
    
    return false;
  });

  if (isLoading) {
    return <div>Loading teams...</div>;
  }

  return (
    <div className="w-full">
      <div className="flex items-center justify-between pb-4">
        <div className="flex items-center gap-2">
          <Search className="h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search teams..."
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="max-w-xs"
          />
        </div>
        
        <Button onClick={onAddTeam} className="gap-1">
          <Plus className="h-4 w-4" />
          New Team
        </Button>
      </div>
      
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Team</TableHead>
              <TableHead>Members</TableHead>
              <TableHead>Repositories</TableHead>
              <TableHead>Labels</TableHead>
              <TableHead className="w-20">Visible</TableHead>
              <TableHead className="w-20"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredTeams.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-24 text-center">
                  No teams found.
                </TableCell>
              </TableRow>
            ) : (
              filteredTeams.map((team) => (
                <TableRow key={team.id}>
                  <TableCell>
                    <div className="flex items-center">
                      <div className="h-4 w-4 rounded-full mr-2" style={{ backgroundColor: `#${team.color}` }} />
                      <span className="font-medium">{team.name}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex -space-x-2 overflow-hidden">
                      {team.members.slice(0, 3).map((member) => (
                        <Avatar key={member.id} className="border-2 border-background h-8 w-8">
                          <AvatarImage src={member.avatarUrl} alt={member.login} />
                          <AvatarFallback>{member.login.slice(0, 2).toUpperCase()}</AvatarFallback>
                        </Avatar>
                      ))}
                      {team.members.length > 3 && (
                        <div className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-background bg-muted text-xs font-medium">
                          +{team.members.length - 3}
                        </div>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      {team.repositories.slice(0, 2).map((repo) => (
                        <Badge key={repo.id} variant="outline" className="max-w-[150px] truncate">
                          {repo.nameWithOwner}
                        </Badge>
                      ))}
                      {team.repositories.length > 2 && (
                        <Badge variant="outline">+{team.repositories.length - 2}</Badge>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      {team.labels.slice(0, 3).map((label) => (
                        <GithubLabel 
                          key={label.id} 
                          label={{
                            name: label.name,
                            color: label.color,
                            description: label.description
                          }} 
                        />
                      ))}
                      {team.labels.length > 3 && (
                        <Badge variant="outline">+{team.labels.length - 3}</Badge>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Button 
                      variant="ghost" 
                      size="icon"
                      onClick={() => onToggleVisibility && onToggleVisibility(team.id, !team.hidden)}
                      title={team.hidden ? "Show team" : "Hide team"}
                    >
                      {team.hidden ? (
                        <EyeOff className="h-4 w-4" />
                      ) : (
                        <Eye className="h-4 w-4" />
                      )}
                    </Button>
                  </TableCell>
                  <TableCell>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" className="h-8 w-8 p-0">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => onEditTeam && onEditTeam(team)}>Edit Team</DropdownMenuItem>
                        
                        <DropdownMenuItem 
                          onClick={() => onAddRepository && onAddRepository(team.id)}
                        >
                          Add Repository
                        </DropdownMenuItem>
                        
                        <DropdownMenuItem
                          onClick={() => onAddLabel && onAddLabel(team.id)}
                        >
                          Add Label
                        </DropdownMenuItem>
                        
                        <DropdownMenuSeparator />
                        
                        <Dialog>
                          <DialogTrigger asChild>
                            <DropdownMenuItem
                              className="text-destructive focus:text-destructive"
                              onSelect={(e) => e.preventDefault()}
                            >
                              Delete Team
                            </DropdownMenuItem>
                          </DialogTrigger>
                          <DialogContent>
                            <DialogHeader>
                              <DialogTitle>Are you sure?</DialogTitle>
                              <DialogDescription>
                                This action cannot be undone. This will permanently delete the team "{team.name}".
                              </DialogDescription>
                            </DialogHeader>
                            <DialogFooter>
                              <Button 
                                variant="destructive" 
                                onClick={() => onDeleteTeam && onDeleteTeam(team.id)}
                              >
                                Delete
                              </Button>
                            </DialogFooter>
                          </DialogContent>
                        </Dialog>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}