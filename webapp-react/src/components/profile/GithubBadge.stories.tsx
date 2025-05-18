import type { Meta, StoryObj } from "@storybook/react";
import { GithubBadge } from "./GithubBadge";

/**
 * Component for displaying GitHub-style badges with customizable colors and labels.
 * Used for showing repository and PR metadata in a visual format.
 */
const meta = {
  component: GithubBadge,
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component: 'A badge component styled to match GitHub\'s visual design, suitable for displaying labels and metadata.',
      },
    },
  },
  argTypes: {
    label: {
      description: 'Text to display in the badge',
      control: 'text',
    },
    color: {
      description: 'Hex color code for the badge background (without # prefix)',
      control: 'color',
      table: {
        type: { summary: 'string' },
        defaultValue: { summary: 'undefined' },
      },
    },
    className: {
      description: 'Additional CSS classes to apply',
      control: 'text',
      table: {
        type: { summary: 'string' },
      },
    },
  },
  tags: ["autodocs"],
} satisfies Meta<typeof GithubBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default green enhancement badge example.
 * Use for positive feature-related annotations.
 */
export const Enhancement: Story = {
  args: {
    label: "enhancement",
    color: "0E8A16",
  },
};

/**
 * Bug badge example with GitHub's standard red color.
 * Use for issue and bug annotations.
 */
export const Bug: Story = {
  args: {
    label: "bug",
    color: "d73a4a",
  },
};

/**
 * Documentation badge example with blue color.
 * Use for documentation-related annotations.
 */
export const Documentation: Story = {
  args: {
    label: "documentation",
    color: "0075ca",
  },
};

export const WithoutColor: Story = {
  args: {
    label: "documentation",
  },
};

export const MultipleBadges: Story = {
  args: {
    label: "bug",
    color: "d73a4a"
  },
  render: () => (
    <div className="flex flex-row gap-2">
      <GithubBadge label="bug" color="d73a4a" />
      <GithubBadge label="enhancement" color="0E8A16" />
      <GithubBadge label="help wanted" color="008672" />
      <GithubBadge label="good first issue" color="7057ff" />
      <GithubBadge label="documentation" color="0075ca" />
    </div>
  ),
};

export const GithubTealLabel: Story = {
  args: {
    label: "teal label",
    color: "69feff", // This is the exact teal color from the example (r: 105, g: 254, b: 255)
  },
};
