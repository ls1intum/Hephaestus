import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import { AuditDateFacet } from "./AuditDateFacet";

/**
 * The date facet of the audit toolbar: a dashed trigger that summarises the picked range as a badge,
 * so the row reads as one control set rather than a calendar bolted onto a filter bar.
 */
const meta = {
	component: AuditDateFacet,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { value: undefined, onChange: fn() },
} satisfies Meta<typeof AuditDateFacet>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing picked — the trigger is just "Date", with no badge claiming a filter is active. */
export const Empty: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByRole("button", { name: "Date" })).toBeVisible();
	},
};

/**
 * A half-open range: the user has clicked a start day and not yet an end. It reads "From …" rather
 * than inventing an end date, because the filter really is open-ended until the second click.
 */
export const StartOnly: Story = {
	args: { value: { from: new Date(2026, 6, 1) } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("From Jul 1, 2026")).toBeVisible();
	},
};

/** A closed range — the badge states both ends, with the year carried once. */
export const FullRange: Story = {
	args: { value: { from: new Date(2026, 6, 1), to: new Date(2026, 6, 24) } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Jul 1 – Jul 24, 2026")).toBeVisible();
	},
};

/** A range spanning two months still reads unambiguously. */
export const AcrossMonths: Story = {
	args: { value: { from: new Date(2026, 5, 28), to: new Date(2026, 6, 3) } },
};

/** The trigger is a real disclosure: it reports whether the calendar is open to assistive tech. */
export const OpenedCalendar: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = await canvas.findByRole("button", { name: "Date" });

		await expect(trigger).toHaveAttribute("aria-expanded", "false");
		await userEvent.click(trigger);
		await expect(trigger).toHaveAttribute("aria-expanded", "true");
	},
};
