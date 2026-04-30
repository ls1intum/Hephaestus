import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { LandingHeroSection } from "./LandingHeroSection";

/**
 * Landing hero with a sample comment from Hephaestus on a pull request.
 */
const meta = {
	component: LandingHeroSection,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"The hero: a short pitch and a sample comment Hephaestus might leave on a pull request.",
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

/**
 * Default hero for first-time visitors. Sign-in CTA and a sample comment card.
 */
export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

/**
 * Hero section for authenticated users.
 * "Get Started" button is replaced with "Go to Dashboard".
 */
export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
};
