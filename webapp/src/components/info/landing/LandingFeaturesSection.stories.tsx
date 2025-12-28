import type { Meta, StoryObj } from "@storybook/react-vite";
import { LandingFeaturesSection } from "./LandingFeaturesSection";

/**
 * Features section component that showcases the key capabilities of Hephaestus
 * through visually appealing cards with icons and detailed descriptions.
 */
const meta = {
	component: LandingFeaturesSection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The features section highlights the main capabilities of Hephaestus through a grid of feature cards, each focusing on a specific aspect of the platform.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof LandingFeaturesSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default features section with two main feature cards:
 * Code Review Gamification and AI-Powered Mentorship.
 */
export const Default: Story = {};
