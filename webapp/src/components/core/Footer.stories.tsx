import type { Meta, StoryObj } from "@storybook/react";
import Footer from "./Footer";

/**
 * Minimal footer with navigation links and attribution.
 * Shows build info (branch, commit, deployed at) only for preview deployments.
 * Version is NOT shown here - it's displayed in the Header.
 */
const meta = {
	component: Footer,
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "desktop" },
	},
	tags: ["autodocs"],
	argTypes: {
		buildInfo: {
			control: "object",
			description: "Build metadata for preview deployments",
		},
	},
} satisfies Meta<typeof Footer>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default production/local state - clean footer with no build info.
 */
export const Default: Story = {
	args: {
		buildInfo: undefined,
	},
};

/**
 * Preview deployment with full build metadata for debugging.
 */
export const Preview: Story = {
	args: {
		buildInfo: {
			branch: "feat/new-footer-design",
			commit: "a1b2c3d4e5f6g7h8i9j0",
			deployedAt: "2024-12-14T17:44:00Z",
		},
	},
};

/**
 * Preview with only branch and commit (no deployment time).
 */
export const PreviewNoTime: Story = {
	args: {
		buildInfo: {
			branch: "fix/mobile-layout",
			commit: "9876543",
		},
	},
};

/**
 * Mobile layout - build info hidden on small screens.
 */
export const Mobile: Story = {
	args: {
		buildInfo: {
			branch: "fix/mobile-layout",
			commit: "9876543",
			deployedAt: "2024-12-14T10:00:00Z",
		},
	},
	globals: {
		viewport: {
			value: "mobile1",
			isRotated: false,
		},
	},
};
