import type { Meta, StoryObj } from "@storybook/react";
import { GitHubLabel } from "./GitHubLabel";

const meta: Meta<typeof GitHubLabel> = {
  component: GitHubLabel,
  tags: ["autodocs"],
};

export default meta;

type Story = StoryObj<typeof GitHubLabel>;

export const Default: Story = {
  args: {
    label: {
      id: 1,
      name: "bug",
      color: "d73a4a"
    }
  }
};

export const Enhancement: Story = {
  args: {
    label: {
      id: 2,
      name: "enhancement",
      color: "a2eeef"
    }
  }
};

export const Documentation: Story = {
  args: {
    label: {
      id: 3,
      name: "documentation",
      color: "0075ca"
    }
  }
};
