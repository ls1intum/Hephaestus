import type { Meta, StoryObj } from "@storybook/react";
import { WorkspaceNotFound } from "./WorkspaceNotFound";

/**
 * Error state for workspace resolution failures (HTTP 404).
 * Use in route notFoundComponent when a workspace slug does not exist.
 */
const meta = {
	component: WorkspaceNotFound,
	parameters: {
		layout: "centered",
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

/** Generic not-found message without workspace identification. */
export const Default: Story = {};

/** Not-found message with the workspace slug displayed. */
export const WithSlug: Story = {
	args: {
		slug: "my-team-workspace",
	},
};
