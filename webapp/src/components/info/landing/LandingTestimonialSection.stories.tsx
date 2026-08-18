import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { LandingTestimonialSection } from "./LandingTestimonialSection";

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
