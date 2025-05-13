import type { Meta, StoryObj } from "@storybook/react";
import { ProfileHeader } from "./ProfileHeader";

const meta = {
  title: "Profile/ProfileHeader",
  component: ProfileHeader,
  parameters: {
    layout: "centered",
  },
  tags: ["autodocs"],
} satisfies Meta<typeof ProfileHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    isLoading: false,
    user: {
      id: 1,
      login: "johndoe",
      name: "John Doe",
      avatarUrl: "https://github.com/github.png",
      htmlUrl: "https://github.com/johndoe",
      leaguePoints: 150,
    },
    firstContribution: "2022-05-15T00:00:00Z",
    contributedRepositories: [
      {
        id: 1,
        name: "Hephaestus",
        nameWithOwner: "ls1intum/Hephaestus",
        description: "A GitHub contribution tracking tool",
        htmlUrl: "https://github.com/ls1intum/Hephaestus",
      },
      {
        id: 2,
        name: "Artemis",
        nameWithOwner: "ls1intum/Artemis",
        description: "Interactive learning platform",
        htmlUrl: "https://github.com/ls1intum/Artemis",
      },
    ],
  },
};

export const Loading: Story = {
  args: {
    isLoading: true,
  },
};

export const NoRepositories: Story = {
  args: {
    isLoading: false,
    user: {
      id: 1,
      login: "janedoe",
      name: "Jane Doe",
      avatarUrl: "https://github.com/octocat.png",
      htmlUrl: "https://github.com/janedoe",
      leaguePoints: 75,
    },
    firstContribution: "2023-01-10T00:00:00Z",
    contributedRepositories: [],
  },
};
