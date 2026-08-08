import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { LandingHeroSection } from "./LandingHeroSection";

const meta = {
	component: LandingHeroSection,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"The hero introduces practice feedback in plain language and uses a responsive, reduced-motion-aware preview to show how project work leads to feedback and conversation.",
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
		onLearnMoreClick: {
			description: "Callback function triggered when the learn more button is clicked",
			action: "learn more clicked",
		},
	},
	args: {
		onSignIn: fn(),
		onGoToDashboard: fn(),
		onLearnMoreClick: fn(),
	},
} satisfies Meta<typeof LandingHeroSection>;

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
