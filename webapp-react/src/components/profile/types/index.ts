import type { 
  LabelInfo, 
  PullRequestInfo, 
  PullRequestReviewInfo, 
  RepositoryInfo, 
  UserInfo 
} from "@/api/types.gen";

export interface ProfileHeaderProps {
  user?: UserInfo;
  firstContribution?: string;
  contributedRepositories?: RepositoryInfo[];
  leaguePoints?: number;
  isLoading: boolean;
}

export interface ReviewActivityCardProps {
  isLoading: boolean;
  state?: 'COMMENTED' | 'APPROVED' | 'CHANGES_REQUESTED' | 'UNKNOWN';
  submittedAt?: string;
  htmlUrl?: string;
  pullRequest?: {
    id?: number;
    title?: string;
    number?: number;
    state?: string;
    isDraft?: boolean;
    isMerged?: boolean;
    htmlUrl?: string;
    repository?: {
      id?: number;
      name?: string;
      nameWithOwner?: string;
      htmlUrl?: string;
    };
  };
  repositoryName?: string;
  score?: number;
}

export interface IssueCardProps {
  isLoading: boolean;
  title?: string;
  number?: number;
  additions?: number;
  deletions?: number;
  htmlUrl?: string;
  repositoryName?: string;
  createdAt?: string;
  state?: 'OPEN' | 'CLOSED';
  isDraft?: boolean;
  isMerged?: boolean;
  pullRequestLabels?: LabelInfo[];
}

export interface ProfileContentProps {
  reviewActivity?: PullRequestReviewInfo[];
  openPullRequests?: PullRequestInfo[];
  isLoading: boolean;
  username: string;
}
