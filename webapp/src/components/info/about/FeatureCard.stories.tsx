import type { Meta, StoryObj } from "@storybook/react";
import { Code, MessageSquare, Sparkles, Trophy } from "lucide-react";
import { FeatureCard } from "./FeatureCard";

/**
 * FeatureCard component for displaying feature information with icons and badges.
 * Used to highlight key platform capabilities with consistent visual styling.
 */
const meta = {
	component: FeatureCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	argTypes: {
		feature: {
			description: "Feature data object containing icon, badge, title, description, and content",
		},
	},
} satisfies Meta<typeof FeatureCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default feature card, as used on the about page for practice feedback.
 */
export const Default: Story = {
	args: {
		feature: {
			icon: Code,
			badge: "Core feature",
			title: "Practice feedback",
			description: "Specific feedback on how the work was done",
			content:
				"Hephaestus reviews pull requests, merge requests, and issues against the practices a workspace has chosen. Each piece of feedback points to evidence in the work and suggests what to try next.",
		},
	},
};

/**
 * The mentor feature, as used on the about page.
 */
export const AIMentorship: Story = {
	args: {
		feature: {
			icon: Sparkles,
			badge: "Core feature",
			title: "Chat with Heph",
			description: "Talk through feedback and recent work",
			content:
				"Heph can use recent project activity, feedback you have received, and selected Slack messages or Outline documents as context. Developers can chat in the web app or, when connected, in Slack.",
		},
	},
};

/**
 * A shipped integration, showing how a non-core badge reads.
 */
export const SlackIntegration: Story = {
	args: {
		feature: {
			icon: MessageSquare,
			badge: "Integration",
			title: "Heph in Slack",
			description: "Talk with Heph in a direct message",
			content:
				"Talk to Heph in a Slack DM, and let a workspace administrator decide whether monitored team channels are available as context. Channel monitoring starts only after a visible announcement in the channel.",
		},
	},
};

/**
 * An optional feature, showing a longer badge and a warm icon.
 */
export const Recognition: Story = {
	args: {
		feature: {
			icon: Trophy,
			badge: "Optional",
			title: "Achievements and leaderboard",
			description: "Recognition features a workspace can switch on",
			content:
				"Workspace administrators can enable achievements and a weekly leaderboard of review activity. Neither feature affects the practice feedback Hephaestus gives developers.",
		},
	},
};
