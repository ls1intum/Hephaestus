import { useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Search, GitFork, Filter, GitPullRequest, Star } from 'lucide-react';

export interface Repository {
  id: number;
  name: string;
  nameWithOwner: string;
  description?: string;
  isPrivate?: boolean;
  stargazerCount?: number;
  forkCount?: number;
  openIssuesCount?: number;
  openPullRequestsCount?: number;
  url?: string;
}

export interface WorkspaceRepositoriesProps {
  isAdmin?: boolean;
  initialRepositories?: Repository[];
}

export function WorkspaceRepositories({ 
  isAdmin = false,
  initialRepositories = [] 
}: WorkspaceRepositoriesProps) {
  // We don't need to modify repositories in this component, so we don't need setRepositories
  const repositories = initialRepositories;
  const [searchQuery, setSearchQuery] = useState('');
  
  const filteredRepositories = repositories.filter(repo =>
    repo.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    repo.nameWithOwner.toLowerCase().includes(searchQuery.toLowerCase()) ||
    (repo.description && repo.description.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  return (
    <Card>
      <CardContent className="p-6">
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                type="search"
                placeholder="Search repositories..."
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
                  <GitFork className="h-4 w-4 mr-2" />
                  Add Repository
                </Button>
              )}
            </div>
          </div>

          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[40%]">Repository</TableHead>
                  <TableHead>Visibility</TableHead>
                  <TableHead>Stats</TableHead>
                  {isAdmin && <TableHead>Actions</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredRepositories.length > 0 ? (
                  filteredRepositories.map((repo) => (
                    <TableRow key={repo.id}>
                      <TableCell className="font-medium">
                        <div className="flex flex-col">
                          <a 
                            href={repo.url || `https://github.com/${repo.nameWithOwner}`} 
                            target="_blank"
                            rel="noopener noreferrer"
                            className="font-medium hover:underline"
                          >
                            {repo.nameWithOwner}
                          </a>
                          {repo.description && (
                            <span className="text-sm text-muted-foreground truncate max-w-xs">
                              {repo.description}
                            </span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        {repo.isPrivate ? (
                          <Badge variant="secondary">Private</Badge>
                        ) : (
                          <Badge variant="default">Public</Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-3">
                          <div className="flex items-center gap-1" title="Stars">
                            <Star className="h-4 w-4 text-amber-500" />
                            <span>{repo.stargazerCount || 0}</span>
                          </div>
                          <div className="flex items-center gap-1" title="Forks">
                            <GitFork className="h-4 w-4 text-blue-500" />
                            <span>{repo.forkCount || 0}</span>
                          </div>
                          <div className="flex items-center gap-1" title="Pull Requests">
                            <GitPullRequest className="h-4 w-4 text-green-500" />
                            <span>{repo.openPullRequestsCount || 0}</span>
                          </div>
                        </div>
                      </TableCell>
                      {isAdmin && (
                        <TableCell>
                          <Button variant="ghost" size="sm">
                            Configure
                          </Button>
                        </TableCell>
                      )}
                    </TableRow>
                  ))
                ) : (
                  <TableRow>
                    <TableCell colSpan={isAdmin ? 4 : 3} className="text-center py-6 text-muted-foreground">
                      No repositories found. {searchQuery && 'Try a different search query.'}
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