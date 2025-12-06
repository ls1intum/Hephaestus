import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { LandingHeroSection } from "./LandingHeroSection";

/**
 * Hero section component that introduces Hephaestus with a prominent headline,
 * description, and call-to-action buttons. Features a preview of the leaderboard
 * to showcase the platform's core functionality.
 */
const meta = {
	component: LandingHeroSection,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"The hero section serves as the main entry point to the landing page, featuring a clear value proposition and preview of the platform's key features.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		onSignIn: {
			description:
				"Callback function triggered when the sign-in button is clicked",
			action: "signed in",
		},
		onGoToDashboard: {
			description:
				"Callback function triggered when the 'Go to Dashboard' button is clicked",
			action: "go to dashboard",
		},
		isSignedIn: {
			description: "Whether the user is currently signed in",
			control: "boolean",
		},
		onLearnMoreClick: {
			description:
				"Callback function triggered when the learn more button is clicked",
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
 * Default hero section for first-time visitors.
 * Features "Get Started" CTA and leaderboard preview.
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
