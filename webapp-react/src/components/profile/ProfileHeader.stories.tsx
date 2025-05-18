import type { Meta, StoryObj } from "@storybook/react";
import { ProfileHeader } from "./ProfileHeader";

/**
 * Header component for the user profile page displaying key user information.
 * Shows the user's GitHub profile details, league points, contribution history,
 * and repositories they've contributed to.
 */
const meta = {
  component: ProfileHeader,
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component: 'Header section of the profile page showing user information, stats, and repository contributions.',
      },
    },
  },
  argTypes: {
    isLoading: {
      description: 'Whether the component is in a loading state',
      control: 'boolean',
    },
    user: {
      description: 'User profile data from GitHub',
      control: 'object',
    },
    leaguePoints: {
      description: 'Total league points earned by the user',
      control: 'number',
    },
    firstContribution: {
      description: 'Date of the user\'s first contribution (ISO string)',
      control: 'text',
    },
    contributedRepositories: {
      description: 'List of repositories the user has contributed to',
      control: 'object',
    },
  },
  tags: ["autodocs"],
} satisfies Meta<typeof ProfileHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Standard profile header showing an active contributor with multiple repositories
 * and a significant number of league points.
 */
export const Default: Story = {
  args: {
    isLoading: false,
    user: {
      id: 1,
      login: "johndoe",
      name: "John Doe",
      avatarUrl: "https://github.com/github.png",
      htmlUrl: "https://github.com/johndoe",
    },
    leaguePoints: 1450,
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

/**
 * Loading state displayed while user data is being fetched.
 */
export const Loading: Story = {
  args: {
    isLoading: true,
    leaguePoints: 0,
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
    },
    leaguePoints: 750,
    firstContribution: "2023-01-10T00:00:00Z",
    contributedRepositories: [],
  },
};

export const BronzeLeague: Story = {
  args: {
    isLoading: false,
    user: {
      id: 2,
      login: "bronzeUser",
      name: "Bronze User",
      avatarUrl: "https://github.com/github.png",
      htmlUrl: "https://github.com/bronzeUser",
    },
    leaguePoints: 1000,
    firstContribution: "2023-03-15T00:00:00Z",
    contributedRepositories: [],
  },
};

export const SilverLeague: Story = {
  args: {
    isLoading: false,
    user: {
      id: 3,
      login: "silverUser",
      name: "Silver User",
      avatarUrl: "https://github.com/github.png",
      htmlUrl: "https://github.com/silverUser",
    },
    leaguePoints: 1400,
    firstContribution: "2022-10-10T00:00:00Z",
    contributedRepositories: [],
  },
};

export const GoldLeague: Story = {
  args: {
    isLoading: false,
    user: {
      id: 4,
      login: "goldUser",
      name: "Gold User",
      avatarUrl: "https://github.com/github.png",
      htmlUrl: "https://github.com/goldUser",
    },
    leaguePoints: 1650,
    firstContribution: "2022-07-22T00:00:00Z",
    contributedRepositories: [],
  },
};

export const DiamondLeague: Story = {
  args: {
    isLoading: false,
    user: {
      id: 5,
      login: "diamondUser",
      name: "Diamond User",
      avatarUrl: "https://github.com/github.png",
      htmlUrl: "https://github.com/diamondUser",
    },
    leaguePoints: 1900,
    firstContribution: "2021-12-05T00:00:00Z",
    contributedRepositories: [],
  },
};

export const MasterLeague: Story = {
  args: {
    isLoading: false,
    user: {
      id: 6,
      login: "masterUser",
      name: "Master User",
      avatarUrl: "https://github.com/github.png",
      htmlUrl: "https://github.com/masterUser",
    },
    leaguePoints: 2200,
    firstContribution: "2020-05-01T00:00:00Z",
    contributedRepositories: [],
  },
};
