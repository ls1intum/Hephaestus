import type { LeaderboardEntry, UserInfo } from "@/api/types.gen";

export type LeaderboardSortType = 'SCORE' | 'LEAGUE_POINTS';

export interface LeagueIconProps {
  leaguePoints?: number;
  size?: 'sm' | 'default' | 'lg' | 'max' | 'full';
  showPoints?: boolean;
  className?: string;
}

export interface ReviewsPopoverProps {
  reviewedPRs: LeaderboardEntry['reviewedPullRequests'];
  highlight?: boolean;
}

export interface LeaderboardTableProps {
  leaderboard?: LeaderboardEntry[];
  isLoading: boolean;
  currentUser?: UserInfo;
}

export interface LeaderboardOverviewProps {
  leaderboardEntry: LeaderboardEntry;
  leaguePoints: number;
  leaderboardEnd?: string;
  leaguePointsChange?: number;
}

export interface LeaderboardLegendProps {
  // No specific props needed for the legend component
}

export interface LeaderboardFilterProps {
  teams: string[];
  onTeamChange?: (team: string) => void;
  onSortChange?: (sort: LeaderboardSortType) => void;
  onTimeframeChange?: (afterDate: string, beforeDate: string, timeframe?: string) => void;
  selectedTeam?: string;
  selectedSort?: LeaderboardSortType;
  initialAfterDate?: string;
  initialBeforeDate?: string;
  leaderboardSchedule?: {
    day: number;
    hour: number;
    minute: number;
    formatted: string;
  };
}

export interface TimeframeFilterProps {
  onTimeframeChange?: (afterDate: string, beforeDate: string, timeframe?: string) => void;
  initialAfterDate?: string;
  initialBeforeDate?: string;
  leaderboardSchedule?: {
    day: number;
    hour: number;
    minute: number;
    formatted: string;
  };
}

export interface SortFilterProps {
  onSortChange?: (sort: LeaderboardSortType) => void;
  selectedSort?: LeaderboardSortType;
}

export interface TeamFilterProps {
  teams: string[];
  onTeamChange?: (team: string) => void;
  selectedTeam?: string;
}