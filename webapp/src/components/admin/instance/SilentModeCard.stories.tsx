import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import type { InstanceSettings } from "@/api/types.gen";
import { SilentModeCard } from "./SilentModeCard";

const released: InstanceSettings = {
	silentModeEngaged: false,
	silentModeChangedAt: new Date("2026-07-10T09:30:00Z"),
	silentModeChangedBy: "felixtjdietrich",
};

const engaged: InstanceSettings = {
	silentModeEngaged: true,
	silentModeReason: "Investigating incident #42",
	silentModeChangedAt: new Date("2026-07-16T08:00:00Z"),
	silentModeChangedBy: "felixtjdietrich",
};

const meta = {
	component: SilentModeCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		settings: released,
		isPending: false,
		onEngage: fn(),
		onRelease: fn(),
	},
} satisfies Meta<typeof SilentModeCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Released: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /engage silent mode/i }));
		// The dialog renders in a portal on the document body, not inside the canvas element.
		const body = within(canvasElement.ownerDocument.body);
		await userEvent.type(
			await body.findByLabelText(/why are you silencing/i),
			"Investigating incident #42",
		);
		await userEvent.click(body.getByRole("button", { name: /^engage silent mode$/i }));
		await expect(args.onEngage).toHaveBeenCalledWith("Investigating incident #42");
	},
};

export const Engaged: Story = {
	args: { settings: engaged },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /release silent mode/i }));
		const body = within(canvasElement.ownerDocument.body);
		const confirm = await body.findByRole("button", { name: /^release silent mode$/i });
		// A mismatch reports the error instead of firing; only the exact word releases.
		await userEvent.type(body.getByLabelText(/type/i), "nope");
		await userEvent.click(confirm);
		await expect(args.onRelease).not.toHaveBeenCalled();
		await expect(await body.findByText(/does not match/i)).toBeInTheDocument();

		await userEvent.clear(body.getByLabelText(/type/i));
		await userEvent.type(body.getByLabelText(/type/i), "release");
		await userEvent.click(confirm);
		await expect(args.onRelease).toHaveBeenCalled();
	},
};

export const Pending: Story = {
	args: { settings: engaged, isPending: true },
};

/** The dialog is dismissed by the settings landing, not by the click — so a failed request keeps it open. */
export const ClosesWhenTheToggleLands: Story = {
	play: async ({ canvasElement, args }) => {
		const canvas = within(canvasElement);
		const body = within(canvasElement.ownerDocument.body);
		await userEvent.click(canvas.getByRole("button", { name: /engage silent mode/i }));
		await expect(await body.findByRole("dialog")).toBeInTheDocument();

		await userEvent.click(body.getByRole("button", { name: /^engage silent mode$/i }));
		await expect(args.onEngage).toHaveBeenCalled();
		// The parent has not flipped `settings` yet, so the dialog is still up.
		await expect(body.getByRole("dialog")).toBeInTheDocument();
	},
};
