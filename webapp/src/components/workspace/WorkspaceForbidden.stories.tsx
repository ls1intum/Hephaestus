import type { Meta, StoryObj } from "@storybook/react";
import { WorkspaceForbidden } from "./WorkspaceForbidden";

/**
 * Error state for workspace authorization failures (HTTP 403).
 * Use in route error boundaries when the user lacks permission to access a workspace.
 */
const meta = {
	component: WorkspaceForbidden,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		slug: {
			control: "text",
			description: "The workspace slug that was denied access",
		},
	},
} satisfies Meta<typeof WorkspaceForbidden>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Generic forbidden message without workspace identification. */
export const Default: Story = {};

/** Forbidden message with the workspace slug displayed. */
export const WithSlug: Story = {
	args: {
		slug: "private-workspace",
	},
};
