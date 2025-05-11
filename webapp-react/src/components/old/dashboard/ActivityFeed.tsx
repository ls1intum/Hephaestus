import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { 
  GitCommit, 
  GitPullRequest, 
  GitMerge, 
  MessageSquare, 
  Star
} from 'lucide-react';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { formatDistanceToNow } from 'date-fns';

export interface ActivityItem {
  id: string;
  type: 'commit' | 'pull_request' | 'merge' | 'comment' | 'star';
  title: string;
  repository: string;
  timestamp: Date;
  user?: {
    login: string;
    avatarUrl?: string;
  };
}

export interface ActivityFeedProps {
  activities?: ActivityItem[];
}

export function ActivityFeed({ activities = [] }: ActivityFeedProps) {
  const getActivityIcon = (type: ActivityItem['type']) => {
    switch (type) {
      case 'commit':
        return <GitCommit className="h-5 w-5" />;
      case 'pull_request':
        return <GitPullRequest className="h-5 w-5" />;
      case 'merge':
        return <GitMerge className="h-5 w-5" />;
      case 'comment':
        return <MessageSquare className="h-5 w-5" />;
      case 'star':
        return <Star className="h-5 w-5" />;
      default:
        return <GitCommit className="h-5 w-5" />;
    }
  };

  return (
    <Card className="h-[500px] overflow-hidden">
      <CardHeader>
        <CardTitle>Recent Activity</CardTitle>
        <CardDescription>Your GitHub activity over the last 30 days</CardDescription>
      </CardHeader>
      <CardContent className="p-0 overflow-auto h-[400px]">
        {activities.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-muted-foreground p-6">
            <p>No recent activity to display.</p>
            <p className="text-sm">Start contributing to see your activity here.</p>
          </div>
        ) : (
          <div className="space-y-0">
            {activities.map((activity) => (
              <div key={activity.id} className="flex items-start p-4 border-b last:border-0">
                <div className="mr-4 mt-0.5 bg-muted p-2 rounded-full">
                  {getActivityIcon(activity.type)}
                </div>
                <div className="space-y-1">
                  <p className="text-sm font-medium leading-none">
                    {activity.title}
                  </p>
                  <div className="flex items-center pt-1">
                    <Badge variant="secondary" className="mr-2">
                      {activity.repository}
                    </Badge>
                    <span className="text-xs text-muted-foreground">
                      {formatDistanceToNow(activity.timestamp, { addSuffix: true })}
                    </span>
                  </div>
                </div>
                {activity.user && (
                  <Avatar className="h-6 w-6 ml-auto">
                    <AvatarImage src={activity.user.avatarUrl} />
                    <AvatarFallback>{activity.user.login.charAt(0).toUpperCase()}</AvatarFallback>
                  </Avatar>
                )}
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}