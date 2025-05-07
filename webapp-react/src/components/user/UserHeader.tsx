import { ClockIcon } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Skeleton } from '@/components/ui/skeleton';
import { LeagueIcon } from '@/components/league/LeagueIcon';
import type { UserInfo, RepositoryInfo } from './utils';
import { formatFirstContribution, getRepositoryImage } from './utils';

interface UserHeaderProps {
  isLoading?: boolean;
  user?: UserInfo;
  firstContribution?: string;
  contributedRepositories?: RepositoryInfo[];
  leaguePoints?: number;
}

export function UserHeader({
  isLoading = false,
  user,
  firstContribution,
  contributedRepositories = [],
  leaguePoints = 0
}: UserHeaderProps) {
  const displayFirstContribution = formatFirstContribution(firstContribution);

  return (
    <div className="flex items-center justify-between mx-8">
      <div className="flex gap-8 items-center">
        {isLoading ? (
          <div className="h-20 w-20 rounded-full ring-2 ring-neutral-100 dark:ring-neutral-800 overflow-hidden">
            <Skeleton className="h-full w-full rounded-full" />
          </div>
        ) : (
          <Avatar className="h-20 w-20 ring-2 ring-neutral-100 dark:ring-neutral-800">
            <AvatarImage src={user?.avatarUrl} alt={`${user?.login}'s avatar`} />
            <AvatarFallback>{user?.login?.slice(0, 2)?.toUpperCase()}</AvatarFallback>
          </Avatar>
        )}

        {isLoading ? (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-8 w-48" />
            <Skeleton className="h-5 w-64" />
            <Skeleton className="h-5 w-80" />
            <div className="flex items-center gap-2">
              <Skeleton className="size-10" />
              <Skeleton className="size-10" />
            </div>
          </div>
        ) : user ? (
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl md:text-3xl font-bold leading-6">{user.name}</h1>
            <a 
              className="md:text-lg font-medium text-muted-foreground mb-1 hover:text-github-accent-foreground"
              href={user.htmlUrl}
              target="_blank" 
              rel="noopener noreferrer"
            >
              github.com/{user.login}
            </a>
            
            {displayFirstContribution && (
              <div className="flex items-center gap-1 md:gap-2 text-muted-foreground font-medium text-sm md:text-base">
                <ClockIcon size={16} className="overflow-visible" />
                Contributing since {displayFirstContribution}
              </div>
            )}
            
            {contributedRepositories.length > 0 && (
              <div className="flex items-center gap-2">
                {contributedRepositories.map((repository) => (
                  <TooltipProvider key={repository.nameWithOwner}>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button 
                          variant="outline" 
                          className="size-10 p-1"
                          aria-label={repository.nameWithOwner}
                          asChild
                        >
                          <a href={repository.htmlUrl} target="_blank" rel="noopener noreferrer">
                            <img 
                              src={getRepositoryImage(repository.nameWithOwner)} 
                              alt={repository.nameWithOwner} 
                              className="w-full h-full object-contain"
                            />
                          </a>
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>
                        {repository.nameWithOwner}
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                ))}
              </div>
            )}
          </div>
        ) : null}
      </div>
      
      <div className="flex flex-col justify-center items-center gap-2">
        <LeagueIcon leaguePoints={leaguePoints} size="max" />
        <span className="text-muted-foreground text-xl md:text-2xl font-bold leading-6">{leaguePoints}</span>
      </div>
    </div>
  );
}