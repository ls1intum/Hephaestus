export enum MessageSender {
  User = 'USER',
  Mentor = 'MENTOR'
}

export interface Message {
  id: string;
  content: string;
  sender: MessageSender;
  sentAt: string;
}

export interface StatusItem {
  title: string;
  description: string;
}

export interface ChatSummary {
  response: string;
  status: StatusItem[];
  impediments: StatusItem[];
  promises: StatusItem[];
}

export interface PullRequest {
  url: string;
  repo: string;
  number: number;
  title: string;
  status: string;
  created_at: string;
}

export interface PullRequestsOverview {
  response: string;
  development: PullRequest[];
}