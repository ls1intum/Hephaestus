import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AdminPracticesPage } from "./AdminPracticesPage";
import { mockAreas, mockPractices } from "./storyMockData";

/**
 * Full admin page for managing practice definitions.
 * Card-based layout with Link navigation to create/edit routes and AlertDialog for delete.
 */
const meta = {
	component: AdminPracticesPage,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		practices: mockPractices,
		areas: mockAreas,
		isLoading: false,
		isDeleting: false,
		togglingPractices: new Set<string>(),
		onDeletePractice: fn().mockResolvedValue(undefined),
		onSetActive: fn(),
	},
	decorators: [
		(Story) => (
			<div className="mx-auto max-w-3xl">
				<Story />
			</div>
		),
	],
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

/** Error state when the catalog fails to load. */
export const LoadError: Story = {
	args: {
		practices: [],
		isError: true,
		onRetry: fn(),
	},
};
