export interface LabelInfo {
  id: number;
  name: string;
  color: string;
  description?: string;
  repository?: {
    id: number;
    nameWithOwner: string;
  };
}

export interface RepositoryInfo {
  id: number;
  nameWithOwner: string;
  description?: string;
  url?: string;
  isPrivate?: boolean;
}

export interface TeamMember {
  id: number;
  login: string;
  name?: string;
  avatarUrl?: string;
}

export interface TeamInfo {
  id: number;
  name: string;
  color: string;
  hidden: boolean;
  repositories: RepositoryInfo[];
  labels: LabelInfo[];
  members: TeamMember[];
}