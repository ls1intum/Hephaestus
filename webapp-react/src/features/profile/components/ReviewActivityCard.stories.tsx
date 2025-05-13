import type { Meta, StoryObj } from "@storybook/react";
import { ReviewActivityCard } from "./ReviewActivityCard";

const meta = {
  title: "Profile/ReviewActivityCard",
  component: ReviewActivityCard,
  parameters: {
    layout: "centered",
  },
  tags: ["autodocs"],
} satisfies Meta<typeof ReviewActivityCard>;

export default meta;
type Story = StoryObj<typeof meta>;

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

export const Loading: Story = {
  args: {
    isLoading: true,
  },
};
