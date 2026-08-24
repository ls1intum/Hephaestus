import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { LandingFaqSection } from "./LandingFaqSection";

const meta = {
	component: LandingFaqSection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The FAQ section provides answers to common questions through an accordion interface, helping users find information quickly and easily. It also includes a link to the community for additional support.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		onSignIn: {
			description: "Callback function triggered when the sign-in button is clicked",
			action: "signed in",
		},
		isSignedIn: {
			description: "Whether the user is currently signed in",
			control: "boolean",
		},
	},
	args: {
		onSignIn: fn(),
	},
} satisfies Meta<typeof LandingFaqSection>;

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
