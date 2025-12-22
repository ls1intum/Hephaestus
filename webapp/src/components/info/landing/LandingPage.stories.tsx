import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { LandingPage } from "./LandingPage";

/**
 * A straightforward landing page that honestly presents Hephaestus with a clean hero section,
 * key features, and a real testimonial. The page adapts its content and actions based on
 * the user's authentication state while maintaining a truthful representation of the project.
 */
const meta = {
	component: LandingPage,
	tags: ["autodocs"],
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"A simple, honest landing page that presents the Hephaestus platform features and benefits without exaggeration, focusing on the real value it provides to development teams.",
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
				"Callback function triggered when the 'Go to Dashboard' button is clicked (for signed-in users)",
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
 * Features prominent "Get Started" CTAs directing to the sign-in flow.
 */
export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

/**
 * Landing page view for users who are already authenticated.
 * "Get Started" buttons are replaced with "Go to Dashboard" to provide
 * quick access to the user's workspace.
 */
export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
};
