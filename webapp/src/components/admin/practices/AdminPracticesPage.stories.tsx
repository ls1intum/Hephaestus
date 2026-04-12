import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AdminPracticesPage } from "./AdminPracticesPage";
import { mockPractices } from "./storyMockData";

/**
 * Full admin page for managing practice definitions.
 * Card-based layout with Link navigation to create/edit routes and AlertDialog for delete.
 */
const meta = {
	component: AdminPracticesPage,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		practices: mockPractices,
		isLoading: false,
		isDeleting: false,
		togglingPractices: new Set<string>(),
		onDeletePractice: fn().mockResolvedValue(undefined),
		onSetActive: fn(),
	},
} satisfies Meta<typeof AdminPracticesPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Default view with practice cards. */
export const Default: Story = {};

/** Loading state while fetching practices. */
export const Loading: Story = {
	args: {
		practices: [],
		isLoading: true,
	},
};

/** Empty state when no practices have been configured. */
export const Empty: Story = {
	args: {
		practices: [],
	},
};
