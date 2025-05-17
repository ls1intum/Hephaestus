import type { Meta, StoryObj } from "@storybook/react";
import { BadPracticeLegendCard } from "./BadPracticeLegendCard";

const meta: Meta<typeof BadPracticeLegendCard> = {
  component: BadPracticeLegendCard,
  tags: ["autodocs"],
};

export default meta;

type Story = StoryObj<typeof BadPracticeLegendCard>;

export const Default: Story = {
  args: {}
};
