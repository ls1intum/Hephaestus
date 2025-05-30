import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { LandingCTASection } from "./LandingCTASection";

/**
 * Call-to-Action section component that encourages users to get started
 * with Hephaestus through a prominent CTA button.
 */
const meta = {
  component: LandingCTASection,
  parameters: {
    layout: "padded",
    docs: {
      description: {
        component:
          "The CTA section provides a final opportunity to convert visitors into users through a prominent call-to-action button and compelling copy.",
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
} satisfies Meta<typeof LandingCTASection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default CTA section for first-time visitors.
 * Features "Get Started" CTA button.
 */
export const Default: Story = {
  args: {
    isSignedIn: false,
  },
};

/**
 * CTA section for authenticated users.
 * "Get Started" button is replaced with "Go to Dashboard".
 */
export const SignedIn: Story = {
  args: {
    isSignedIn: true,
  },
}; 