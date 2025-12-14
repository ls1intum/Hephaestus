import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import Header from "./Header";

/**
 * Header component - fully presentational, receives all data via props.
 * Version badge links to GitHub releases for production versions.
 */
const meta = {
	component: Header,
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "desktop" },
	},
	tags: ["autodocs"],
	args: {
		version: "1.0.0",
		name: "John Doe",
		username: "johnDoe",
		workspaceSlug: "demo-workspace",
		sidebarTrigger: <SidebarTrigger />,
		onLogin: fn(),
		onLogout: fn(),
	},
	decorators: [
		(Story) => (
			<SidebarProvider>
				<div className="w-full">
					<Story />
				</div>
			</SidebarProvider>
		),
	],
	argTypes: {
		isAuthenticated: {
			control: "boolean",
			description: "User authentication state",
		},
		isLoading: {
			control: "boolean",
			description: "Whether the authentication is currently loading",
		},
		name: {
			control: "text",
			description: "Full name of the authenticated user",
		},
		username: {
			control: "text",
			description: "Username of the authenticated user",
		},
		version: {
			control: "text",
			description: "Application version - links to release notes for semver",
		},
		workspaceSlug: {
			control: "text",
			description: "Active workspace slug for routing",
		},
	},
} satisfies Meta<typeof Header>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Production header with clickable version linking to release notes.
 */
export const Default: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
	},
};

/**
 * Local development header with non-clickable DEV version badge.
 */
export const Development: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
		version: "DEV",
	},
};

/**
 * Header for unauthenticated visitors with sign-in button.
 */
export const LoggedOut: Story = {
	args: {
		isAuthenticated: false,
		isLoading: false,
	},
};

/**
 * Header in loading state while authentication is being verified.
 */
export const Loading: Story = {
	args: {
		isAuthenticated: false,
		isLoading: true,
	},
};

/**
 * Header without active workspace - logo links to landing page.
 */
export const NoWorkspace: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
		workspaceSlug: undefined,
	},
};

/**
 * Mobile view with compact layout.
 */
export const Mobile: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
	},
	parameters: {
		viewport: { defaultViewport: "mobile1" },
	},
};
