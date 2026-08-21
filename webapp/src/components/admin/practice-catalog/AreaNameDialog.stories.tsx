import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import { mockAreas } from "@/components/admin/practices/story-mock-data";
import { expectGenuinelyDisabled } from "@/test/controls";
import { expectSettledVisible } from "@/test/overlay";
import { AreaNameDialog } from "./AreaNameDialog";

const meta = {
	component: AreaNameDialog,
	parameters: { layout: "centered" },
	args: {
		area: null,
		open: true,
		pending: false,
		onOpenChange: fn(),
		onSubmit: fn(async () => true),
	},
	argTypes: { area: { control: false } },
	tags: ["autodocs"],
} satisfies Meta<typeof AreaNameDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Creating: Story = {
	play: async ({ args }) => {
		await expectSettledVisible(await screen.findByRole("heading", { name: "Create area" }));
		await userEvent.type(screen.getByLabelText("Name"), "Documentation");
		await userEvent.click(screen.getByRole("button", { name: "Create" }));
		await expect(args.onSubmit).toHaveBeenCalledWith("Documentation");
	},
};

export const Renaming: Story = {
	args: { area: mockAreas[0] },
	play: async ({ args }) => {
		const field = await screen.findByLabelText("Name");
		await expectSettledVisible(field);
		await expect(screen.getByRole("heading", { name: "Rename area" })).toBeVisible();
		await userEvent.clear(field);
		await userEvent.type(field, "Review-ready work");
		await userEvent.click(screen.getByRole("button", { name: "Save" }));
		await expect(args.onSubmit).toHaveBeenCalledWith("Review-ready work");
	},
};

export const UnchangedNameJustCloses: Story = {
	args: { area: mockAreas[0] },
	play: async ({ args }) => {
		await expectSettledVisible(await screen.findByLabelText("Name"));
		await userEvent.click(screen.getByRole("button", { name: "Save" }));
		// No request for a name that did not change.
		await expect(args.onSubmit).not.toHaveBeenCalled();
		await expect(args.onOpenChange).toHaveBeenCalledWith(false);
	},
};

export const EmptyNameCannotBeSubmitted: Story = {
	play: async () => {
		await expectSettledVisible(await screen.findByLabelText("Name"));
		// Refusing up front, rather than closing on submit and discarding a create in progress.
		await expectGenuinelyDisabled(screen.getByRole("button", { name: "Create" }));
	},
};

export const Pending: Story = {
	args: { pending: true },
	play: async () => {
		await expectGenuinelyDisabled(await screen.findByRole("button", { name: "Creating…" }));
	},
};
