import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { ClaimStatusAlert } from "./ReviewBadges";

const meta = {
	title: "Admin/Practice reviews/Claim status alert",
	component: ClaimStatusAlert,
	args: { status: "STALE" },
} satisfies Meta<typeof ClaimStatusAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Stale: Story = {
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).getByText("This result is outdated")).toBeVisible();
	},
};

export const Unverifiable: Story = {
	args: { status: "UNVERIFIABLE" },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).getByText("This claim cannot be verified")).toBeVisible();
	},
};
