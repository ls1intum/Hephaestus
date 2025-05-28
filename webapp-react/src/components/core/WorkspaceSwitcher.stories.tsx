import { SidebarProvider } from "@/components/ui/sidebar";
import type { Meta, StoryObj } from "@storybook/react";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

/**
 * UI component for switching between different workspaces, displaying the
 * current workspace and allowing selection of others.
 */
const meta = {
  component: WorkspaceSwitcher,
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Workspace switching component that displays the current workspace and allows switching between multiple workspaces.",
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
  },
  argTypes: {
    workspaces: {
      control: "object",
      description: "List of available workspaces",
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
export const SingleWorkspace: Story = {};

/**
 * Multiple workspaces available for switching.
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
  },
};
