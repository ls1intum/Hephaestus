import type { Meta, StoryObj } from "@storybook/react";
import { IssueCard } from "./IssueCard";

const meta = {
  title: "Profile/IssueCard",
  component: IssueCard,
  parameters: {
    layout: "centered",
  },
  tags: ["autodocs"],
} satisfies Meta<typeof IssueCard>;

export default meta;
type Story = StoryObj<typeof meta>;

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
