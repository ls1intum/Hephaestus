import React from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { 
  Github, 
  Slack, 
  Gitlab, 
  AlertCircle,
  Trello
} from 'lucide-react';
import { BitbucketIcon } from '../icons/BitbucketIcon';
import { JiraIcon } from '../icons/JiraIcon';
import { Separator } from '@/components/ui/separator';
import { Badge } from '@/components/ui/badge';

interface Integration {
  id: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  connected: boolean;
}

export function IntegrationSettings() {
  const [integrations, setIntegrations] = React.useState<Integration[]>([
    {
      id: 'github',
      name: 'GitHub',
      description: 'Connect your GitHub account to sync repositories and track issues.',
      icon: <Github className="h-6 w-6" />,
      connected: true,
    },
    {
      id: 'gitlab',
      name: 'GitLab',
      description: 'Connect your GitLab account to import projects and track merge requests.',
      icon: <Gitlab className="h-6 w-6" />,
      connected: false,
    },
    {
      id: 'bitbucket',
      name: 'Bitbucket',
      description: 'Connect your Bitbucket account to import repositories and track pull requests.',
      icon: <BitbucketIcon className="h-6 w-6" />,
      connected: false,
    },
    {
      id: 'slack',
      name: 'Slack',
      description: 'Receive notifications and updates directly in your Slack workspace.',
      icon: <Slack className="h-6 w-6" />,
      connected: true,
    },
    {
      id: 'jira',
      name: 'Jira',
      description: 'Link GitHub issues to Jira tickets and track workflow.',
      icon: <JiraIcon className="h-6 w-6" />,
      connected: false,
    },
    {
      id: 'trello',
      name: 'Trello',
      description: 'Sync GitHub issues with Trello boards and track progress.',
      icon: <Trello className="h-6 w-6" />,
      connected: false,
    },
  ]);

  const handleToggleIntegration = (id: string) => {
    setIntegrations(integrations.map(integration => 
      integration.id === id 
        ? { ...integration, connected: !integration.connected } 
        : integration
    ));
  };

  const handleConnect = (id: string) => {
    // In a real app, this would initiate OAuth flow
    console.log(`Connecting to ${id}...`);
  };

  const handleDisconnect = (id: string) => {
    // In a real app, this would revoke access tokens
    console.log(`Disconnecting from ${id}...`);
    handleToggleIntegration(id);
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Integrations</CardTitle>
          <CardDescription>
            Connect external services to enhance your workflow.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-6">
            {integrations.map((integration) => (
              <div key={integration.id}>
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-4">
                    <div className="p-2 bg-muted rounded-md">
                      {integration.icon}
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <h3 className="font-medium">{integration.name}</h3>
                        {integration.connected && (
                          <Badge variant="outline" className="text-xs bg-green-50 text-green-700 border-green-200">
                            Connected
                          </Badge>
                        )}
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {integration.description}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {integration.connected ? (
                      <>
                        <Button 
                          variant="outline" 
                          size="sm"
                          onClick={() => handleDisconnect(integration.id)}
                        >
                          Disconnect
                        </Button>
                        <Switch 
                          checked={integration.connected} 
                          onCheckedChange={() => handleToggleIntegration(integration.id)}
                        />
                      </>
                    ) : (
                      <Button 
                        variant="outline" 
                        size="sm"
                        onClick={() => handleConnect(integration.id)}
                      >
                        Connect
                      </Button>
                    )}
                  </div>
                </div>
                <Separator className="my-4" />
              </div>
            ))}

            <div className="p-4 border border-amber-200 bg-amber-50 dark:bg-amber-900/20 dark:border-amber-800 rounded-lg">
              <div className="flex items-start gap-3">
                <AlertCircle className="h-5 w-5 text-amber-600 dark:text-amber-400 mt-0.5" />
                <div>
                  <h4 className="text-sm font-medium text-amber-800 dark:text-amber-300">Integration Permissions</h4>
                  <p className="text-sm text-amber-700 dark:text-amber-400 mt-1">
                    When connecting integrations, you grant permission to access certain data. 
                    You can review and revoke these permissions at any time from the integration's website.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}