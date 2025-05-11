import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Card, CardContent } from '@/components/ui/card';
import { Search, Users, Filter, Eye, EyeOff } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';

export interface TeamMember {
  id: number;
  login: string;
  name?: string;
  avatarUrl?: string;
}

export interface Team {
  id: number;
  name: string;
  color: string;
  membersCount: number;
  repositoriesCount: number;
  labelsCount: number;
  hidden?: boolean;
  members: TeamMember[];
}

export interface WorkspaceTeamsProps {
  isAdmin?: boolean;
  initialTeams?: Team[];
}

export function WorkspaceTeams({ 
  isAdmin = false,
  initialTeams = [] 
}: WorkspaceTeamsProps) {
  const [teams, setTeams] = useState<Team[]>(initialTeams);
  const [searchQuery, setSearchQuery] = useState('');
  
  const filteredTeams = teams.filter(team =>
    team.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const toggleTeamVisibility = (teamId: number) => {
    if (!isAdmin) return;
    
    setTeams(prevTeams =>
      prevTeams.map(team => 
        team.id === teamId ? { ...team, hidden: !team.hidden } : team
      )
    );
  };

  return (
    <Card>
      <CardContent className="p-6">
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                type="search"
                placeholder="Search teams..."
                className="pl-8"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <div className="flex items-center gap-2">
              <Button variant="outline" size="icon">
                <Filter className="h-4 w-4" />
              </Button>
              {isAdmin && (
                <Button>
                  <Users className="h-4 w-4 mr-2" />
                  Add Team
                </Button>
              )}
            </div>
          </div>

          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Team</TableHead>
                  <TableHead>Members</TableHead>
                  <TableHead>Repositories</TableHead>
                  <TableHead>Labels</TableHead>
                  {isAdmin && <TableHead>Visibility</TableHead>}
                  {isAdmin && <TableHead>Actions</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredTeams.length > 0 ? (
                  filteredTeams.map((team) => (
                    <TableRow key={team.id}>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <div 
                            className="w-4 h-4 rounded-full"
                            style={{ backgroundColor: `#${team.color}` }}
                          />
                          <span className="font-medium">{team.name}</span>
                        </div>
                      </TableCell>
                      <TableCell>
                        {team.members.length > 0 ? (
                          <div className="flex -space-x-2 overflow-hidden">
                            {team.members.slice(0, 3).map((member) => (
                              <Avatar key={member.id} className="border-2 border-background h-8 w-8">
                                <AvatarImage src={member.avatarUrl} alt={member.login} />
                                <AvatarFallback>
                                  {member.name ? member.name.charAt(0) : member.login.charAt(0)}
                                </AvatarFallback>
                              </Avatar>
                            ))}
                            {team.members.length > 3 && (
                              <div className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-background bg-muted text-xs font-medium">
                                +{team.members.length - 3}
                              </div>
                            )}
                          </div>
                        ) : (
                          <span className="text-muted-foreground text-sm">No members</span>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">{team.repositoriesCount}</Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">{team.labelsCount}</Badge>
                      </TableCell>
                      {isAdmin && (
                        <TableCell>
                          <Button 
                            variant="ghost" 
                            size="sm" 
                            onClick={() => toggleTeamVisibility(team.id)}
                            className="p-0 h-8 w-8"
                          >
                            {team.hidden ? (
                              <EyeOff className="h-4 w-4" />
                            ) : (
                              <Eye className="h-4 w-4" />
                            )}
                          </Button>
                        </TableCell>
                      )}
                      {isAdmin && (
                        <TableCell>
                          <Button variant="ghost" size="sm">
                            Edit
                          </Button>
                        </TableCell>
                      )}
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell 
                      colSpan={isAdmin ? 6 : 4} 
                      className="text-center py-6 text-muted-foreground"
                    >
                      No teams found. {searchQuery && 'Try a different search query.'}
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}