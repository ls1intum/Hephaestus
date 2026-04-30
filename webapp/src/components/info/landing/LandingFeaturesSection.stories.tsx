import type { Meta, StoryObj } from "@storybook/react";
import { LandingFeaturesSection } from "./LandingFeaturesSection";

/**
 * The features section: four short paragraphs describing how Hephaestus
 * fits into the day.
 */
const meta = {
	component: LandingFeaturesSection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component: "Four short paragraphs about how Hephaestus fits into the day.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof LandingFeaturesSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Default features section. */
export const Default: Story = {};
