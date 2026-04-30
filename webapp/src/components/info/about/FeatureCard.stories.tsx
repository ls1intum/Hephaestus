import type { Meta, StoryObj } from "@storybook/react";
import { MessageCircle, ScanSearch, TrendingUp, Zap } from "lucide-react";
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
 * Practice detection feature card.
 */
export const Default: Story = {
	args: {
		feature: {
			icon: ScanSearch,
			badge: "Detect",
			title: "Practice Detection",
			description: "Evaluate contributions against your project's practice catalog",
			content:
				"An AI agent evaluates each contribution against workspace-defined practices. Findings include a verdict, severity, evidence, and tailored guidance. Contributors can mark findings as applied, disputed, or not applicable.",
		},
	},
};

/**
 * Adaptive coaching feature card.
 */
export const AdaptiveCoaching: Story = {
	args: {
		feature: {
			icon: MessageCircle,
			badge: "Guide",
			title: "Adaptive Coaching",
			description: "Guidance adapts to each contributor's track record",
			content:
				"The system tracks each contributor's history per practice and instructs the agent to adapt accordingly. New contributors are guided toward concrete examples; repeat issues prompt direct coaching; improving contributors get prompts for reflection. Heph, the AI mentor, complements in-context findings with goal-setting and reflection.",
		},
	},
};

/**
 * Engagement and recognition feature card.
 */
export const EngagementRecognition: Story = {
	args: {
		feature: {
			icon: TrendingUp,
			badge: "Grow",
			title: "Engagement & Recognition",
			description: "Surface contribution activity over time",
			content:
				"A weekly leaderboard, leagues, and achievements surface contribution activity over time. Weekly Slack digests highlight standout contributors. Practice-aware recognition is on the roadmap.",
		},
	},
};

/**
 * Agent orchestration feature card.
 */
export const AgentOrchestration: Story = {
	args: {
		feature: {
			icon: Zap,
			badge: "Infrastructure",
			title: "Agent Orchestration",
			description: "Run AI agents in sandboxed containers",
			content:
				"Run AI agents (Claude Code, OpenCode) in sandboxed Docker containers with configurable LLM providers, resource limits, and concurrency caps.",
		},
	},
};
