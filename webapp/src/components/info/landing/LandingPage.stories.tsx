import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";

import { LandingPage } from "./LandingPage";

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
