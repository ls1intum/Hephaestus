import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { LandingFAQSection } from "./LandingFAQSection";

/**
 * FAQ section component that displays frequently asked questions and answers
 * through an accordion interface, along with a community link.
 */
const meta = {
	component: LandingFAQSection,
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
} satisfies Meta<typeof LandingFAQSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default FAQ section for first-time visitors.
 * Features "Get Started" CTA and FAQ accordion.
 */
export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

/**
 * FAQ section for authenticated users.
 * "Get Started" button is replaced with "Go to Dashboard".
 */
export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
};
