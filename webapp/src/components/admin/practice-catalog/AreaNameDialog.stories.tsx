import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import { mockAreas } from "@/components/admin/practices/story-mock-data";
import { expectGenuinelyDisabled } from "@/test/controls";
import { expectSettledVisible } from "@/test/overlay";
import { AreaNameDialog } from "./AreaNameDialog";

/**
 * One surface for naming an area. Creating one used to happen in a `Popover` and renaming one in a
 * `Dialog`, side by side on the same page — the same single-field decision in two containers.
 */
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
		// Create and rename are told apart by `area` alone, so the title and the button cannot disagree.
		await expectSettledVisible(await screen.findByRole("heading", { name: "Create area" }));
		await userEvent.type(screen.getByLabelText("New area name"), "Documentation");
		await userEvent.click(screen.getByRole("button", { name: "Create" }));
		await expect(args.onSubmit).toHaveBeenCalledWith("Documentation");
	},
};

export const Renaming: Story = {
	args: { area: mockAreas[0] },
	play: async ({ args }) => {
		const field = await screen.findByLabelText("Area name");
		await expectSettledVisible(field);
		await expect(screen.getByRole("heading", { name: "Rename area" })).toBeVisible();
		await expect(field).toHaveValue(mockAreas[0].name);
		await userEvent.clear(field);
		await userEvent.type(field, "Review-ready work");
		await userEvent.click(screen.getByRole("button", { name: "Save" }));
		await expect(args.onSubmit).toHaveBeenCalledWith("Review-ready work");
	},
};

export const UnchangedNameJustCloses: Story = {
	args: { area: mockAreas[0] },
	play: async ({ args }) => {
		await expectSettledVisible(await screen.findByLabelText("Area name"));
		await userEvent.click(screen.getByRole("button", { name: "Save" }));
		// No request for a name that did not change.
		await expect(args.onSubmit).not.toHaveBeenCalled();
		await expect(args.onOpenChange).toHaveBeenCalledWith(false);
	},
};

export const EmptyNameDoesNotDismiss: Story = {
	play: async ({ args }) => {
		await expectSettledVisible(await screen.findByLabelText("New area name"));
		await userEvent.click(screen.getByRole("button", { name: "Create" }));
		// Closing on an empty name would throw away a create the reader had started, and look like a
		// decision they did not make.
		await expect(args.onOpenChange).not.toHaveBeenCalled();
		await expect(args.onSubmit).not.toHaveBeenCalled();
	},
};

export const Pending: Story = {
	args: { pending: true },
	play: async () => {
		await expectGenuinelyDisabled(await screen.findByRole("button", { name: "Creating…" }));
	},
};

export const DarkMode: Story = {
	args: { area: mockAreas[0] },
	globals: { theme: "dark" },
};
