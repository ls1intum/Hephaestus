import type { Meta, StoryObj } from "@storybook/react";
import { SidebarProvider } from "@/components/ui/sidebar";
import { NavContextHeader } from "./NavContextHeader";

/**
 * Header navigation for a context with back button to return to main navigation.
 * Provides context and navigation controls when the sidebar is in a specific context.
 */
const meta = {
	component: NavContextHeader,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		title: {
			control: "text",
			description: "Title of the context header, displayed prominently.",
			defaultValue: "Mentor",
		},
	},
	decorators: [
		(Story) => (
			<SidebarProvider className="min-h-0 w-[16rem] border border-border rounded-lg p-2 bg-sidebar">
				<Story />
			</SidebarProvider>
		),
	],
} satisfies Meta<typeof NavContextHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default context header with back navigation.
 */
export const Default: Story = {
	args: {
		title: "Mentor",
		workspaceSlug: "ls1intum",
	},
};

/**
 * Header shown when user is a specific context, providing clear context and way back to main navigation.
 */
export const WithContext: Story = {
	args: {
		title: "Mentor",
		workspaceSlug: "ls1intum",
	},
};
