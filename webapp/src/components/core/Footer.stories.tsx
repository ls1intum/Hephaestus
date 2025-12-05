import type { Meta, StoryObj } from "@storybook/react";
import Footer from "./Footer";

/**
 * Footer component displays navigation links and attribution information for the Hephaestus application.
 * It includes links to About, Releases, Privacy, Imprint, and project information.
 * In preview deployments, an additional Storybook link is displayed for easy access to the component library.
 */
const meta = {
	component: Footer,
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "desktop" },
		docs: {
			description: {
				component:
					"A responsive footer providing navigation and attribution information for the Hephaestus application. In preview deployments, includes a link to the bundled Storybook component library.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof Footer>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default footer component with all links displayed.
 */
export const Default: Story = {};

/**
 * Footer component in a mobile viewport.
 * Links are stacked vertically for better mobile readability.
 */
export const Mobile: Story = {
	parameters: {
		viewport: { defaultViewport: "mobile1" },
		docs: {
			description: {
				story:
					"Footer layout optimized for mobile screens with stacked link presentation.",
			},
		},
	},
};

/**
 * Footer component in a desktop viewport.
 * Links are displayed horizontally for efficient space utilization.
 */
export const Desktop: Story = {
	parameters: {
		viewport: { defaultViewport: "desktop" },
		docs: {
			description: {
				story:
					"Footer layout for desktop screens with horizontal link presentation and right-aligned attribution.",
			},
		},
	},
};
