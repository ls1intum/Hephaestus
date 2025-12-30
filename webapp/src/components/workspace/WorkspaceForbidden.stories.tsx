import type { Meta, StoryObj } from "@storybook/react";
import { WorkspaceForbidden } from "./WorkspaceForbidden";

/**
 * WorkspaceForbidden displays a friendly 403 error state when a user
 * does not have permission to access a workspace.
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
 */
export const Default: Story = {};

/**
 * Shows the specific slug that access was denied to.
 */
export const WithSlug: Story = {
	args: {
		slug: "private-workspace",
	},
};
