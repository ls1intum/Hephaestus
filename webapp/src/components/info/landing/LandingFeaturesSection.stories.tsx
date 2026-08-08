import type { Meta, StoryObj } from "@storybook/react";
import { LandingFeaturesSection } from "./LandingFeaturesSection";

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

export const Default: Story = {};
