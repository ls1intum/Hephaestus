import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, screen, userEvent, waitFor, within } from "storybook/test";

import { NEUTRAL_GREY } from "@/components/practice-vocabulary/standing-counts";

import { InfoLines, InfoTip } from "@/components/common/InfoTip";
import { NONE_YET_PLAIN } from "./across-workspace-copy";

const meta = {
	component: InfoTip,
	tags: ["autodocs"],
	args: {
		label: "About the bars",
		children: (
			<InfoLines
				lines={[
					{ ...NONE_YET_PLAIN, swatchClass: NEUTRAL_GREY },
					{ term: "You:", text: "the marker shows your part." },
				]}
			/>
		),
	},
	render: (args) => (
		<p className="text-sm text-muted-foreground">
			Last 30 days <InfoTip {...args} />
		</p>
	),
} satisfies Meta<typeof InfoTip>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A press or a tap opens the panel, so a touch screen reads it too; Escape closes it. */
export const OpensOnPress: Story = {
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "About the bars" }));
		const panel = await screen.findByRole("dialog", { name: "About the bars" });
		await expect(panel).toHaveTextContent("Grey does not mean okay.");
		// The panel names itself first, and each line leads with its term in bold.
		await expect(panel.querySelector("p")).toHaveTextContent(/^About the bars$/u);
		await expect(within(panel).getByText("Grey, None yet:").tagName).toBe("STRONG");
		await userEvent.keyboard("{Escape}");
		await waitFor(async () => {
			await expect(screen.queryByRole("dialog")).toBeNull();
		});
	},
};

/** A keyboard's focus opens the panel, and focus stays on the button. */
export const OpensOnKeyboardFocus: Story = {
	play: async ({ canvas }) => {
		await userEvent.tab();
		const trigger = canvas.getByRole("button", { name: "About the bars" });
		await expect(trigger).toHaveFocus();
		const panel = await screen.findByRole("dialog", { name: "About the bars" });
		await waitFor(async () => {
			await expect(panel).toBeVisible();
		});
		await expect(panel).toHaveTextContent("Grey does not mean okay.");
		await expect(trigger).toHaveFocus();
	},
};

/** A pointer resting on the button opens the panel. */
export const OpensOnHover: Story = {
	play: async ({ canvas }) => {
		await userEvent.hover(canvas.getByRole("button", { name: "About the bars" }));
		const panel = await screen.findByRole("dialog", { name: "About the bars" });
		await waitFor(async () => {
			await expect(panel).toBeVisible();
		});
	},
};

/** Left open, as the panel reads in a screenshot. */
export const Open: Story = {
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "About the bars" }));
		const panel = await screen.findByRole("dialog", { name: "About the bars" });
		await waitFor(async () => {
			await expect(panel).toBeVisible();
		});
	},
};

export const Dark: Story = { ...Open, globals: { theme: "dark" } };

/** At 390 px the panel keeps inside the screen. */
export const Phone: Story = {
	...Open,
	globals: { viewport: { value: "phone390" } },
	play: async (context) => {
		await Open.play?.(context);
		const panel = await screen.findByRole("dialog", { name: "About the bars" });
		await expect(panel.getBoundingClientRect().right).toBeLessThanOrEqual(390);
	},
};
