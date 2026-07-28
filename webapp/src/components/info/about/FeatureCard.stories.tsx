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
			title: "Practice feedback on your work",
			description: "What was done well, what could be better, and a way to get there",
			content:
				"Hephaestus reviews pull requests and issues in your GitHub and GitLab repositories against real engineering practices and posts its feedback right where the work happens. You can act on it, push back with a reason, or let it pass.",
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
			title: "Heph, your AI mentor",
			description: "A mentor chat grounded in your repository activity",
			content:
				"Heph knows your recent issues, commits, reviews, and pull requests, so its answers start from your actual work. Ask it about your changes, reflect on your week, or get a suggestion for what to do next, in the app or in Slack.",
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
			description: "Mentoring where your team already talks",
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
				"Workspace administrators can enable achievements and a weekly leaderboard of review activity. Both are off by default, and neither affects the feedback Hephaestus gives you.",
		},
	},
};
