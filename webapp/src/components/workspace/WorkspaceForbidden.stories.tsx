import type { Meta, StoryObj } from "@storybook/react";
import { WorkspaceForbidden } from "./WorkspaceForbidden";

/**
 * Error state component for workspace 403 scenarios.
 * Use when a user lacks permissions to access a workspace,
 * typically shown after authentication succeeds but authorization fails.
 */
const meta = {
	component: WorkspaceForbidden,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Empty-state screen shown when user lacks permission to access a workspace (HTTP 403).",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		slug: {
			control: "text",
			description: "The workspace slug that access was denied to",
		},
	},
} satisfies Meta<typeof WorkspaceForbidden>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default presentation without a specific slug.
 * Use when the workspace slug is unknown or not relevant.
 */
export const Default: Story = {};

/**
 * Shows the specific slug that access was denied to.
 * Use when you want to display which workspace the user tried to access.
 */
export const WithSlug: Story = {
	args: {
		slug: "private-workspace",
	},
};
