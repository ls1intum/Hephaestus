import type { Meta, StoryObj } from "@storybook/react";
import AIMentor from "./AIMentor";

const meta = {
  component: AIMentor,
  parameters: {
    layout: "centered",
  },
  tags: ["autodocs"],
} satisfies Meta<typeof AIMentor>;

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