import type { Meta, StoryObj } from "@storybook/react-vite";
import { AboutCallToActionSection } from "./AboutCallToActionSection";

/**
 * AboutCallToActionSection component for encouraging user engagement with the project.
 * Features prominent GitHub repository and documentation links with inviting messaging.
 */
const meta = {
	component: AboutCallToActionSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof AboutCallToActionSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default call-to-action section with standard styling and content.
 */
export const Default: Story = {};
