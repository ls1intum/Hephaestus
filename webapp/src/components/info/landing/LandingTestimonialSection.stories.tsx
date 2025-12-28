import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { LandingTestimonialSection } from "./LandingTestimonialSection";

/**
 * Testimonial section component that displays user feedback and success stories
 * through a collection of testimonial cards.
 */
const meta = {
	component: LandingTestimonialSection,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The Testimonial section showcases real user experiences and success stories through a collection of testimonial cards, helping build trust and credibility.",
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
} satisfies Meta<typeof LandingTestimonialSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default testimonial section for first-time visitors.
 * Features "Get Started" CTA and testimonial cards.
 */
export const Default: Story = {
	args: {
		isSignedIn: false,
	},
};

/**
 * Testimonial section for authenticated users.
 * "Get Started" button is replaced with "Go to Dashboard".
 */
export const SignedIn: Story = {
	args: {
		isSignedIn: true,
	},
};
