import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { LandingWhyChooseSection } from "./LandingWhyChooseSection";

/**
 * Why Choose section component that explains the unique value proposition of Hephaestus
 * through a combination of visual elements and feature highlights.
 */
const meta = {
	component: LandingWhyChooseSection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The Why Choose section presents the core benefits of Hephaestus through a combination of visual storytelling and feature highlights, helping users understand the platform's unique value.",
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
} satisfies Meta<typeof LandingWhyChooseSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default Why Choose section for first-time visitors.
 * Features "Get Started" CTA and benefit highlights.
 */
export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

/**
 * Why Choose section for authenticated users.
 * "Get Started" button is replaced with "Go to Dashboard".
 */
export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
};
