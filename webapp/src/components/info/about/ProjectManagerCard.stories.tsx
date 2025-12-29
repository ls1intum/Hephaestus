import type { Meta, StoryObj } from "@storybook/react";
import { ProjectManagerCard } from "./ProjectManagerCard";

/**
 * ProjectManagerCard component for displaying project manager information.
 * Features avatar, contact details, and professional description with external links.
 */
const meta = {
	component: ProjectManagerCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	argTypes: {
		projectManager: {
			description: "Project manager data object containing profile information",
		},
	},
} satisfies Meta<typeof ProjectManagerCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default project manager card with real project architect information.
 */
export const Default: Story = {
	args: {
		projectManager: {
			id: 5898705,
			login: "felixtjdietrich",
			name: "Felix T.J. Dietrich",
			title: "Project Architect & Vision Lead",
			description:
				"Forging Hephaestus from concept to reality, Felix combines technical mastery with a passion for creating tools that empower software teams to achieve their full potential through data-driven insights and collaborative learning.",
			avatarUrl: "https://avatars.githubusercontent.com/u/5898705",
			htmlUrl: "https://github.com/felixtjdietrich",
			websiteUrl: "https://aet.cit.tum.de/people/dietrich/",
		},
	},
};

/**
 * Project manager card with placeholder data for design testing.
 */
export const Placeholder: Story = {
	args: {
		projectManager: {
			id: 12345,
			login: "techleader",
			name: "Alex Johnson",
			title: "Senior Engineering Manager",
			description:
				"Leading innovative development teams with over 10 years of experience in software architecture, team building, and strategic technology planning. Passionate about creating inclusive environments where developers thrive.",
			avatarUrl: "https://i.pravatar.cc/300?img=8",
			htmlUrl: "https://github.com/techleader",
			websiteUrl: "https://alexjohnson.dev",
		},
	},
};

/**
 * Project manager card with longer description text to test layout.
 */
export const LongDescription: Story = {
	args: {
		projectManager: {
			id: 67890,
			login: "innovator",
			name: "Dr. Sarah Chen",
			title: "Chief Technology Officer & Research Director",
			description:
				"With a Ph.D. in Computer Science and 15+ years in the industry, Sarah leads cutting-edge research initiatives while managing large-scale engineering teams. Her expertise spans artificial intelligence, distributed systems, and human-computer interaction. She is passionate about bridging the gap between academic research and practical software solutions that make a real impact on developer productivity and team collaboration.",
			avatarUrl: "https://i.pravatar.cc/300?img=5",
			htmlUrl: "https://github.com/innovator",
			websiteUrl: "https://sarahchen.research.com",
		},
	},
};
