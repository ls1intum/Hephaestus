import type { Meta, StoryObj } from "@storybook/react";
import { NoWorkspace } from "./NoWorkspace";

/**
 * Empty state for users without workspace membership.
 * Use when redirecting authenticated users who have no associated workspaces.
 */
const meta = {
	component: NoWorkspace,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
} satisfies Meta<typeof NoWorkspace>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Standard presentation for users with no workspace memberships. */
export const Default: Story = {};
