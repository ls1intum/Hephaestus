import type { Meta, StoryObj } from "@storybook/react";
import { LandingFeaturesSection } from "./LandingFeaturesSection";

/**
 * Features section component that renders the practice-aware loop as a
 * three-stage pipeline: Detect → Coach → Reflect.
 */
const meta = {
	component: LandingFeaturesSection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The features section renders the practice-aware loop as a three-stage pipeline (Detect → Coach → Reflect) and frames each stage by the channels and surfaces it serves.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof LandingFeaturesSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default features section: Detect → Coach → Reflect.
 */
export const Default: Story = {};
