import type { Meta, StoryObj } from "@storybook/react";
import { GithubBadge } from "./GithubBadge";

const meta = {
  title: "Profile/GithubBadge",
  component: GithubBadge,
  parameters: {
    layout: "centered",
  },
  tags: ["autodocs"],
} satisfies Meta<typeof GithubBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    label: "enhancement",
    color: "0E8A16",
  },
};

export const WithLink: Story = {
  args: {
    label: "bug",
    color: "d73a4a",
    href: "https://github.com/ls1intum/Artemis/issues?q=is%3Aissue+label%3Abug",
    tooltipText: "View all bug issues",
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
    tooltipText: "Example of GitHub's teal label style",
  },
};
