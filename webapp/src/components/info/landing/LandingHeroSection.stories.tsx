import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn } from "storybook/test";
import { HeroScene, LandingHeroSection } from "./LandingHeroSection";

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
 * Captured by `webapp/scripts/export-readme-assets.ts` for the README and the documentation site,
 * and compared byte-for-byte by the `webapp-storybook` CI leg. Renaming this export or its
 * `data-readme-export` attribute breaks that leg, which `bun run check` does not run.
 *
 * It renders the scene alone: a capture of the whole hero would put this page's headline inside
 * an image sitting next to that same headline.
 */
export const SceneExport: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
	},
	render: () => (
		<div data-readme-export="feedback-scene" className="w-[56rem] bg-background p-6">
			<HeroScene />
		</div>
	),
};
