import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import Header from "./Header";

/**
 * Header component for the Hephaestus application that provides navigation,
 * authentication controls, and access to various application features.
 */
const meta = {
	component: Header,
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "desktop" },
		docs: {
			description: {
				component:
					"Main navigation component with adaptive layout and contextual controls based on user permissions.",
			},
		},
	},
	tags: ["autodocs"],
	args: {
		version: "1.0.0",
		name: "John Doe",
		username: "johnDoe",
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
			description: "Application version displayed beside logo",
		},
		sidebarTrigger: {
			control: "object",
			description: "Sidebar trigger button component",
		},
	},
} satisfies Meta<typeof Header>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Header state for a regular authenticated user without special permissions.
 */
export const LoggedInUser: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
	},
	parameters: {
		docs: {
			description: {
				story:
					"Standard header view for authenticated users showing the user profile dropdown with navigation options.",
			},
		},
	},
};

/**
 * Header state for unauthenticated visitors, showing minimal options and a sign-in button.
 */
export const LoggedOut: Story = {
	args: {
		isAuthenticated: false,
		isLoading: false,
	},
	parameters: {
		docs: {
			description: {
				story:
					"Header view for unauthenticated users with sign-in button and limited navigation options.",
			},
		},
	},
};

/**
 * Header state when authentication status is being determined, showing loading state.
 */
export const Loading: Story = {
	args: {
		isAuthenticated: false,
		isLoading: true,
	},
	parameters: {
		docs: {
			description: {
				story:
					"Header in loading state while user authentication is being verified.",
			},
		},
	},
};

/**
 * Mobile view of the header showing the hamburger menu for navigation.
 * Demonstrates responsive behavior with collapsed navigation.
 */
export const MobileView: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
	},
	parameters: {
		viewport: { defaultViewport: "mobile1" },
		docs: {
			description: {
				story:
					"Header in mobile view with collapsed navigation in a hamburger menu and compact controls.",
			},
		},
	},
};

/**
 * Desktop view of the header showing full horizontal navigation.
 */
export const DesktopView: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
	},
	parameters: {
		viewport: { defaultViewport: "desktop" },
		docs: {
			description: {
				story:
					"Header in desktop view with full horizontal navigation and expanded controls.",
			},
		},
	},
};

/**
 * Header without sidebar trigger for layouts that don't require a sidebar.
 */
export const WithoutSidebarTrigger: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
		sidebarTrigger: undefined,
	},
	parameters: {
		docs: {
			description: {
				story:
					"Header variant without a sidebar toggle button for pages with fixed layouts.",
			},
		},
	},
};
