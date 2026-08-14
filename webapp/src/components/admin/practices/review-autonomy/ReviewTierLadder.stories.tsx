import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import { Stateful } from "@/stories/stateful";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewTierLadder } from "./ReviewTierLadder";

const meta = {
	title: "Workspace admin/Practices/Review/Tier ladder",
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
	/**
	 * The rung a story starts on is `args.value`; clicking another moves it. With only `fn()` behind
	 * `onChange` the ladder could not be operated at all — every rung stayed where the args put it,
	 * which is the one thing a ladder has to be able to do.
	 */
	render: (args) => (
		<Stateful initial={args.value}>
			{(value, setValue) => (
				<ReviewTierLadder
					{...args}
					value={value}
					onChange={(next) => {
						args.onChange(next);
						setValue(next);
					}}
				/>
			)}
		</Stateful>
	),
} satisfies Meta<typeof ReviewTierLadder>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Full: Story = {
	args: { variant: "full" },
	play: async ({ canvas }) => {
		// Radios, not pressed buttons: the rungs are three states of one setting, and a screen reader
		// should say "Deliver, selected, 3 of 3" rather than "Off, not pressed" twice over.
		await expect(canvas.getAllByRole("radio")).toHaveLength(3);
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
	args: { variant: "compact", value: "PROPOSE" },
};

/** How an inherited row is drawn: readable, and visibly somebody else's decision. */
export const Inherited: Story = {
	args: { variant: "compact", value: "PROPOSE", muted: true },
};

export const Choosing: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	args: { variant: "full", value: "DELIVER" },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("radio", { name: "Propose" }));
		await expect(args.onChange).toHaveBeenCalledWith("PROPOSE");
	},
};

export const ReSelectingTheRungInForceWritesNothing: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	args: { variant: "full", value: "PROPOSE" },
	play: async ({ args, canvas, userEvent }) => {
		const propose = canvas.getByRole("radio", { name: "Propose" });
		await expect(propose).toBeChecked();
		await userEvent.click(propose);
		await expect(args.onChange).not.toHaveBeenCalled();
		await userEvent.click(canvas.getByRole("radio", { name: "Deliver" }));
		await expect(args.onChange).toHaveBeenCalledWith("DELIVER");
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
