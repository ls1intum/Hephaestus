import type { Meta, StoryObj } from "@storybook/react";
import { TeamLeaderboardTable } from "./TeamLeaderboardTable";
import type { TeamInfo } from "@/api/types.gen";
import { fn } from "@storybook/test";

// Mock teams from TeamsPage.stories.tsx
const mockTeams: TeamInfo[] = [
  {
    id: 1,
    name: "Frontend",
    color: "#0ea5e9",
    repositories: [],
    labels: [],
    members: [
      {
        id: 1,
        login: "johndoe",
        name: "John Doe",
        avatarUrl: "https://avatars.githubusercontent.com/u/1?v=4",
        htmlUrl: "https://github.com/johndoe",
      },
      {
        id: 2,
        login: "janedoe",
        name: "Jane Doe",
        avatarUrl: "https://avatars.githubusercontent.com/u/2?v=4",
        htmlUrl: "https://github.com/janedoe",
      },
    ],
    hidden: false,
  },
  {
    id: 2,
    name: "Backend",
    color: "#10b981",
    repositories: [],
    labels: [],
    members: [
      {
        id: 3,
        login: "bobsmith",
        name: "Bob Smith",
        avatarUrl: "https://avatars.githubusercontent.com/u/3?v=4",
        htmlUrl: "https://github.com/bobsmith",
      },
    ],
    hidden: false,
  },
  {
    id: 3,
    name: "DevOps",
    color: "#6366f1",
    repositories: [],
    labels: [],
    members: [],
    hidden: false,
  },
];

// Example leaderboard data
const mockLeaderboard = [
  {
    rank: 1,
    score: 120,
    team: mockTeams[0],
    activity: {
      prs: 20,
      reviews: 15,
      comments: 50,
    },
    league: "Gold",
  },
  {
    rank: 2,
    score: 90,
    team: mockTeams[1],
    activity: {
      prs: 12,
      reviews: 10,
      comments: 30,
    },
    league: "Silver",
  },
  {
    rank: 3,
    score: 40,
    team: mockTeams[2],
    activity: {
      prs: 5,
      reviews: 2,
      comments: 10,
    },
    league: "Bronze",
  },
];

const meta = {
  component: TeamLeaderboardTable,
  tags: ["autodocs"],
  parameters: {
    layout: "padded",
  },
  argTypes: {
    leaderboard: {
      description: "Array of team leaderboard entries to display",
    },
    isLoading: {
      description: "Whether the leaderboard data is currently loading",
      control: "boolean",
    },
    currentTeam: {
      description: "Currently logged in users teams info to highlight",
    },
    onTeamClick: {
      description: "Callback function when a team row is clicked",
      action: "clicked",
    },
  },
  args: {
    onTeamClick: fn(),
  },
} satisfies Meta<typeof TeamLeaderboardTable>;

export default meta;
type Story = StoryObj<typeof TeamLeaderboardTable>;

export const Default: Story = {
  args: {
    leaderboard: mockLeaderboard,
    isLoading: false,
  },
};

export const Loading: Story = {
  args: {
    leaderboard: [],
    isLoading: true,
  },
};

export const Empty: Story = {
  args: {
    leaderboard: [],
    isLoading: false,
  },
};

export const WithCurrentTeam: Story = {
  args: {
    leaderboard: mockLeaderboard,
    isLoading: false,
    currentTeam: mockTeams[0],
  },
};