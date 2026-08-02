import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { LandingHeroSection } from "./LandingHeroSection";

/**
 * Hero section component that introduces Hephaestus with a prominent headline,
 * description, and call-to-action buttons. Its animated preview connects project
 * work, practice feedback, and a conversation with Heph.
 */
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

/**
 * Default hero section for first-time visitors.
 * Features the sign-in CTA and animated practice-feedback preview.
 */
export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

/**
 * Hero section for authenticated users.
 * "Sign in" button is replaced with "Go to dashboard".
 */
export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
};
