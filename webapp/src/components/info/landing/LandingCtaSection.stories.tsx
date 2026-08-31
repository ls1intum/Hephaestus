import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn } from "storybook/test";
import { LandingCtaSection } from "./LandingCtaSection";

const meta = {
	component: LandingCtaSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		onSignIn: fn(),
		onGoToDashboard: fn(),
	},
} satisfies Meta<typeof LandingCtaSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("link", { name: /Read the installation guide/ })).toBeNull();
		await expect(canvas.getByRole("button", { name: /dashboard/i })).toBeVisible();
	},
};

export const DarkMode: Story = {
	args: {
		isSignedIn: false,
	},
	globals: { theme: "dark" },
};
