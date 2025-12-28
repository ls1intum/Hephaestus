import type { Meta, StoryObj } from "@storybook/react-vite";
import { AboutHeroSection } from "./AboutHeroSection";

/**
 * AboutHeroSection component for the main hero area of the About page.
 * Features the iconic hammer symbol and introduces the Hephaestus platform with mythological context.
 */
const meta = {
	component: AboutHeroSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof AboutHeroSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default hero section with standard styling and content.
 */
export const Default: Story = {};
