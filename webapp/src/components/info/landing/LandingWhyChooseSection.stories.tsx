import type { Meta, StoryObj } from "@storybook/react";
import { LandingWhyChooseSection } from "./LandingWhyChooseSection";

const meta = {
	component: LandingWhyChooseSection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The Why Choose section presents the core benefits of Hephaestus through a combination of visual storytelling and feature highlights, helping users understand the platform's unique value.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof LandingWhyChooseSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
