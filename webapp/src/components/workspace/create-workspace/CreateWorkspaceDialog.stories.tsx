import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { fn } from "storybook/test";
import { CreateWorkspaceDialog } from "./CreateWorkspaceDialog";

const queryClient = new QueryClient({
	defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
});

/**
 * Multi-step wizard dialog for creating GitLab workspaces.
 * Guides users through connection, group selection, and configuration.
 */
const meta: Meta<typeof CreateWorkspaceDialog> = {
	component: CreateWorkspaceDialog,
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Three-step wizard: (1) Enter GitLab URL and PAT, (2) Select a group, (3) Configure workspace name and slug.",
			},
		},
	},
	argTypes: {
		open: {
			control: "boolean",
			description: "Controls dialog visibility",
		},
		onOpenChange: {
			description: "Callback when dialog open state changes",
		},
	},
	args: {
		onOpenChange: fn(),
	},
	decorators: [
		(Story) =>
			React.createElement(
				QueryClientProvider,
				{ client: queryClient },
				React.createElement("div", { className: "p-6 max-w-lg" }, React.createElement(Story)),
			),
	],
};

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Dialog open — shows step 1 (Connect to GitLab) by default.
 */
export const Open: Story = {
	args: { open: true },
};

/**
 * Dialog closed — not visible.
 */
export const Closed: Story = {
	args: { open: false },
};
