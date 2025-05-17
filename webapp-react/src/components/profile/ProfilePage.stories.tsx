import type { Meta, StoryObj } from "@storybook/react";
import { ProfilePage } from "./ProfilePage";

/**
 * Complete user profile page that combines header and content sections.
 * Displays a user's GitHub information, contributions, review activity,
 * and open pull requests in a unified interface.
 */
const meta = {
  title: "Profile/ProfilePage",
  component: ProfilePage,
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component: 'The main profile page that integrates all profile components into a cohesive user profile view.',
      },
    },
  },
  argTypes: {
    isLoading: {
      description: 'Whether the page is in a loading state',
      control: 'boolean',
      table: {
        type: { summary: 'boolean' },
        defaultValue: { summary: 'false' },
      },
    },
    error: {
      description: 'Whether there was an error loading the profile data',
      control: 'boolean',
      table: {
        type: { summary: 'boolean' },
        defaultValue: { summary: 'false' },
      },
    },
    username: {
      description: 'GitHub username of the profile owner',
      control: 'text',
      table: {
        type: { summary: 'string' },
      },
    },
    profileData: {
      description: 'Complete profile data object containing user info and activity',
      control: 'object',
      table: {
        type: { summary: 'object' },
      },
    },
  },
  tags: ["autodocs"],
} satisfies Meta<typeof ProfilePage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Standard profile view showing a user with activity across multiple repositories.
 */
export const Default: Story = {
  args: {
    isLoading: false,
    error: false,
    username: "johndoe",
    profileData: {
      userInfo: {
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
      reviewActivity: [
        {
          id: 1,
          state: "APPROVED" as const,
          submittedAt: new Date().toISOString(),
          htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
          pullRequest: {
            id: 101,
            title: "Add new feature to dashboard",
            number: 42,
            state: "OPEN" as const,
            isDraft: false,
            isMerged: false,
            htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
            repository: {
              id: 1,
              name: "Hephaestus",
              nameWithOwner: "ls1intum/Hephaestus",
              htmlUrl: "https://github.com/ls1intum/Hephaestus",
            },
          },
          score: 5,
          isDismissed: false,
          codeComments: 2,
        },
        {
          id: 2,
          state: "CHANGES_REQUESTED" as const,
          submittedAt: new Date().toISOString(),
          htmlUrl: "https://github.com/ls1intum/Artemis/pull/123",
          pullRequest: {
            id: 102,
            title: "Fix bug in submission process",
            number: 123,
            state: "OPEN" as const,
            isDraft: false,
            isMerged: false,
            htmlUrl: "https://github.com/ls1intum/Artemis/pull/123",
            repository: {
              id: 2,
              name: "Artemis",
              nameWithOwner: "ls1intum/Artemis",
              htmlUrl: "https://github.com/ls1intum/Artemis",
            },
          },
          score: 3,
          isDismissed: false,
          codeComments: 5,
        },
        {
          id: 3,
          state: "COMMENTED" as const,
          submittedAt: new Date().toISOString(),
          htmlUrl: "https://github.com/ls1intum/Athena/pull/56",
          pullRequest: {
            id: 103,
            title: "Update documentation for API endpoints",
            number: 56,
            state: "OPEN" as const,
            isDraft: false,
            isMerged: false,
            htmlUrl: "https://github.com/ls1intum/Athena/pull/56",
            repository: {
              id: 3,
              name: "Athena",
              nameWithOwner: "ls1intum/Athena",
              htmlUrl: "https://github.com/ls1intum/Athena",
            },
          },
          score: 1,
          isDismissed: false,
          codeComments: 0,
        },
      ],
      openPullRequests: [
        {
          id: 101,
          number: 42,
          title: "Implement new dashboard features",
          state: "OPEN",
          isDraft: false,
          isMerged: false,
          commentsCount: 5,
          additions: 150,
          deletions: 50,
          htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
          createdAt: new Date().toISOString(),
          repository: {
            id: 1,
            name: "Hephaestus",
            nameWithOwner: "ls1intum/Hephaestus",
            htmlUrl: "https://github.com/ls1intum/Hephaestus",
          },
          labels: [
            { id: 1, name: "enhancement", color: "0E8A16" },
            { id: 2, name: "frontend", color: "FBCA04" }
          ],
        },
        {
          id: 102,
          number: 87,
          title: "WIP: Refactor authentication module",
          state: "OPEN",
          isDraft: true,
          isMerged: false,
          commentsCount: 0,
          additions: 320,
          deletions: 280,
          htmlUrl: "https://github.com/ls1intum/Artemis/pull/87",
          createdAt: new Date().toISOString(),
          repository: {
            id: 2,
            name: "Artemis",
            nameWithOwner: "ls1intum/Artemis",
            htmlUrl: "https://github.com/ls1intum/Artemis",
          },
          labels: [
            { id: 3, name: "refactoring", color: "D93F0B" },
            { id: 4, name: "security", color: "5319E7" }
          ],
        },
      ],
    },
  }
};

/**
 * Loading state shown while profile data is being fetched.
 */
export const Loading: Story = {
  args: {
    isLoading: true,
    error: false,
    username: "johndoe",
    profileData: undefined,
  }
};

/**
 * Error state displayed when profile data could not be loaded.
 */
export const Error: Story = {
  args: {
    isLoading: false,
    error: true,
    username: "johndoe",
    profileData: undefined,
  }
};

/**
 * Shows how the profile page appears for a new user with no activity.
 */
export const Empty: Story = {
  args: {
    isLoading: false,
    error: false,
    username: "emptydoe",
    profileData: {
      userInfo: {
        id: 3,
        login: "emptydoe",
        name: "Empty Doe",
        avatarUrl: "https://github.com/octocat.png",
        htmlUrl: "https://github.com/emptydoe",
        leaguePoints: 0,
      },
      firstContribution: "2023-10-15T00:00:00Z",
      contributedRepositories: [],
      reviewActivity: [],
      openPullRequests: [],
    },
  }
};
