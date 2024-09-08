export namespace Leaderboard {
  export interface Entry {
    githubName: string;
    name: string;
    score: number;
    total: number;
    changes_requested: number;
    approvals: number;
    comments: number;
  }
}
