import type { Meta, StoryObj } from "@storybook/react";
import { ModeToggle } from "./ModeToggle";
import { ThemeProvider } from "@/integrations/theme/ThemeContext";

/**
 * Mode toggle component for switching between light, dark and system theme
 */
const meta = {
  component: ModeToggle,
  parameters: {
    layout: "centered",
  },
  decorators: [
    (Story) => (
      <ThemeProvider storageKey="theme" defaultTheme="light">
        <div className="flex items-center justify-center p-8 bg-primary-foreground">
          <Story />
        </div>
      </ThemeProvider>
    ),
  ],
  tags: ["autodocs"],
} satisfies Meta<typeof ModeToggle>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default mode toggle component
 */
export const Default: Story = {};
