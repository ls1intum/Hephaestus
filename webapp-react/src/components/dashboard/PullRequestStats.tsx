import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export interface PullRequestData {
  open: number;
  closed: number;
  merged: number;
}

export interface PullRequestStatsProps {
  data?: PullRequestData;
}

export function PullRequestStats({ 
  data = { open: 0, closed: 0, merged: 0 } 
}: PullRequestStatsProps) {
  const total = data.open + data.closed + data.merged;
  
  const calculatePercentage = (value: number) => {
    if (total === 0) return 0;
    return Math.round((value / total) * 100);
  };

  const openPercentage = calculatePercentage(data.open);
  const closedPercentage = calculatePercentage(data.closed);
  const mergedPercentage = calculatePercentage(data.merged);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Pull Requests</CardTitle>
        <CardDescription>Your PR status distribution</CardDescription>
      </CardHeader>
      <CardContent>
        {total === 0 ? (
          <div className="flex flex-col items-center justify-center h-[300px] text-muted-foreground">
            <p>No pull request data available.</p>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="h-3 w-3 rounded-full bg-green-500" />
                  <span className="text-sm font-medium">Open</span>
                </div>
                <div className="text-sm text-muted-foreground">
                  {data.open} ({openPercentage}%)
                </div>
              </div>
              <div className="h-2 w-full rounded-full bg-muted">
                <div 
                  className="h-full rounded-full bg-green-500" 
                  style={{ width: `${openPercentage}%` }}
                />
              </div>
            </div>
            
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="h-3 w-3 rounded-full bg-red-500" />
                  <span className="text-sm font-medium">Closed</span>
                </div>
                <div className="text-sm text-muted-foreground">
                  {data.closed} ({closedPercentage}%)
                </div>
              </div>
              <div className="h-2 w-full rounded-full bg-muted">
                <div 
                  className="h-full rounded-full bg-red-500" 
                  style={{ width: `${closedPercentage}%` }}
                />
              </div>
            </div>
            
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="h-3 w-3 rounded-full bg-purple-500" />
                  <span className="text-sm font-medium">Merged</span>
                </div>
                <div className="text-sm text-muted-foreground">
                  {data.merged} ({mergedPercentage}%)
                </div>
              </div>
              <div className="h-2 w-full rounded-full bg-muted">
                <div 
                  className="h-full rounded-full bg-purple-500" 
                  style={{ width: `${mergedPercentage}%` }}
                />
              </div>
            </div>
            
            <div className="pt-4 flex justify-between items-center">
              <div className="text-sm text-muted-foreground">
                Total Pull Requests
              </div>
              <div className="text-2xl font-bold">
                {total}
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}