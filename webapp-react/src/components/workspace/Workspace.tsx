import { useState } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { AlertTriangle } from 'lucide-react';
import { WorkspaceUsers } from './WorkspaceUsers';
import { WorkspaceRepositories } from './WorkspaceRepositories';
import { WorkspaceTeams } from './WorkspaceTeams';
import type { User } from './types';

export interface WorkspaceProps {
  organizationName: string;
  userCount?: number;
  repositoryCount?: number;
  teamCount?: number;
  isAdmin?: boolean;
}

export function Workspace({
  organizationName,
  userCount = 0,
  repositoryCount = 0,
  teamCount = 0,
  isAdmin = false,
}: WorkspaceProps) {
  const [activeTab, setActiveTab] = useState('users');

  // Mock data - in a real app this would come from an API
  const mockUsers: User[] = [
    {
      id: 1,
      login: 'johndoe',
      name: 'John Doe',
      avatarUrl: 'https://github.com/identicons/app/oauth_app/1',
      role: 'Admin',
      isActive: true,
    },
    {
      id: 2,
      login: 'janedoe',
      name: 'Jane Doe',
      avatarUrl: 'https://github.com/identicons/app/oauth_app/2',
      role: 'Developer',
      isActive: true,
    }
  ];

  return (
    <div className="container py-6">
      <div className="flex flex-col space-y-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{organizationName}</h1>
          <p className="text-muted-foreground">A collaborative workspace for your team</p>
        </div>

        {!isAdmin && (
            <Alert variant="warning" className="w-auto">
              <AlertTriangle className="h-4 w-4" />
              <AlertTitle>Limited Access</AlertTitle>
              <AlertDescription>
                You are viewing this workspace with limited permissions. Some actions may not be available.
              </AlertDescription>
            </Alert>
        )}

        <Tabs defaultValue="overview" value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="grid w-full md:w-auto grid-cols-4">
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="repositories">Repositories</TabsTrigger>
            <TabsTrigger value="users">Users</TabsTrigger>
            <TabsTrigger value="teams">Teams</TabsTrigger>
          </TabsList>
          
          <TabsContent value="overview" className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>Workspace Overview</CardTitle>
                <CardDescription>
                  Key metrics and information about this workspace
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                  <Card>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-sm font-medium">
                        Repositories
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="text-2xl font-bold">{repositoryCount}</div>
                    </CardContent>
                  </Card>
                  <Card>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-sm font-medium">
                        Users
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="text-2xl font-bold">{userCount}</div>
                    </CardContent>
                  </Card>
                  <Card>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-sm font-medium">
                        Teams
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="text-2xl font-bold">{teamCount}</div>
                    </CardContent>
                  </Card>
                  <Card>
                    <CardHeader className="pb-2">
                      <CardTitle className="text-sm font-medium">
                        Pull Requests
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="text-2xl font-bold">24</div>
                    </CardContent>
                  </Card>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="repositories">
            <WorkspaceRepositories isAdmin={isAdmin} />
          </TabsContent>
          
          <TabsContent value="users">
            <WorkspaceUsers isAdmin={isAdmin} initialUsers={mockUsers} />
          </TabsContent>
          
          <TabsContent value="teams">
            <WorkspaceTeams isAdmin={isAdmin} />
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}