import type { Meta, StoryObj } from "@storybook/react";
import { TeamsPage } from "./TeamsPage";
import type { TeamInfo } from "@/api/types.gen";

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
  {
    id: 4,
    name: "Hidden Team",
    color: "#ec4899",
    repositories: [],
    labels: [],
    members: [
      {
        id: 4,
        login: "hiddenuser",
        name: "Hidden User",
        avatarUrl: "https://avatars.githubusercontent.com/u/4?v=4",
        htmlUrl: "https://github.com/hiddenuser",
      },
    ],
    hidden: true,
  },
];

const meta: Meta<typeof TeamsPage> = {
  component: TeamsPage,
  tags: ["autodocs"],
  parameters: {
    layout: "fullscreen",
  },
};

export default meta;
type Story = StoryObj<typeof TeamsPage>;

export const WithTeams: Story = {
  args: {
    teams: mockTeams,
    isLoading: false,
  },
};

export const Loading: Story = {
  args: {
    teams: [],
    isLoading: true,
  },
};

export const Empty: Story = {
  args: {
    teams: [],
    isLoading: false,
  },
};
