import type { Meta, StoryObj } from "@storybook/react";
import { LandingFeaturesSection } from "./LandingFeaturesSection";

/**
 * Animated overview of the feedback cycle and the delivery options available today.
 */
const meta = {
	component: LandingFeaturesSection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The section follows project work through practice review, feedback, and developer choice, then shows where feedback can appear today.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof LandingFeaturesSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default feedback cycle with current GitHub, GitLab, web app, and Slack delivery paths.
 */
export const Default: Story = {};
