import { useState } from 'react';
import { 
  Card, 
  CardContent, 
  CardDescription, 
  CardHeader, 
  CardTitle 
} from '@/components/ui/card';
import { 
  Table, 
  TableBody, 
  TableCell, 
  TableHead, 
  TableHeader, 
  TableRow 
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { 
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { UserPlus, AlertCircle } from 'lucide-react';
import type { User } from './types';

interface WorkspaceUsersProps {
  isAdmin: boolean;
  initialUsers: User[];
}

export function WorkspaceUsers({ isAdmin, initialUsers = [] }: WorkspaceUsersProps) {
  const [users, setUsers] = useState<User[]>(initialUsers);
  const [isAddUserDialogOpen, setIsAddUserDialogOpen] = useState(false);
  const [newUser, setNewUser] = useState<Partial<User>>({
    role: 'Developer',
    isActive: true
  });

  const handleAddUser = () => {
    if (newUser.login && newUser.name) {
      const user: User = {
        id: Math.max(0, ...users.map(u => u.id)) + 1,
        login: newUser.login,
        name: newUser.name,
        avatarUrl: `https://github.com/identicons/app/oauth_app/${Date.now()}`,
        role: newUser.role || 'Developer',
        isActive: true
      };
      
      setUsers([...users, user]);
      setIsAddUserDialogOpen(false);
      setNewUser({
        role: 'Developer',
        isActive: true
      });
    }
  };

  const deactivateUser = (id: number) => {
    setUsers(users.map(user => 
      user.id === id ? { ...user, isActive: false } : user
    ));
  };

  const activateUser = (id: number) => {
    setUsers(users.map(user => 
      user.id === id ? { ...user, isActive: true } : user
    ));
  };

  const changeUserRole = (id: number, role: string) => {
    setUsers(users.map(user => 
      user.id === id ? { ...user, role } : user
    ));
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Workspace Users</CardTitle>
          {isAdmin && (
            <Button 
              onClick={() => setIsAddUserDialogOpen(true)}
              size="sm"
              className="flex items-center gap-1"
            >
              <UserPlus className="h-4 w-4" />
              <span>Add User</span>
            </Button>
          )}
        </div>
        <CardDescription>
          {users.length === 0 
            ? "No users in this workspace yet." 
            : `${users.length} user${users.length !== 1 ? 's' : ''} in this workspace`}
        </CardDescription>
      </CardHeader>

      <CardContent>
        {users.length === 0 ? (
          <div className="flex flex-col items-center justify-center p-8 text-center">
            <AlertCircle className="h-12 w-12 text-muted-foreground mb-4" />
            <h3 className="text-lg font-semibold">No Users Found</h3>
            <p className="text-sm text-muted-foreground max-w-sm mt-2">
              {isAdmin 
                ? "This workspace doesn't have any users yet. Add users to collaborate on projects."
                : "This workspace doesn't have any users yet. Contact an administrator to add users."}
            </p>
            {isAdmin && (
              <Button 
                onClick={() => setIsAddUserDialogOpen(true)} 
                className="mt-4"
              >
                Add First User
              </Button>
            )}
          </div>
        ) : (
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>User</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Status</TableHead>
                  {isAdmin && <TableHead className="text-right">Actions</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((user) => (
                  <TableRow key={user.id}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Avatar>
                          <AvatarImage src={user.avatarUrl} alt={user.name} />
                          <AvatarFallback>{user.name.substring(0, 2).toUpperCase()}</AvatarFallback>
                        </Avatar>
                        <div>
                          <div className="font-medium">{user.name}</div>
                          <div className="text-sm text-muted-foreground">@{user.login}</div>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      {isAdmin ? (
                        <Select 
                          defaultValue={user.role}
                          onValueChange={(value) => changeUserRole(user.id, value)}
                        >
                          <SelectTrigger className="w-[140px]">
                            <SelectValue placeholder="Select role" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="Admin">Admin</SelectItem>
                            <SelectItem value="Manager">Manager</SelectItem>
                            <SelectItem value="Developer">Developer</SelectItem>
                            <SelectItem value="Viewer">Viewer</SelectItem>
                          </SelectContent>
                        </Select>
                      ) : (
                        <Badge variant="outline">{user.role}</Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant={user.isActive ? "success" : "destructive"}>
                        {user.isActive ? "Active" : "Inactive"}
                      </Badge>
                    </TableCell>
                    {isAdmin && (
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => user.isActive ? deactivateUser(user.id) : activateUser(user.id)}
                        >
                          {user.isActive ? "Deactivate" : "Activate"}
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>

      <Dialog open={isAddUserDialogOpen} onOpenChange={setIsAddUserDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add New User</DialogTitle>
            <DialogDescription>
              Add a new user to your workspace. They will receive an invitation email.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="username" className="text-right">
                Username
              </Label>
              <Input
                id="username"
                placeholder="johndoe"
                className="col-span-3"
                value={newUser.login || ''}
                onChange={(e) => setNewUser({...newUser, login: e.target.value})}
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="name" className="text-right">
                Full Name
              </Label>
              <Input
                id="name"
                placeholder="John Doe"
                className="col-span-3"
                value={newUser.name || ''}
                onChange={(e) => setNewUser({...newUser, name: e.target.value})}
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="role" className="text-right">
                Role
              </Label>
              <Select
                defaultValue={newUser.role}
                onValueChange={(value) => setNewUser({...newUser, role: value})}
              >
                <SelectTrigger className="col-span-3">
                  <SelectValue placeholder="Select role" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="Admin">Admin</SelectItem>
                  <SelectItem value="Manager">Manager</SelectItem>
                  <SelectItem value="Developer">Developer</SelectItem>
                  <SelectItem value="Viewer">Viewer</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsAddUserDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleAddUser}>
              Add User
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}