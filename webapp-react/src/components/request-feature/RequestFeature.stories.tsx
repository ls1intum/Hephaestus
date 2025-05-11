import type { Meta, StoryObj } from "@storybook/react";
import RequestFeature from "./RequestFeature";

const meta = {
  title: "Components/RequestFeature",
  component: RequestFeature,
  parameters: {
    layout: "centered",
  },
  tags: ["autodocs"],
} satisfies Meta<typeof RequestFeature>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    iconOnly: false,
  },
};

export const IconOnly: Story = {
  args: {
    iconOnly: true,
  },
};