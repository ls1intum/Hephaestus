import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { ClaimCurrentnessAlert } from "./ReviewBadges";

const meta = {
	title: "Admin/Practice reviews/Claim currentness alert",
	component: ClaimCurrentnessAlert,
	args: { currentness: "STALE" },
} satisfies Meta<typeof ClaimCurrentnessAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Stale: Story = {
	play: async ({ canvasElement }) => {
		await expect(
			within(canvasElement).getByText("This result uses older review rules"),
		).toBeVisible();
	},
};

export const Unverifiable: Story = {
	args: { currentness: "UNVERIFIABLE" },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).getByText("Currentness is unknown")).toBeVisible();
	},
};
