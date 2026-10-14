import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, screen, userEvent, waitFor, within } from "storybook/test";

import { InfoLines, InfoTip } from "@/components/common/InfoTip";
import { PAGE_INTRO } from "./across-workspace-copy";

const meta = {
	component: InfoTip,
	tags: ["autodocs"],
	args: {
		label: "About this page",
		children: <InfoLines lines={PAGE_INTRO} />,
	},
	render: (args) => (
		<p className="text-sm text-muted-foreground">
			How each practice goes across this workspace. <InfoTip {...args} />
		</p>
	),
} satisfies Meta<typeof InfoTip>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A press or a tap opens the panel, so a touch screen reads it too; Escape closes it. */
export const OpensOnPress: Story = {
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "About this page" }));
		const panel = await screen.findByRole("dialog", { name: "About this page" });
		await expect(panel).toHaveTextContent("can be wrong.");
		// The panel names itself first, and each line leads with its term in bold.
		await expect(panel.querySelector("p")).toHaveTextContent(/^About this page$/u);
		await expect(within(panel).getByText("Not a grade:").tagName).toBe("STRONG");
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
		const button = canvas.getByRole("button", { name: "About this page" });
		await expect(button).toHaveFocus();
		const panel = await screen.findByRole("dialog", { name: "About this page" });
		await waitFor(async () => {
			await expect(panel).toBeVisible();
		});
		await expect(panel).toHaveTextContent("can be wrong.");
		await expect(button).toHaveFocus();
	},
};

/** A pointer resting on the button opens the panel. */
export const OpensOnHover: Story = {
	play: async ({ canvas }) => {
		await userEvent.hover(canvas.getByRole("button", { name: "About this page" }));
		const panel = await screen.findByRole("dialog", { name: "About this page" });
		await waitFor(async () => {
			await expect(panel).toBeVisible();
		});
	},
};

/** Left open, as the panel reads in a screenshot. */
export const Open: Story = {
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "About this page" }));
		const panel = await screen.findByRole("dialog", { name: "About this page" });
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
		const panel = await screen.findByRole("dialog", { name: "About this page" });
		await expect(panel.getBoundingClientRect().right).toBeLessThanOrEqual(390);
	},
};
