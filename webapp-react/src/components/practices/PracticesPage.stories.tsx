import type { Meta, StoryObj } from "@storybook/react";
import { PracticesPage } from "./PracticesPage";
import type { Activity } from "@/api/types.gen";

const meta: Meta<typeof PracticesPage> = {
  component: PracticesPage,
  tags: ["autodocs"],
  argTypes: {
    onDetectBadPractices: { action: "detect bad practices clicked" }
  }
};

export default meta;

type Story = StoryObj<typeof PracticesPage>;

const mockActivityData: Activity = {
  pullRequests: [
    {
      id: 1,
      number: 12,
      title: "Add feature X",
      state: "OPEN",
      isDraft: false,
      isMerged: false,
      labels: [
        { id: 1, name: "bug", color: "f00000" },
        { id: 2, name: "enhancement", color: "008000" }
      ],
      repository: {
        id: 1,
        name: "Artemis",
        nameWithOwner: "ls1intum/Artemis",
        htmlUrl: "http://example.com/repo",
        description: "Artemis learning platform"
      },
      additions: 10,
      deletions: 5,
      htmlUrl: "http://example.com/pr/12",
      createdAt: "2024-01-01",
      badPracticeSummary: "We found 2 bad practices in this pull request. Please fix them.",
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
      ]
    },
    {
      id: 2,
      number: 13,
      title: "Fix bug in user authentication",
      state: "OPEN",
      isDraft: false,
      isMerged: false,
      labels: [
        { id: 3, name: "bug", color: "f00000" }
      ],
      repository: {
        id: 1,
        name: "Artemis",
        nameWithOwner: "ls1intum/Artemis",
        htmlUrl: "http://example.com/repo",
        description: "Artemis learning platform"
      },
      additions: 5,
      deletions: 2,
      htmlUrl: "http://example.com/pr/13",
      createdAt: "2024-01-02",
      badPracticeSummary: "Great work! We found 1 good practice in your code.",
      badPractices: [
        {
          id: 3,
          title: "Good error handling",
          description: "The error handling is well implemented.",
          state: "GOOD_PRACTICE"
        }
      ]
    }
  ]
};

export const Default: Story = {
  args: {
    activityData: mockActivityData,
    isLoading: false,
    isDetectingBadPractices: false,
    username: "johndoe",
    currUserIsDashboardUser: true
  }
};

export const Loading: Story = {
  args: {
    isLoading: true,
    isDetectingBadPractices: false,
    username: "johndoe",
    currUserIsDashboardUser: true
  }
};

export const DetectingBadPractices: Story = {
  args: {
    activityData: mockActivityData,
    isLoading: false,
    isDetectingBadPractices: true,
    username: "johndoe",
    currUserIsDashboardUser: true
  }
};

export const NoBadPractices: Story = {
  args: {
    activityData: {
      pullRequests: []
    },
    isLoading: false,
    isDetectingBadPractices: false,
    username: "johndoe",
    currUserIsDashboardUser: false
  }
};

export const OtherUserView: Story = {
  args: {
    activityData: mockActivityData,
    isLoading: false,
    isDetectingBadPractices: false,
    username: "janedoe",
    currUserIsDashboardUser: false
  }
};
