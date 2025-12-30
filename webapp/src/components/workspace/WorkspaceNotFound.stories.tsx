import type { Meta, StoryObj } from "@storybook/react";
import { WorkspaceNotFound } from "./WorkspaceNotFound";

/**
 * WorkspaceNotFound displays a friendly 404 error state when a workspace
 * cannot be found. Provides navigation back to the home page.
 */
const meta = {
	component: WorkspaceNotFound,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Empty-state screen shown when a workspace slug does not exist (HTTP 404).",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		slug: {
			control: "text",
			description: "The workspace slug that was not found",
		},
	},
} satisfies Meta<typeof WorkspaceNotFound>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default presentation without a specific slug.
 */
export const Default: Story = {};

/**
 * Shows the specific slug that wasn't found.
 */
export const WithSlug: Story = {
	args: {
		slug: "my-team-workspace",
	},
};
