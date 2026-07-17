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

/** Engaging is deliberately cheap: one confirm with an optional reason. */
export const Released: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /engage silent mode/i }));
		// The dialog renders in a portal on the document body, not inside the canvas element.
		const body = within(canvasElement.ownerDocument.body);
		await userEvent.type(await body.findByLabelText(/reason/i), "Investigating incident #42");
		await userEvent.click(body.getByRole("button", { name: /^engage silent mode$/i }));
		await expect(args.onEngage).toHaveBeenCalledWith("Investigating incident #42");
	},
};

/** Releasing is deliberately heavy: consequences restated + type-to-confirm gate. */
export const Engaged: Story = {
	args: { settings: engaged },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /release silent mode/i }));
		const body = within(canvasElement.ownerDocument.body);
		const confirm = await body.findByRole("button", { name: /^release silent mode$/i });
		// Locked until the operator types the confirm word.
		await expect(confirm).toBeDisabled();
		await userEvent.type(body.getByLabelText(/type/i), "release");
		await expect(confirm).toBeEnabled();
		await userEvent.click(confirm);
		await expect(args.onRelease).toHaveBeenCalled();
	},
};

export const Pending: Story = {
	args: { settings: engaged, isPending: true },
};
