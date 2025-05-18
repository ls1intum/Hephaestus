import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
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
		onLogin: fn(),
		onLogout: fn(),
	},
	argTypes: {
		showAdmin: {
			control: "boolean",
			description: "Whether user has admin access",
		},
		showMentor: {
			control: "boolean",
			description: "Whether user has mentor access",
		},
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
	},
} satisfies Meta<typeof Header>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Header state for an authenticated admin user with full permissions.
 * Shows admin navigation options and mentor access.
 */
export const LoggedInAdmin: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
		showAdmin: true,
		showMentor: true,
	},
};

/**
 * Header state for an authenticated user with mentor access but not admin privileges.
 */
export const LoggedInNonAdminWithMentor: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
		showAdmin: false,
		showMentor: true,
	},
};

/**
 * Header state for a regular authenticated user without special permissions.
 */
export const LoggedInNonAdmin: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
		showAdmin: false,
		showMentor: false,
	},
};

/**
 * Header state for unauthenticated visitors, showing minimal options and a sign-in button.
 */
export const LoggedOut: Story = {
	args: {
		isAuthenticated: false,
		isLoading: false,
		showAdmin: false,
		showMentor: false,
	},
};

/**
 * Header state when authentication status is being determined, showing loading state.
 */
export const Loading: Story = {
	args: {
		isAuthenticated: false,
		isLoading: true,
		showAdmin: false,
		showMentor: false,
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
		showAdmin: true,
		showMentor: true,
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
		showAdmin: true,
		showMentor: true,
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
