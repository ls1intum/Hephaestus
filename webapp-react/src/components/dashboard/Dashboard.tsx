import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ActivityFeed } from './ActivityFeed';
import { PullRequestStats } from './PullRequestStats';
import { RepositoryStats } from './RepositoryStats';
import { BadPractices } from './BadPractices';
import type { ActivityItem } from './ActivityFeed';
import type { BadPractice } from './BadPractices';

export interface DashboardProps {
  username: string;
  repositories?: number;
  pullRequests?: number;
  commits?: number;
  activeDays?: number;
  streak?: number;
  activities?: ActivityItem[];
  badPractices?: BadPractice[];
}

export function Dashboard({
  username,
  repositories = 0,
  pullRequests = 0,
  commits = 0,
  activeDays = 0,
  streak = 0,
  activities = [],
  badPractices = []
}: DashboardProps) {
  return (
    <div className="container mx-auto py-6">
      <div className="flex flex-col space-y-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Welcome back, {username}!</h1>
          <p className="text-muted-foreground">
            Here's an overview of your GitHub activity and analytics.
          </p>
        </div>
        
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle>Repositories</CardTitle>
              <CardDescription>Total repositories contributed to</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{repositories}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle>Pull Requests</CardTitle>
              <CardDescription>Total PRs opened this month</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{pullRequests}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle>Commits</CardTitle>
              <CardDescription>Total commits this month</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{commits}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle>Active Days</CardTitle>
              <CardDescription>Current streak: {streak} days</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{activeDays}</div>
            </CardContent>
          </Card>
        </div>
        
        <div className="grid gap-4 md:grid-cols-2">
          <ActivityFeed activities={activities} />
          <PullRequestStats />
        </div>
        
        <div className="grid gap-4 md:grid-cols-2">
          <RepositoryStats />
          <BadPractices badPractices={badPractices} />
        </div>
      </div>
    </div>
  );
}