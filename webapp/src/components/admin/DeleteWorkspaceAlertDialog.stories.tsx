import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { DeleteWorkspaceAlertDialog } from "./DeleteWorkspaceAlertDialog";

/** The dialog is portalled, so the plays query the document rather than the story canvas. */
const meta = {
	component: DeleteWorkspaceAlertDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		open: true,
		workspaceSlug: "acme-corp",
		isDeleting: false,
		onOpenChange: fn(),
		onConfirm: fn(),
	},
} satisfies Meta<typeof DeleteWorkspaceAlertDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A near-miss slug states the mismatch and purges nothing; the exact slug gets through. */
export const TypeToConfirm: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("alertdialog"));
		const gate = dialog.getByLabelText(/to confirm/i);
		const confirm = dialog.getByRole("button", { name: /delete workspace/i });

		await userEvent.type(gate, "acme-crop");
		await userEvent.click(confirm);
		await expect(args.onConfirm).not.toHaveBeenCalled();
		await expect(gate).toHaveAttribute("aria-invalid", "true");
		await expect(dialog.getByText(/that does not match/i)).toBeInTheDocument();

		await userEvent.clear(gate);
		await userEvent.type(gate, "acme-corp");
		await userEvent.click(confirm);
		await expect(args.onConfirm).toHaveBeenCalledTimes(1);
	},
};

/** The consequences must live in the description, since that is what `aria-describedby` resolves
 * to — an alert dialog suppresses screen-reader virtual navigation, so prose outside it is read
 * to nobody. */
export const ConsequencesAreDescribed: Story = {
	play: async () => {
		const dialog = await screen.findByRole("alertdialog");
		const describedBy = dialog.getAttribute("aria-describedby");
		await expect(describedBy).toBeTruthy();

		const description = dialog.ownerDocument.getElementById(describedBy as string);
		await expect(description).toHaveTextContent(/stays reserved/i);
	},
};

/** In flight: the gate and the confirm lock, so a second click cannot fire a second purge. */
export const Deleting: Story = {
	args: { isDeleting: true },
	play: async () => {
		const dialog = within(await screen.findByRole("alertdialog"));
		await expect(dialog.getByRole("button", { name: /deleting/i })).toBeDisabled();
		await expect(dialog.getByLabelText(/to confirm/i)).toBeDisabled();
	},
};
