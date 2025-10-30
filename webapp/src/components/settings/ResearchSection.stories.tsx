import type { Meta, StoryObj } from "@storybook/react";
import { ResearchSection } from "./ResearchSection";

/**
 * ResearchSection component for managing research and analytics preferences.
 * Allows users to opt out of PostHog analytics for research purposes.
 */
const meta = {
	component: ResearchSection,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		researchOptOut: {
			control: "boolean",
			description: "Whether the user has opted out of research analytics",
		},
		onToggleResearchOptOut: {
			description: "Callback when research opt-out setting is changed",
		},
		isLoading: {
			control: "boolean",
			description: "Whether the component is in loading state",
		},
	},
	args: {
		researchOptOut: false,
		isLoading: false,
		onToggleResearchOptOut: () => {},
	},
} satisfies Meta<typeof ResearchSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state with research analytics enabled (not opted out).
 */
export const Default: Story = {
	args: {
		researchOptOut: false,
	},
};

/**
 * State when user has opted out of research analytics.
 */
export const OptedOut: Story = {
	args: {
		researchOptOut: true,
	},
};

/**
 * Loading state while settings are being fetched or updated.
 */
export const Loading: Story = {
	args: {
		isLoading: true,
	},
};
