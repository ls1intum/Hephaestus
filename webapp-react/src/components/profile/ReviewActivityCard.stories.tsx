import type { Meta, StoryObj } from "@storybook/react";
import { ReviewActivityCard } from "./ReviewActivityCard";

/**
 * Card component that displays a user's review activity for a specific pull request.
 * Shows the review state, submission time, and score earned from the review.
 */
const meta = {
  component: ReviewActivityCard,
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component: 'Displays information about a code review performed by the user, including the status and score earned.',
      },
    },
  },
  argTypes: {
    isLoading: {
      description: 'Whether the card is in a loading state',
      control: 'boolean',
    },
    state: {
      description: 'The state of the review',
      control: 'select',
      options: ['APPROVED', 'CHANGES_REQUESTED', 'COMMENTED', 'DISMISSED', 'PENDING'],
    },
    submittedAt: {
      description: 'When the review was submitted (ISO date string)',
      control: 'text',
    },
    htmlUrl: {
      description: 'URL to the pull request or review',
      control: 'text',
    },
    pullRequest: {
      description: 'Pull request details',
      control: 'object',
    },
    repositoryName: {
      description: 'Name of the repository',
      control: 'text',
    },
    score: {
      description: 'Points earned for the review',
      control: 'number',
    },
  },
  tags: ["autodocs"],
} satisfies Meta<typeof ReviewActivityCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Shows a review where the user approved the pull request and earned points.
 */
export const Approved: Story = {
  args: {
    isLoading: false,
    state: "APPROVED",
    submittedAt: new Date().toISOString(),
    htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
    pullRequest: {
      title: "Add new feature to dashboard",
      number: 42,
      repository: {
        name: "Hephaestus",
      },
    },
    repositoryName: "Hephaestus",
    score: 5,
  },
};

/**
 * Shows a review where the user requested changes to the pull request.
 */
export const ChangesRequested: Story = {
  args: {
    isLoading: false,
    state: "CHANGES_REQUESTED",
    submittedAt: new Date().toISOString(),
    htmlUrl: "https://github.com/ls1intum/Artemis/pull/123",
    pullRequest: {
      title: "Fix bug in submission process",
      number: 123,
      repository: {
        name: "Artemis",
      },
    },
    repositoryName: "Artemis",
    score: 3,
  },
};

/**
 * Shows a review where the user only left comments without approving or requesting changes.
 */
export const Commented: Story = {
  args: {
    isLoading: false,
    state: "COMMENTED",
    submittedAt: new Date().toISOString(),
    htmlUrl: "https://github.com/ls1intum/Athena/pull/56",
    pullRequest: {
      title: "Update documentation for API endpoints",
      number: 56,
      repository: {
        name: "Athena",
      },
    },
    repositoryName: "Athena",
    score: 1,
  },
};

/**
 * Shows the loading state of the card when data is being fetched.
 */
export const Loading: Story = {
  args: {
    isLoading: true,
    state: "COMMENTED",
    submittedAt: new Date().toISOString(),
    htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/77", 
    pullRequest: {
      title: "Loading...",
      number: 77,
      repository: {
        name: "Hephaestus",
      },
    },
    repositoryName: "Hephaestus",
    score: 0,
  },
};

/**
 * Shows a dismissed review which no longer counts towards the user's score.
 */
export const Dismissed: Story = {
  args: {
    isLoading: false,
    state: "COMMENTED",
    submittedAt: new Date().toISOString(),
    htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/99",
    pullRequest: {
      title: "Refactor authentication system",
      number: 99,
      repository: {
        name: "Hephaestus",
      },
    },
    repositoryName: "Hephaestus",
    score: 0,
  },
};

/**
 * Shows a review with an unknown state that doesn't match standard GitHub review states.
 */
export const Unknown: Story = {
  args: {
    isLoading: false,
    state: "UNKNOWN",
    submittedAt: new Date().toISOString(),
    htmlUrl: "https://github.com/ls1intum/ExampleRepo/pull/78",
    pullRequest: {
      title: "Initial implementation of feature X",
      number: 78,
      repository: {
        name: "ExampleRepo",
      },
    },
    repositoryName: "ExampleRepo",
    score: 0,
  },
};

/**
 * Shows a review with code snippet references in the title.
 */
export const WithCodeInTitle: Story = {
  args: {
    isLoading: false,
    state: "APPROVED",
    submittedAt: new Date().toISOString(),
    htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
    pullRequest: {
      title: "Update `LeaderboardTable` component and fix `ProfileContent` layout",
      number: 42,
      repository: {
        name: "Hephaestus",
      },
    },
    repositoryName: "Hephaestus",
    score: 5,
  },
};
