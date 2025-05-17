import type { Meta, StoryObj } from "@storybook/react";
import { BadPracticeCard } from "./BadPracticeCard";

const meta: Meta<typeof BadPracticeCard> = {
  tags: ["autodocs"],
  argTypes: {
    onResolveBadPracticeAsFixed: { action: "resolved as fixed" }
  }
};

export default meta;

type Story = StoryObj<typeof BadPracticeCard>;

export const GoodPractice: Story = {
  args: {
    id: 1,
    title: "Avoid using any type",
    description: "Using the any type defeats the purpose of TypeScript.",
    state: "GOOD_PRACTICE"
  }
};

export const Fixed: Story = {
  args: {
    id: 2,
    title: "Avoid using any type",
    description: "Using the any type defeats the purpose of TypeScript.",
    state: "FIXED"
  }
};

export const CriticalIssue: Story = {
  args: {
    id: 3,
    title: "Avoid using any type",
    description: "Using the any type defeats the purpose of TypeScript.",
    state: "CRITICAL_ISSUE"
  }
};

export const NormalIssue: Story = {
  args: {
    id: 4,
    title: "Avoid using any type",
    description: "Using the any type defeats the purpose of TypeScript.",
    state: "NORMAL_ISSUE"
  }
};

export const MinorIssue: Story = {
  args: {
    id: 5,
    title: "Avoid using any type",
    description: "Using the any type defeats the purpose of TypeScript.",
    state: "MINOR_ISSUE"
  }
};

export const WontFix: Story = {
  args: {
    id: 6,
    title: "Avoid using any type",
    description: "Using the any type defeats the purpose of TypeScript.",
    state: "WONT_FIX"
  }
};

export const Wrong: Story = {
  args: {
    id: 7,
    title: "Avoid using any type",
    description: "Using the any type defeats the purpose of TypeScript.",
    state: "WRONG"
  }
};

export const WithResolutionControls: Story = {
  args: {
    id: 8,
    title: "Avoid using any type",
    description: "Using the any type defeats the purpose of TypeScript.",
    state: "NORMAL_ISSUE",
    currUserIsDashboardUser: true
  }
};
