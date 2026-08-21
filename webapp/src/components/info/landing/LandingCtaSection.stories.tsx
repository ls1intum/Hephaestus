import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { LandingCtaSection } from "./LandingCtaSection";

const meta = {
	component: LandingCtaSection,
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
} satisfies Meta<typeof LandingCtaSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
};
