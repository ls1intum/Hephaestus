import type { Meta, StoryObj } from "@storybook/react";
import { IssueCard } from "./IssueCard";

/**
 * Card component for displaying GitHub issues or pull requests with metadata.
 * Shows details such as title, repository, state, labels, and creation date.
 * Used primarily in user profile pages to display contribution activity.
 */
const meta = {
  title: "Profile/IssueCard",
  component: IssueCard,
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component: 'A card that displays GitHub pull request or issue information with metadata like state, labels, and repository.',
      },
    },
  },
  argTypes: {
    isLoading: {
      description: 'Whether the card is in a loading state',
      control: 'boolean',
    },
    title: {
      description: 'Title of the issue or pull request',
      control: 'text',
    },
    number: {
      description: 'Issue or pull request number',
      control: 'number',
    },
    additions: {
      description: 'Number of added lines (for PRs)',
      control: 'number',
    },
    deletions: {
      description: 'Number of deleted lines (for PRs)',
      control: 'number',
    },
    htmlUrl: {
      description: 'Link to the GitHub issue or PR',
      control: 'text',
    },
    repositoryName: {
      description: 'Name of the repository',
      control: 'text',
    },
    createdAt: {
      description: 'Creation date (ISO string)',
      control: 'text', 
    },
    state: {
      description: 'Current state of the issue/PR',
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
    pullRequestLabels: {
      description: 'Labels attached to the issue/PR',
      control: 'object',
    }
  },
  tags: ["autodocs"],
} satisfies Meta<typeof IssueCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * An open pull request with enhancement and frontend labels.
 */
export const OpenPR: Story = {
  args: {
    isLoading: false,
    title: "Implement new dashboard features",
    number: 42,
    additions: 150,
    deletions: 50,
    htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
    repositoryName: "Hephaestus",
    createdAt: new Date().toISOString(),
    state: "OPEN",
    isDraft: false,
    isMerged: false,
    pullRequestLabels: [
      { id: 1, name: "enhancement", color: "0E8A16" },
      { id: 2, name: "frontend", color: "FBCA04" }
    ],
  },
};

/**
 * A draft pull request for major refactoring work that's still in progress.
 */
export const DraftPR: Story = {
  args: {
    isLoading: false,
    title: "WIP: Refactor authentication module",
    number: 87,
    additions: 320,
    deletions: 280,
    htmlUrl: "https://github.com/ls1intum/Artemis/pull/87",
    repositoryName: "Artemis",
    createdAt: new Date().toISOString(),
    state: "OPEN",
    isDraft: true,
    isMerged: false,
    pullRequestLabels: [
      { id: 3, name: "refactoring", color: "D93F0B" },
      { id: 4, name: "security", color: "5319E7" }
    ],
  },
};

/**
 * A merged pull request that fixed a critical bug with high priority.
 */
export const MergedPR: Story = {
  args: {
    isLoading: false,
    title: "Fix critical security vulnerability",
    number: 103,
    additions: 25,
    deletions: 5,
    htmlUrl: "https://github.com/ls1intum/Athena/pull/103",
    repositoryName: "Athena",
    createdAt: new Date().toISOString(),
    state: "CLOSED",
    isDraft: false,
    isMerged: true,
    pullRequestLabels: [
      { id: 5, name: "bug", color: "B60205" },
      { id: 6, name: "priority", color: "C2E0C6" }
    ],
  },
};

/**
 * A closed pull request that was not merged, typically indicating the proposed changes were rejected.
 */
export const ClosedPR: Story = {
  args: {
    isLoading: false,
    title: "Add experimental feature (closed without merge)",
    number: 75,
    additions: 450,
    deletions: 0,
    htmlUrl: "https://github.com/ls1intum/ExampleRepo/pull/75",
    repositoryName: "ExampleRepo",
    createdAt: new Date().toISOString(),
    state: "CLOSED",
    isDraft: false,
    isMerged: false,
    pullRequestLabels: [
      { id: 7, name: "wontfix", color: "000000" },
      { id: 8, name: "experimental", color: "C5DEF5" }
    ],
  },
};

export const Loading: Story = {
  args: {
    isLoading: true,
  },
};
