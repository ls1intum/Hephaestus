import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "storybook/test";
import type { InstanceSettings } from "@/api/types.gen";
import { SilentModeBanner } from "./SilentModeBanner";

const engaged: InstanceSettings = {
	silentModeEngaged: true,
	silentModeReason: "Investigating incident #42 — bad feedback going out",
	silentModeChangedAt: new Date(Date.now() - 45 * 60_000),
	silentModeChangedBy: "felixtjdietrich",
};

const meta = {
	component: SilentModeBanner,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { settings: engaged },
} satisfies Meta<typeof SilentModeBanner>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Engaged: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/silent mode is engaged/i, { exact: false })).toBeInTheDocument();
		await expect(canvas.getByText(/incident #42/i, { exact: false })).toBeInTheDocument();
		await expect(canvas.getByRole("link", { name: /manage/i })).toBeInTheDocument();
	},
};

/** An engaged brake with no breadcrumbs (e.g. set before the changed-by tracking existed). */
export const WithoutMetadata: Story = {
	args: {
		settings: { silentModeEngaged: true },
	},
};
