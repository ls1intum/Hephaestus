import type { Meta, StoryObj } from "@storybook/react";
import { LandingFeaturesSection } from "./LandingFeaturesSection";

/**
 * Features section for the two main ways developers receive feedback.
 */
const meta = {
	component: LandingFeaturesSection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The features section distinguishes practice feedback on development work from conversations with Heph.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof LandingFeaturesSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default features section with cards for practice feedback and Heph.
 */
export const Default: Story = {};
