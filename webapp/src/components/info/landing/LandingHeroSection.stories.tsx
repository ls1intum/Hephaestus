import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn } from "storybook/test";
import { LandingHeroSection } from "./LandingHeroSection";

const meta = {
	component: LandingHeroSection,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		onSignIn: fn(),
		onGoToDashboard: fn(),
		isSignedIn: false,
	},
} satisfies Meta<typeof LandingHeroSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { level: 1 })).toBeVisible();
		await expect(canvas.getByRole("link", { name: /View on GitHub/ })).toHaveAttribute(
			"href",
			"https://github.com/ls1intum/Hephaestus",
		);
		// One DOM serves the scattered and the stacked composition; a second copy means the
		// two layouts have drifted apart.
		await expect(canvas.getAllByText("Export reports to CSV")).toHaveLength(1);
	},
};

export const SignedIn: Story = {
	args: { isSignedIn: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: /dashboard/i })).toBeVisible();
	},
};

export const Mobile: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
};

export const Tablet: Story = {
	parameters: {
		viewport: { defaultViewport: "tablet" },
		chromatic: { viewports: [768] },
	},
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};

/**
 * Captured by `webapp/scripts/export-readme-assets.ts` for the README illustration, and
 * compared byte-for-byte by the `webapp-storybook` CI leg. Renaming this export or its
 * `data-readme-export` attribute breaks that leg, which `pnpm run check` does not run.
 */
export const ReadmeExport: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
	},
	render: (args) => (
		<div data-readme-export="landing-hero" className="mx-auto w-full max-w-[1280px] bg-background">
			<LandingHeroSection {...args} />
		</div>
	),
};
