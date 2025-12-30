import type { Meta, StoryObj } from "@storybook/react";
import { WorkspaceNotFound } from "./WorkspaceNotFound";

/**
 * Error state component for workspace 404 scenarios.
 * Use when a workspace slug cannot be resolved,
 * typically in route loaders or workspace-dependent layouts.
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
 * Use when the workspace slug is unknown or not relevant.
 */
export const Default: Story = {};

/**
 * Shows the specific slug that wasn't found.
 * Use when you want to display which workspace the user tried to access.
 */
export const WithSlug: Story = {
	args: {
		slug: "my-team-workspace",
	},
};
