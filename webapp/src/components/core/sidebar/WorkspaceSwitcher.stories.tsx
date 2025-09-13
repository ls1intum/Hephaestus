import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { SidebarProvider } from "@/components/ui/sidebar";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

/**
 * UI component for switching between different workspaces, displaying the
 * current workspace and allowing selection of others. Supports keyboard shortcuts
 * with ⌘1, ⌘2, etc. on Mac or Ctrl+1, Ctrl+2, etc. on Windows/Linux to quickly switch between workspaces.
 */
const meta = {
	component: WorkspaceSwitcher,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Workspace switching component that displays the current workspace and allows switching between multiple workspaces. Supports cross-platform keyboard shortcuts (⌘1-9 on Mac, Ctrl+1-9 on Windows/Linux) for quick workspace switching.",
			},
		},
	},
	tags: ["autodocs"],
	args: {
		workspaces: [
			{
				name: "AET",
				logoUrl: "https://avatars.githubusercontent.com/u/11064260?s=200&v=4",
			},
		],
		activeWorkspace: {
			name: "AET",
			logoUrl: "https://avatars.githubusercontent.com/u/11064260?s=200&v=4",
		},
		onWorkspaceChange: fn(),
		onAddWorkspace: fn(),
	},
	argTypes: {
		workspaces: {
			control: "object",
			description: "List of available workspaces",
		},
		activeWorkspace: {
			control: "object",
			description: "Currently selected workspace",
		},
		onWorkspaceChange: {
			action: "workspace changed",
			description: "Callback fired when a workspace is selected",
		},
		onAddWorkspace: {
			action: "add workspace clicked",
			description: "Callback fired when the add workspace button is clicked",
		},
	},
	decorators: [
		(Story) => (
			<SidebarProvider className="min-h-0 w-[16rem] border border-border rounded-lg p-2 bg-sidebar">
				<Story />
			</SidebarProvider>
		),
	],
} satisfies Meta<typeof WorkspaceSwitcher>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view with a single workspace.
 */
export const SingleWorkspace: Story = {
	parameters: {
		docs: {
			description: {
				story:
					"Default view with a single workspace. No keyboard shortcuts are available as there's only one workspace.",
			},
		},
	},
};

/**
 * Multiple workspaces available for switching. Use keyboard shortcuts ⌘1, ⌘2, and ⌘3 on Mac
 * or Ctrl+1, Ctrl+2, and Ctrl+3 on Windows/Linux to quickly switch between workspaces without using the dropdown menu.
 */
export const MultipleWorkspaces: Story = {
	args: {
		workspaces: [
			{
				name: "AET",
				logoUrl: "https://avatars.githubusercontent.com/u/11064260?s=200&v=4",
			},
			{
				name: "Personal",
				logoUrl: "https://github.com/identicons/example.png",
			},
			{
				name: "Team B",
				logoUrl: "https://github.com/identicons/team-b.png",
			},
		],
		activeWorkspace: {
			name: "AET",
			logoUrl: "https://avatars.githubusercontent.com/u/11064260?s=200&v=4",
		},
	},
	parameters: {
		docs: {
			description: {
				story:
					"Multiple workspaces with cross-platform keyboard shortcuts (⌘1-3 on Mac, Ctrl+1-3 on Windows/Linux) to quickly switch between them.",
			},
		},
	},
};
