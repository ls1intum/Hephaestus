import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AdminPracticesPage } from "./AdminPracticesPage";
import { mockPractices } from "./storyMockData";

/**
 * Full admin page for managing practice definitions.
 * Card-based layout with slide-over sheet for create/edit and AlertDialog for delete.
 */
const meta = {
	component: AdminPracticesPage,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		practices: mockPractices,
		isLoading: false,
		isCreating: false,
		isUpdating: false,
		isDeleting: false,
		togglingPractices: new Set<string>(),
		onCreatePractice: fn().mockResolvedValue(undefined),
		onUpdatePractice: fn().mockResolvedValue(undefined),
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
