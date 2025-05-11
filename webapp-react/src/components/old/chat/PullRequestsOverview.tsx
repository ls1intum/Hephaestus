import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { CalendarIcon, GitPullRequestIcon } from 'lucide-react';
import { format } from 'date-fns';
import type { PullRequest } from './types';

interface PullRequestsOverviewProps {
  pullRequests: PullRequest[];
}

export function PullRequestsOverview({ pullRequests }: PullRequestsOverviewProps) {
  if (!pullRequests.length) return null;
  
  return (
    <Card className="w-full max-w-[600px]">
      <CardHeader>
        <CardTitle className="text-lg">Pull Requests</CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <ul className="divide-y">
          {pullRequests.map((pr, index) => (
            <li key={index} className="p-4 hover:bg-muted/50">
              <a 
                href={pr.url} 
                target="_blank" 
                rel="noopener noreferrer"
                className="block"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <GitPullRequestIcon className="h-4 w-4 text-primary" />
                    <span className="text-sm text-muted-foreground">
                      {pr.repo} #{pr.number}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <CalendarIcon className="h-3 w-3 text-muted-foreground" />
                    <span className="text-xs text-muted-foreground">
                      {format(new Date(pr.created_at), 'MMM d, yyyy')}
                    </span>
                  </div>
                </div>
                <h3 className="mt-1 text-sm font-medium">{pr.title}</h3>
                <div className="mt-1">
                  <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                    pr.status === 'OPEN' ? 'bg-green-100 text-green-800' : 
                    pr.status === 'CLOSED' ? 'bg-red-100 text-red-800' : 
                    'bg-blue-100 text-blue-800'
                  }`}>
                    {pr.status.toLowerCase()}
                  </span>
                </div>
              </a>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}