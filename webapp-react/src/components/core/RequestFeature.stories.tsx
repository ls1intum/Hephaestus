import type { Meta, StoryObj } from "@storybook/react";
import RequestFeature from "./RequestFeature";

/**
 * The RequestFeature component provides an actionable button that directs users 
 * to the feature request page. It can display in full text or icon-only mode
 * with a tooltip for space-constrained UIs.
 */
const meta = {
  component: RequestFeature,
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component: 'A button that allows users to request new features for the application, with responsive display options.',
      },
    },
  },
  tags: ["autodocs"],
  argTypes: {
    iconOnly: { 
      control: 'boolean',
      description: 'Whether to show only the icon without text',
      defaultValue: false,
    },
  },
} satisfies Meta<typeof RequestFeature>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view with full text label and icon.
 * Suitable for desktop or spaces with sufficient room.
 */
export const Default: Story = {
  args: {
    iconOnly: false,
  },
};

/**
 * Compact view showing only an icon with tooltip.
 * Ideal for mobile or space-constrained interfaces.
 */
export const IconOnly: Story = {
  args: {
    iconOnly: true,
  },
  parameters: {
    docs: {
      description: {
        story: 'Icon-only version with tooltip for smaller screen sizes or compact layouts.',
      },
    },
  },
};
