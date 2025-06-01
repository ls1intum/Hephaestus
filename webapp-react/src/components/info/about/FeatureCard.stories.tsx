import type { Meta, StoryObj } from "@storybook/react";
import { Code, Sparkles, Users, Zap } from "lucide-react";
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
			description:
				"Feature data object containing icon, badge, title, description, and content",
		},
	},
} satisfies Meta<typeof FeatureCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default feature card showcasing code review gamification.
 */
export const Default: Story = {
	args: {
		feature: {
			icon: Code,
			badge: "Core Feature",
			title: "Code Review Gamification",
			description: "Turning technical work into team growth",
			content:
				"Transform code reviews into engaging experiences with dynamic leaderboards, team competitions, and a structured league system that recognizes excellence and encourages participation from developers at all skill levels.",
		},
	},
};

/**
 * AI mentorship feature with sparkles icon.
 */
export const AIMentorship: Story = {
	args: {
		feature: {
			icon: Sparkles,
			badge: "Core Feature",
			title: "AI-Powered Mentorship",
			description: "Your team's personalized guide",
			content:
				"Receive contextual guidance through our AI mentor that provides personalized feedback, identifies growth opportunities, and helps team members develop their skills with practical insights that lead to measurable improvement.",
		},
	},
};

/**
 * Team collaboration feature with a different badge style.
 */
export const TeamCollaboration: Story = {
	args: {
		feature: {
			icon: Users,
			badge: "New Feature",
			title: "Team Collaboration Hub",
			description: "Connect and grow together",
			content:
				"Foster meaningful connections within your development team through shared challenges, peer mentoring opportunities, and collaborative learning experiences that strengthen both individual skills and team cohesion.",
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
