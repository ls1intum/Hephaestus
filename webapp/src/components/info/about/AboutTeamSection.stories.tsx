import type { Meta, StoryObj } from "@storybook/react";
import { AboutTeamSection } from "./AboutTeamSection";

/**
 * Team section component that displays the project manager and contributors
 * with proper loading and error handling.
 */
const meta = {
	component: AboutTeamSection,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		projectManager: {
			description: "Project manager information",
		},
		contributors: {
			description: "List of project contributors",
		},
		isContributorsLoading: {
			control: "boolean",
			description: "Whether contributors are currently loading",
		},
		isContributorsError: {
			control: "boolean",
			description: "Whether there was an error loading contributors",
		},
	},
} satisfies Meta<typeof AboutTeamSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view showing project manager and contributors
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
		contributors: [
			{
				id: 12345678,
				name: "Alice Developer",
				login: "contributor1",
				avatarUrl: "https://avatars.githubusercontent.com/u/12345678",
				htmlUrl: "https://github.com/contributor1",
			},
			{
				id: 87654321,
				name: "Bob Builder",
				login: "contributor2",
				avatarUrl: "https://avatars.githubusercontent.com/u/87654321",
				htmlUrl: "https://github.com/contributor2",
			},
		],
		isContributorsLoading: false,
		isContributorsError: false,
	},
};

/**
 * Loading state while contributors are being fetched
 */
export const IsLoading: Story = {
	args: {
		...Default.args,
		isContributorsLoading: true,
	},
};

/**
 * Error state when contributors cannot be loaded
 */
export const IsError: Story = {
	args: {
		...Default.args,
		isContributorsError: true,
	},
};

/**
 * Empty state when there are no contributors
 */
export const NoContributors: Story = {
	args: {
		...Default.args,
		contributors: [],
	},
};
