import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen } from "storybook/test";
import { ModelAccessScopeChoice } from "./ModelAccessScopeChoice";

const meta = {
	component: ModelAccessScopeChoice,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		idPrefix: "story-access",
		label: "Initial workspace access",
		value: "ALL",
		onChange: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-md">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof ModelAccessScopeChoice>;

export default meta;
type Story = StoryObj<typeof meta>;

const TRANSPARENT = "rgba(0, 0, 0, 0)";

function cardOf(name: RegExp): HTMLElement {
	const card = screen.getByRole("radio", { name }).closest("label");
	if (!card) throw new Error(`No card wraps the ${name} radio`);
	return card;
}

/**
 * The whole card of the chosen answer is tinted and takes a primary border, not just the radio dot.
 *
 * Asserted against computed style rather than a class name because the tint is a `has-data-checked:`
 * rule on the kit's `FieldLabel`: the class only pays out if the checked radio is actually a
 * descendant of the label carrying it, which is the part a hand-rolled card gets wrong.
 */
export const AllWorkspaces: Story = {
	play: async () => {
		const chosen = cardOf(/^All workspaces/i);
		const other = cardOf(/^Selected workspaces/i);

		await expect(screen.getByRole("radio", { name: /^All workspaces/i })).toHaveAttribute(
			"data-checked",
		);
		await expect(getComputedStyle(chosen).backgroundColor).not.toBe(TRANSPARENT);
		await expect(getComputedStyle(other).backgroundColor).toBe(TRANSPARENT);
		await expect(getComputedStyle(chosen).borderTopColor).not.toBe(
			getComputedStyle(other).borderTopColor,
		);
	},
};

/** The same highlight follows the choice to the second card. */
export const SelectedWorkspaces: Story = {
	args: { value: "SELECTED" },
	play: async () => {
		const chosen = cardOf(/^Selected workspaces/i);
		const other = cardOf(/^All workspaces/i);

		await expect(getComputedStyle(chosen).backgroundColor).not.toBe(TRANSPARENT);
		await expect(getComputedStyle(other).backgroundColor).toBe(TRANSPARENT);
	},
};
