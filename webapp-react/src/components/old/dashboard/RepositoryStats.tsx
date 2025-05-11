import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

export interface RepoContribution {
  name: string;
  nameWithOwner: string;
  commits: number;
  pullRequests: number;
  issues: number;
}

export interface RepositoryStatsProps {
  repositories?: RepoContribution[];
}

export function RepositoryStats({ repositories = [] }: RepositoryStatsProps) {
  // Sort repositories by total activity (commits + PRs + issues)
  const sortedRepos = [...repositories].sort((a, b) => {
    const aActivity = a.commits + a.pullRequests + a.issues;
    const bActivity = b.commits + b.pullRequests + b.issues;
    return bActivity - aActivity;
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Repository Activity</CardTitle>
        <CardDescription>Your most active repositories</CardDescription>
      </CardHeader>
      <CardContent>
        {repositories.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-[300px] text-muted-foreground">
            <p>No repository data available.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {sortedRepos.slice(0, 5).map((repo) => {
              const totalActivity = repo.commits + repo.pullRequests + repo.issues;
              
              return (
                <div key={repo.nameWithOwner} className="space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="flex flex-col">
                      <span className="font-medium">{repo.nameWithOwner}</span>
                      <div className="flex gap-2 mt-1">
                        <Badge variant="outline" className="text-xs">
                          {repo.commits} commits
                        </Badge>
                        <Badge variant="outline" className="text-xs">
                          {repo.pullRequests} PRs
                        </Badge>
                        <Badge variant="outline" className="text-xs">
                          {repo.issues} issues
                        </Badge>
                      </div>
                    </div>
                    <span className="text-2xl font-bold">{totalActivity}</span>
                  </div>
                  <div className="h-2 w-full rounded-full bg-muted overflow-hidden">
                    <div className="flex h-full">
                      <div 
                        className="h-full bg-blue-500" 
                        style={{ width: `${(repo.commits / totalActivity) * 100}%` }}
                        title={`${repo.commits} commits`}
                      />
                      <div 
                        className="h-full bg-purple-500" 
                        style={{ width: `${(repo.pullRequests / totalActivity) * 100}%` }}
                        title={`${repo.pullRequests} pull requests`}
                      />
                      <div 
                        className="h-full bg-yellow-500" 
                        style={{ width: `${(repo.issues / totalActivity) * 100}%` }}
                        title={`${repo.issues} issues`}
                      />
                    </div>
                  </div>
                </div>
              );
            })}
            
            <div className="mt-4 pt-4 border-t flex items-center justify-between text-sm text-muted-foreground">
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-blue-500" /> Commits
                <span className="w-3 h-3 rounded-full bg-purple-500 ml-2" /> Pull Requests
                <span className="w-3 h-3 rounded-full bg-yellow-500 ml-2" /> Issues
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}