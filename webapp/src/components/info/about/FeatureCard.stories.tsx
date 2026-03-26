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
				"An AI agent evaluates each PR against workspace-defined practices. Findings include a verdict, severity, evidence, and tailored guidance. Contributors can mark findings as applied, disputed, or not applicable.",
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
				"The system tracks each contributor's history per practice. New contributors get concrete examples. Repeat issues get direct coaching. As competence grows, guidance fades toward reflection.",
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
			description: "Make good practices visible across the team",
			content:
				"Leaderboards, leagues, and achievements track engagement over time. Weekly Slack digests highlight standout contributors. The AI mentor (Heph) supports reflection and goal-setting.",
		},
	},
};

/**
 * Performance analytics with lightning icon.
 */
export const PerformanceAnalytics: Story = {
	args: {
		feature: {
			icon: Zap,
			badge: "Analytics",
			title: "Performance Insights",
			description: "Data-driven development insights",
			content:
				"Gain deep visibility into your team's development patterns, identify bottlenecks, and discover optimization opportunities through comprehensive analytics and intelligent reporting.",
		},
	},
};
