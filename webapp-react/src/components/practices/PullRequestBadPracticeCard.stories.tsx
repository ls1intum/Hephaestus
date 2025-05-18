import type { Meta, StoryObj } from "@storybook/react";
import { PullRequestBadPracticeCard } from "./PullRequestBadPracticeCard";
import { fn } from "@storybook/test";

/**
 * Card component for displaying pull requests with their metadata and analysis options.
 * Allows users to trigger bad practice detection on specific pull requests.
 */
const meta = {
  component: PullRequestBadPracticeCard,
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component: 'A card component for displaying pull request information with options to analyze the code for bad practices.',
      },
    },
  },
  tags: ["autodocs"],
  argTypes: {
    id: {
      description: 'Unique identifier for the pull request',
      control: 'number',
    },
    title: {
      description: 'Pull request title',
      control: 'text',
    },
    number: {
      description: 'Pull request number (as shown in GitHub)',
      control: 'number',
    },
    additions: {
      description: 'Number of lines added in the PR',
      control: 'number',
    },
    deletions: {
      description: 'Number of lines deleted in the PR',
      control: 'number',
    },
    htmlUrl: {
      description: 'Link to the pull request',
      control: 'text',
    },
    state: {
      description: 'Current state of the PR',
      control: 'select',
      options: ['OPEN', 'CLOSED'],
    },
    isDraft: {
      description: 'Whether the PR is a draft',
      control: 'boolean',
    },
    isMerged: {
      description: 'Whether the PR has been merged',
      control: 'boolean',
    },
    repositoryName: {
      description: 'Name of the repository the PR belongs to',
      control: 'text',
    },
    createdAt: {
      description: 'Creation date of the PR',
      control: 'text',
    },
    pullRequestLabels: {
      description: 'Array of labels attached to the PR',
      control: 'object',
    },
    onDetectBadPractices: {
      description: 'Callback when detect bad practices button is clicked',
      action: "detect bad practices clicked"
    }
  },
  args: {
    onDetectBadPractices: fn(),
  }
} satisfies Meta<typeof PullRequestBadPracticeCard>;

export default meta;

type Story = StoryObj<typeof PullRequestBadPracticeCard>;

/**
 * Default view of an open pull request with multiple labels
 */
export const Default: Story = {
  args: {
    id: 1,
    title: "Add feature X",
    number: 12,
    additions: 10,
    deletions: 5,
    htmlUrl: "http://example.com",
    state: "OPEN",
    isDraft: false,
    isMerged: false,
    repositoryName: "Artemis",
    createdAt: "2024-01-01",
    pullRequestLabels: [
      { id: 1, name: "bug", color: "d73a4a" },
      { id: 2, name: "enhancement", color: "0e8a16" }
    ],
    badPractices: [
      {
        id: 1,
        title: "Avoid using any type",
        description: "Using the any type defeats the purpose of TypeScript.",
        state: "CRITICAL_ISSUE"
      },
      {
        id: 2,
        title: "Unchecked checkbox in description",
        description: "Unchecked checkboxes in the description are not allowed.",
        state: "MINOR_ISSUE"
      }
    ],
    badPracticeSummary: "We found 2 bad practices in this pull request. Please fix them. Thank you!"
  }
};

export const Loading: Story = {
  args: {
    id: 1,
    isLoading: true
  }
};

export const WithGoodPractices: Story = {
  args: {
    id: 1,
    title: "Add feature X",
    number: 12,
    htmlUrl: "http://example.com",
    state: "OPEN",
    badPractices: [
      {
        id: 1,
        title: "Good code structure",
        description: "The code follows a clean structure with proper separation of concerns.",
        state: "GOOD_PRACTICE"
      },
      {
        id: 2,
        title: "Well-documented functions",
        description: "Functions are properly documented with JSDoc comments.",
        state: "GOOD_PRACTICE"
      }
    ],
    badPracticeSummary: "Great work! We found 2 good practices in your code."
  }
};

export const WithUserControls: Story = {
  args: {
    id: 1,
    title: "Add feature X",
    number: 12,
    htmlUrl: "http://example.com",
    state: "OPEN",
    badPractices: [
      {
        id: 1,
        title: "Avoid using any type",
        description: "Using the any type defeats the purpose of TypeScript.",
        state: "CRITICAL_ISSUE"
      }
    ],
    badPracticeSummary: "We found 1 bad practice in this pull request. Please fix it.",
    currUserIsDashboardUser: true
  }
};

export const WithMixedPractices: Story = {
  args: {
    id: 1,
    title: "Add feature X",
    number: 12,
    htmlUrl: "http://example.com",
    state: "OPEN",
    badPractices: [
      {
        id: 1,
        title: "Good code structure",
        description: "The code follows a clean structure with proper separation of concerns.",
        state: "GOOD_PRACTICE"
      },
      {
        id: 2,
        title: "Avoid using any type",
        description: "Using the any type defeats the purpose of TypeScript.",
        state: "CRITICAL_ISSUE"
      },
      {
        id: 3,
        title: "Missing error handling",
        description: "Error handling is missing in async functions.",
        state: "NORMAL_ISSUE"
      }
    ],
    badPracticeSummary: "We found 1 good practice and 2 issues in this pull request."
  }
};

export const WithOldPractices: Story = {
  args: {
    id: 1,
    title: "Add feature X",
    number: 12,
    htmlUrl: "http://example.com",
    state: "OPEN",
    badPractices: [
      {
        id: 1,
        title: "Avoid using any type",
        description: "Using the any type defeats the purpose of TypeScript.",
        state: "CRITICAL_ISSUE"
      }
    ],
    oldBadPractices: [
      {
        id: 2,
        title: "Missing error handling",
        description: "Error handling is missing in async functions.",
        state: "FIXED"
      }
    ],
    badPracticeSummary: "We found 1 issue in this pull request. Previous issues have been fixed."
  }
};
