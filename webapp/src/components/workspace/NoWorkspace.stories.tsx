import type { Meta, StoryObj } from "@storybook/react";
import { NoWorkspace } from "./NoWorkspace";

/**
 * Empty state component for users without workspace membership.
 * Use when a user is authenticated but has no workspaces,
 * typically shown after login or when all memberships have been revoked.
 */
const meta = {
	component: NoWorkspace,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Empty-state screen shown when a user has no workspace membership.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof NoWorkspace>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default presentation for users without any workspace membership.
 * Use when redirecting authenticated users who have no associated workspaces.
 */
export const Default: Story = {};
