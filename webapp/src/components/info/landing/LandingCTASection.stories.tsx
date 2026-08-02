import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { LandingCTASection } from "./LandingCTASection";

/**
 * Final sign-in or workspace action for landing-page visitors.
 */
const meta = {
	component: LandingCTASection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The CTA section directs signed-out visitors to sign in and signed-in visitors to their workspace.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		onSignIn: {
			description: "Callback function triggered when the sign-in button is clicked",
			action: "signed in",
		},
		onGoToDashboard: {
			description: "Callback function triggered when the 'Go to dashboard' button is clicked",
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
} satisfies Meta<typeof LandingCTASection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default CTA section for first-time visitors.
 * Features the "Sign in" CTA button.
 */
export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

/**
 * CTA section for authenticated users.
 * "Sign in" is replaced with "Go to dashboard".
 */
export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
};
