import type { Meta, StoryObj } from "@storybook/react";
import { GitHubLabel } from "./GitHubLabel";

/**
 * Component for displaying GitHub-style labels with customizable text and colors.
 * Replicates the appearance of labels as seen on GitHub issues and PRs.
 */
const meta = {
  title: "Practices/Components/GitHubLabel",
  component: GitHubLabel,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component: 'A component that displays GitHub-style labels with the same visual appearance as on github.com.',
      },
    },
  },
  argTypes: {
    label: {
      control: 'object',
      description: 'Label data with name, color, and id properties',
      table: {
        type: { 
          summary: 'LabelInfo', 
          detail: '{ id: number, name: string, color: string }' 
        },
      }
    },
  },
} satisfies Meta<typeof GitHubLabel>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Bug label example with standard GitHub red color.
 * Used to indicate issues related to bugs.
 */
export const Bug: Story = {
  args: {
    label: {
      id: 1,
      name: "bug",
      color: "d73a4a"
    }
  }
};

/**
 * Enhancement label example with light blue color.
 * Used to indicate feature enhancement requests.
 */
export const Enhancement: Story = {
  args: {
    label: {
      id: 2,
      name: "enhancement",
      color: "a2eeef"
    }
  }
};

/**
 * Documentation label example with blue color.
 * Used for issues related to documentation updates.
 */
export const Documentation: Story = {
  args: {
    label: {
      id: 3,
      name: "documentation",
      color: "0075ca"
    }
  }
};

/**
 * Good first issue label example with purple color.
 * Used to indicate issues suitable for newcomers.
 */
export const GoodFirstIssue: Story = {
  args: {
    label: {
      id: 4,
      name: "good first issue",
      color: "7057ff"
    }
  }
};
