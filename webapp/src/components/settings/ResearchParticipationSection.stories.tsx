import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { ResearchParticipationSection } from "./ResearchParticipationSection";

/**
 * ResearchParticipationSection component for managing research consent
 * Highlights privacy safeguards while allowing opt-out
 */
const meta = {
	component: ResearchParticipationSection,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		participateInResearch: {
			control: "boolean",
			description: "Whether the user participates in research",
		},
		onToggleResearch: {
			description: "Callback when the research participation setting changes",
		},
		isLoading: {
			control: "boolean",
			description: "Whether the component is in loading state",
		},
	},
	args: {
		onToggleResearch: fn(),
	},
} satisfies Meta<typeof ResearchParticipationSection>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Default state with research participation enabled
 */
export const Enabled: Story = {
	args: {
		participateInResearch: true,
		isLoading: false,
	},
};

/**
 * Opt-out state showcasing the privacy badge
 */
export const OptedOut: Story = {
	args: {
		participateInResearch: false,
		isLoading: false,
	},
};

/**
 * Loading state with skeleton placeholders
 */
export const Loading: Story = {
	args: {
		participateInResearch: true,
		isLoading: true,
	},
};
