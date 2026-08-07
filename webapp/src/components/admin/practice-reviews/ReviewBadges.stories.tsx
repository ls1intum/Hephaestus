import type { Meta, StoryObj } from "@storybook/react-vite";
import { ClaimCurrentnessAlert } from "./ReviewBadges";

const meta = {
	title: "Admin/Practice reviews/Claim currentness alert",
	component: ClaimCurrentnessAlert,
	args: { currentness: "STALE" },
} satisfies Meta<typeof ClaimCurrentnessAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Stale: Story = {};

export const Unverifiable: Story = { args: { currentness: "UNVERIFIABLE" } };
