import dayjs from 'dayjs';
import { Circle as CircleIcon, CheckCircle as CheckCircleIcon } from 'lucide-react';
import { Card, CardFooter } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { GithubLabel } from '../github/GithubLabel';
import { cn } from '@/lib/utils';

type PrState = 'OPEN' | 'CLOSED';

export interface IssueCardProps {
  isLoading?: boolean;
  className?: string;
  title?: string;
  number?: number;
  additions?: number;
  deletions?: number;
  htmlUrl?: string;
  repositoryName?: string;
  createdAt?: string;
  state?: PrState;
  isDraft?: boolean;
  isMerged?: boolean;
  pullRequestLabels?: Array<{
    name: string;
    color: string;
    description?: string;
  }>;
}

export function IssueCard({
  isLoading = false,
  className = '',
  title = '',
  number,
  additions = 0,
  deletions = 0,
  htmlUrl = '#',
  repositoryName = '',
  createdAt = '',
  state = 'OPEN',
  // Remove unused parameters
  pullRequestLabels = []
}: IssueCardProps) {
  const displayCreated = createdAt ? dayjs(createdAt) : null;
  // Replace code blocks with styled text
  const displayTitle = title.replace(/`([^`]+)`/g, '<code class="textCode">$1</code>');

  const getIssueIconAndColor = () => {
    switch (state) {
      case 'OPEN':
        return { icon: <CircleIcon className="w-4 h-4 text-green-500" /> };
      case 'CLOSED':
        return { icon: <CheckCircleIcon className="w-4 h-4 text-purple-500" /> };
      default:
        return { icon: <CircleIcon className="w-4 h-4 text-muted-foreground" /> };
    }
  };
  
  const { icon } = getIssueIconAndColor();

  return (
    <a 
      href={htmlUrl} 
      target="_blank" 
      rel="noopener noreferrer"
    >
      <Card 
        className={cn(
          "flex flex-col gap-1 pt-6 w-72", 
          !isLoading && "hover:bg-accent/50 cursor-pointer", 
          className
        )}
      >
        <div className="flex flex-col gap-1 px-6">
          <div className="flex justify-between items-center text-sm text-github-muted-foreground">
            <span className="font-medium flex justify-center items-center space-x-1">
              {isLoading ? (
                <>
                  <Skeleton className="size-5 bg-green-500/30" />
                  <Skeleton className="h-4 w-16 lg:w-36" />
                </>
              ) : (
                <>
                  <span className="mr-1">{icon}</span>
                  <span>{repositoryName} #{number} on {displayCreated?.format('MMM D')}</span>
                </>
              )}
            </span>
            <span className="flex items-center space-x-2">
              {isLoading ? (
                <>
                  <Skeleton className="h-4 w-8 bg-green-500/30" />
                  <Skeleton className="h-4 w-8 bg-destructive/20" />
                </>
              ) : (
                <>
                  <span className="text-github-success-foreground font-bold">+{additions}</span>
                  <span className="text-github-danger-foreground font-bold">-{deletions}</span>
                </>
              )}
            </span>
          </div>

          <span className="flex justify-between font-medium contain-inline-size">
            {isLoading ? (
              <Skeleton className="h-6 w-3/4 mb-6" />
            ) : (
              <div dangerouslySetInnerHTML={{ __html: displayTitle }} />
            )}
          </span>
        </div>
        
        {!isLoading && pullRequestLabels.length > 0 && (
          <CardFooter className="flex flex-wrap gap-2 px-6 pb-6 space-x-0">
            {pullRequestLabels.map(label => (
              <GithubLabel key={label.name} label={label} />
            ))}
          </CardFooter>
        )}
      </Card>
    </a>
  );
}