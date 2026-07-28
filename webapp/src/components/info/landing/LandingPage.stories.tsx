import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { LandingPage } from "./LandingPage";

/**
 * Public landing page for signed-out and signed-in visitors.
 */
const meta = {
	component: LandingPage,
	tags: ["autodocs"],
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"The landing page explains practice feedback, conversations with Heph, and the role of workspace settings.",
			},
		},
	},
	argTypes: {
		onSignIn: {
			description: "Callback function triggered when the sign-in button is clicked",
			action: "signed in",
		},
		onGoToDashboard: {
			description:
				"Callback function triggered when the 'Go to dashboard' button is clicked (for signed-in users)",
			action: "go to dashboard",
		},
		isSignedIn: {
			description: "Whether the user is currently signed in",
			control: "boolean",
		},
	},
	args: {
		onSignIn: fn(),
		onGoToDashboard: fn(),
	},
} satisfies Meta<typeof LandingPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default landing page view for first-time visitors and anonymous users.
 * Features prominent "Sign in" CTAs directing to the sign-in flow.
 */
export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

/**
 * Landing page view for users who are already authenticated.
 * "Sign in" buttons are replaced with "Go to dashboard" to provide
 * quick access to the user's workspace.
 */
export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
};
