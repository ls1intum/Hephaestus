import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { LandingCTASection } from "./LandingCTASection";

/**
 * Call-to-Action section component that encourages users to sign in
 * with GitHub using an officially branded CTA button.
 */
const meta = {
	component: LandingCTASection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The CTA section provides a final opportunity to convert visitors into users through a prominent call-to-action button and compelling copy.",
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
			description: "Callback function triggered when the 'Go to Dashboard' button is clicked",
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
 * Features "Sign in with GitHub" CTA button.
 */
export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

/**
 * CTA section for authenticated users.
 * GitHub sign-in button is replaced with "Go to Dashboard".
 */
export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
};
