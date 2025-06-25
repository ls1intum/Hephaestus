import type { Meta, StoryObj } from "@storybook/react";
import { FileQuestion, GitPullRequest } from "lucide-react";
import { Button } from "../ui/button";
import { EmptyState } from "./EmptyState";

const meta: Meta<typeof EmptyState> = {
	title: "Shared/EmptyState",
	component: EmptyState,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
};

export default meta;
type Story = StoryObj<typeof EmptyState>;

/**
 * Default empty state with icon, title and description
 */
export const Default: Story = {
	args: {
		icon: FileQuestion,
		title: "No content found",
		description: "There is no content to display at the moment.",
	},
};

/**
 * Empty state with an action button
 */
export const WithAction: Story = {
	args: {
		icon: GitPullRequest,
		title: "No pull requests",
		description: "There are no pull requests to display.",
		action: <Button>Create Pull Request</Button>,
	},
};

/**
 * Custom height empty state
 */
export const CustomHeight: Story = {
	args: {
		icon: FileQuestion,
		title: "No content found",
		description: "There is no content to display at the moment.",
		height: "h-80",
	},
};
