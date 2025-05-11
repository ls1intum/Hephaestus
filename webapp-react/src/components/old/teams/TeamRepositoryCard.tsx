import type { RepositoryInfo } from './types';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Trash2, Github } from 'lucide-react';

interface TeamRepositoryCardProps {
  repository: RepositoryInfo;
  onRemove?: (repositoryId: number) => void;
  isRemoving?: boolean;
}

export function TeamRepositoryCard({
  repository,
  onRemove,
  isRemoving = false
}: TeamRepositoryCardProps) {
  const handleRemove = () => {
    if (onRemove) {
      onRemove(repository.id);
    }
  };

  return (
    <Card className="w-full">
      <CardHeader className="p-4 pb-2 flex flex-row items-center justify-between">
        <div className="flex items-center gap-2">
          <Github className="h-5 w-5" />
          <CardTitle className="text-sm font-medium">{repository.nameWithOwner}</CardTitle>
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
            <span className="sr-only">Remove repository</span>
          </Button>
        )}
      </CardHeader>
      <CardContent className="p-4 pt-0">
        {repository.description && (
          <p className="text-xs text-muted-foreground line-clamp-2">{repository.description}</p>
        )}
        <div className="flex items-center gap-4 mt-2">
          {repository.isPrivate ? (
            <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-1 text-xs font-medium text-amber-700 ring-1 ring-inset ring-amber-600/20">
              Private
            </span>
          ) : (
            <span className="inline-flex items-center rounded-full bg-green-50 px-2 py-1 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20">
              Public
            </span>
          )}
          <a 
            href={`https://github.com/${repository.nameWithOwner}`} 
            target="_blank" 
            rel="noopener noreferrer"
            className="text-xs text-blue-600 hover:text-blue-800 hover:underline"
          >
            View on GitHub
          </a>
        </div>
      </CardContent>
    </Card>
  );
}