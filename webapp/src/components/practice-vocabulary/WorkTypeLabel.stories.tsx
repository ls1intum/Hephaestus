import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { ITEM_KIND_VALUES } from "@/lib/item-kinds";

import { WorkTypeLabel } from "./WorkTypeLabel";

const meta = {
	component: WorkTypeLabel,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { itemKind: "scm.pull_request" },
} satisfies Meta<typeof WorkTypeLabel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const EveryKind: Story = {
	render: () => (
		<ul className="space-y-2 text-sm">
			{ITEM_KIND_VALUES.map((kind) => (
				<li key={kind}>
					<WorkTypeLabel itemKind={kind} />
				</li>
			))}
		</ul>
	),
	play: async ({ canvas }) => {
		// Every kind gets its own wording. A missing case falls through to the neutral label, which
		// reads fine on its own and is only wrong because another kind already says it.
		const labels = canvas.getAllByRole("listitem").map((item) => item.textContent.trim());
		await expect(new Set(labels).size).toBe(ITEM_KIND_VALUES.length);
	},
};

/**
 * A kind this build has never heard of takes the neutral glyph rather than borrowing another's, and
 * a generic name rather than its wire id.
 */
export const UnknownKind: Story = {
	args: { itemKind: "scm.something_new" },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Other work")).toBeVisible();
		await expect(canvas.queryByText(/scm\./u)).not.toBeInTheDocument();
	},
};

export const Missing: Story = {
	args: { itemKind: undefined },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Reviewed work")).toBeVisible();
	},
};
