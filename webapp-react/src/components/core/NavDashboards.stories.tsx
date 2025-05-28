import { SidebarProvider } from "@/components/ui/sidebar";
import type { Meta, StoryObj } from "@storybook/react";
import { NavDashboards } from "./NavDashboards";

/**
 * Navigation component for dashboard features, providing access to various user
 * dashboards and analytics views.
 */
const meta = {
  component: NavDashboards,
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Dashboard navigation sidebar component with links to different analytics views and user dashboards.",
      },
    },
  },
  tags: ["autodocs"],
  args: {
    username: "johnDoe",
  },
  argTypes: {
    username: {
      control: "text",
      description: "Username of the current user",
    },
  },
  decorators: [
    (Story) => (
      <SidebarProvider className="min-h-0 w-[16rem] border border-border rounded-lg p-2 bg-sidebar">
        <Story />
      </SidebarProvider>
    ),
  ],
} satisfies Meta<typeof NavDashboards>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view of the dashboards navigation sidebar.
 */
export const Default: Story = {};

/**
 * Different user's dashboard navigation.
 */
export const DifferentUser: Story = {
  args: {
    username: "janeDoe",
  },
};
