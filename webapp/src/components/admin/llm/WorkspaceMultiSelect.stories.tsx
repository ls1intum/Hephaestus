import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { WorkspaceMultiSelect, type WorkspaceMultiSelectOption } from "./WorkspaceMultiSelect";

const mockOptions: WorkspaceMultiSelectOption[] = [
	{ id: 1, displayName: "Obsphera", workspaceSlug: "obsphera" },
	{ id: 2, displayName: "Acme Corp", workspaceSlug: "acme" },
	{ id: 3, displayName: "TUM CIT", workspaceSlug: "tum-cit" },
];

const meta = {
	component: WorkspaceMultiSelect,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		options: mockOptions,
		selectedIds: [],
		onChange: fn(),
	},
	decorators: [
		(Story) => (
			<div className="w-72">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof WorkspaceMultiSelect>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Empty: Story = {};

export const OneSelected: Story = {
	args: { selectedIds: [1] },
};

export const MultipleSelected: Story = {
	args: { selectedIds: [1, 3] },
};

export const NoWorkspaces: Story = {
	args: { options: [] },
};

export const OpensAndListsOptions: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button"));
		// The popup renders in a portal → query the document. `toBeInTheDocument` (not `toBeVisible`):
		// the popup's enter transition can still be animating opacity when this assertion runs.
		await expect(await screen.findByRole("checkbox", { name: /Obsphera/i })).toBeInTheDocument();
		await expect(await screen.findByRole("checkbox", { name: /Acme Corp/i })).toBeInTheDocument();
	},
};
