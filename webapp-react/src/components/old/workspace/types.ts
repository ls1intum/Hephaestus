export type User = {
  id: number;
  login: string;
  name: string;
  avatarUrl: string;
  role: string;
  isActive: boolean;
};

export type Workspace = {
  id: number;
  name: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  owner: User;
  users: User[];
  isPublic: boolean;
};

export type UserRole = 'Admin' | 'Manager' | 'Developer' | 'Viewer';