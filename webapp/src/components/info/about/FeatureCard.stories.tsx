import type { Meta, StoryObj } from "@storybook/react";
import { Code, MessageSquare, Sparkles, Trophy } from "lucide-react";

import { FeatureCard } from "./FeatureCard";

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
