import type { Meta, StoryObj } from "@storybook/react";
import { expect, userEvent } from "storybook/test";
import { LandingFaqSection } from "./LandingFaqSection";

const meta = {
	component: LandingFaqSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof LandingFaqSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Can the feedback be wrong?" }));
		await expect(canvas.getByText(/it can miss context/)).toBeVisible();
	},
};
