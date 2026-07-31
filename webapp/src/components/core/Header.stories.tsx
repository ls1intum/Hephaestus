import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { expectNoPageOverflow } from "@/test/reflow";
import Header from "./Header";

const meta = {
	component: Header,
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "desktop" },
	},
	tags: ["autodocs"],
	args: {
		version: "1.0.0",
		environmentName: "Production",
		isProduction: true,
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
} satisfies Meta<typeof Header>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
	},
};

export const Staging: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
		environmentName: "Staging",
		isProduction: false,
	},
};

export const Preview: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
		environmentName: "Preview",
		isProduction: false,
	},
};

export const Development: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
		version: "DEV",
		environmentName: "Local",
		isProduction: false,
	},
};

export const LoggedOut: Story = {
	args: {
		isAuthenticated: false,
		isLoading: false,
	},
};

export const Loading: Story = {
	args: {
		isAuthenticated: false,
		isLoading: true,
	},
};

export const NoWorkspace: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
		workspaceSlug: undefined,
	},
};

export const Mobile: Story = {
	args: {
		isAuthenticated: true,
		isLoading: false,
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: expectNoPageOverflow,
};
