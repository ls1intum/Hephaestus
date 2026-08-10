import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewTierLadder } from "./ReviewTierLadder";

const meta = {
	title: "Workspace admin/Practices/Review tier ladder",
	component: ReviewTierLadder,
	parameters: { layout: "padded", viewport: { defaultViewport: "reflow" } },
	args: {
		label: "How far Hephaestus may go without you",
		value: "DELIVER",
		onChange: fn(),
	},
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-2xl">
				<Story />
			</div>
		),
	],
	tags: ["autodocs"],
} satisfies Meta<typeof ReviewTierLadder>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Full: Story = {
	args: { variant: "full" },
	play: async ({ canvas }) => {
		// Radios, not pressed buttons: the rungs are four states of one setting, and a screen reader
		// should say "Deliver, selected, 4 of 4" rather than "Off, not pressed" three times over.
		await expect(canvas.getAllByRole("radio")).toHaveLength(4);
		await expect(canvas.getByRole("radio", { name: "Deliver" })).toBeChecked();
		await expect(canvas.getByRole("radiogroup")).toHaveAccessibleName(
			"How far Hephaestus may go without you",
		);
		await expectNoPageOverflow();
	},
};

export const Off: Story = {
	args: { variant: "full", value: "OFF" },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("radio", { name: "Off" })).toBeChecked();
	},
};

export const Compact: Story = {
	args: { variant: "compact", value: "OBSERVE" },
};

/** The state an inherited row is drawn in: shown, readable, and visibly somebody else's decision. */
export const Inherited: Story = {
	args: { variant: "compact", value: "OBSERVE", muted: true },
};

export const Choosing: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	args: { variant: "full", value: "DELIVER" },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("radio", { name: "Observe" }));
		await expect(args.onChange).toHaveBeenCalledWith("OBSERVE");
	},
};

/**
 * Propose has no approval queue behind it, so the server refuses it at every write boundary. The rung
 * stays on the ladder — without it there is no word for what sits between "records it" and "says it
 * unasked" — and cannot be moved to.
 */
export const ProposeIsNotSelectable: Story = {
	args: { variant: "full", value: "OBSERVE" },
	play: async ({ args, canvas, userEvent }) => {
		const propose = canvas.getByRole("radio", { name: "Propose" });
		await expect(propose).toHaveAttribute("aria-disabled", "true");
		// Clicking the rung's label is the reachable route to a disabled radio — a pointer never lands
		// on the control itself — so that is what has to write nothing.
		const rung = propose.closest("label");
		if (!(rung instanceof HTMLElement)) throw new Error("A rung with no label cannot be clicked");
		await userEvent.click(rung);
		await expect(args.onChange).not.toHaveBeenCalled();
		await expect(canvas.getByRole("radio", { name: "Observe" })).toBeChecked();
	},
};

/**
 * A workspace whose rows already hold Propose — the enum and the database CHECK both admit it — must
 * see the tier that is actually in force, and must still be able to reach that rung with a keyboard.
 */
export const ProposeAlreadyInForce: Story = {
	args: { variant: "full", value: "PROPOSE" },
	play: async ({ args, canvas, userEvent }) => {
		const propose = canvas.getByRole("radio", { name: "Propose" });
		await expect(propose).toBeChecked();
		await expect(propose).not.toHaveAttribute("aria-disabled", "true");
		// Re-selecting the rung in force writes nothing; only moving off it does.
		await userEvent.click(propose);
		await expect(args.onChange).not.toHaveBeenCalled();
		await userEvent.click(canvas.getByRole("radio", { name: "Observe" }));
		await expect(args.onChange).toHaveBeenCalledWith("OBSERVE");
	},
};

export const Disabled: Story = {
	args: { variant: "compact", value: "OFF", disabled: true },
	play: async ({ canvas }) => {
		for (const radio of canvas.getAllByRole("radio")) {
			await expect(radio).toHaveAttribute("aria-disabled", "true");
		}
	},
};
