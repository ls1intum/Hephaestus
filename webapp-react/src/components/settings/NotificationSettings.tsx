import React from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { Loader2 } from 'lucide-react';

export function NotificationSettings() {
  const [isLoading, setIsLoading] = React.useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    
    // Simulating API call
    setTimeout(() => {
      setIsLoading(false);
    }, 1500);
  };
  
  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Notifications</CardTitle>
          <CardDescription>
            Configure how and when you want to be notified about activity.
          </CardDescription>
        </CardHeader>
        <form onSubmit={handleSubmit}>
          <CardContent className="space-y-6">
            <div className="space-y-4">
              <h3 className="text-lg font-medium">Pull Requests</h3>
              
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="pr-assigned">When assigned to me</Label>
                  <p className="text-sm text-muted-foreground">
                    Receive notifications when you are assigned to a pull request.
                  </p>
                </div>
                <Switch id="pr-assigned" defaultChecked />
              </div>
              
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="pr-review">Review requested</Label>
                  <p className="text-sm text-muted-foreground">
                    Receive notifications when you're requested to review a pull request.
                  </p>
                </div>
                <Switch id="pr-review" defaultChecked />
              </div>
              
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="pr-comment">New comments</Label>
                  <p className="text-sm text-muted-foreground">
                    Receive notifications when comments are added to pull requests you're involved with.
                  </p>
                </div>
                <Switch id="pr-comment" defaultChecked />
              </div>
            </div>

            <Separator />
            
            <div className="space-y-4">
              <h3 className="text-lg font-medium">Issues</h3>
              
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="issue-assigned">When assigned to me</Label>
                  <p className="text-sm text-muted-foreground">
                    Receive notifications when you are assigned to an issue.
                  </p>
                </div>
                <Switch id="issue-assigned" defaultChecked />
              </div>
              
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="issue-mention">When mentioned</Label>
                  <p className="text-sm text-muted-foreground">
                    Receive notifications when you're mentioned in an issue.
                  </p>
                </div>
                <Switch id="issue-mention" defaultChecked />
              </div>
              
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="issue-activity">Issue activity</Label>
                  <p className="text-sm text-muted-foreground">
                    Receive notifications for updates to issues you've created or participated in.
                  </p>
                </div>
                <Switch id="issue-activity" />
              </div>
            </div>

            <Separator />
            
            <div className="space-y-4">
              <h3 className="text-lg font-medium">Delivery Methods</h3>
              
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="email-notify">Email notifications</Label>
                  <p className="text-sm text-muted-foreground">
                    Receive notifications via email.
                  </p>
                </div>
                <Switch id="email-notify" defaultChecked />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="browser-notify">Browser notifications</Label>
                  <p className="text-sm text-muted-foreground">
                    Show browser notifications when you're using the application.
                  </p>
                </div>
                <Switch id="browser-notify" defaultChecked />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="mobile-notify">Mobile push notifications</Label>
                  <p className="text-sm text-muted-foreground">
                    Send push notifications to your mobile device.
                  </p>
                </div>
                <Switch id="mobile-notify" />
              </div>
            </div>
          </CardContent>
          <CardFooter className="flex justify-between">
            <Button type="button" variant="ghost">
              Reset to Defaults
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving
                </>
              ) : (
                'Save Changes'
              )}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}