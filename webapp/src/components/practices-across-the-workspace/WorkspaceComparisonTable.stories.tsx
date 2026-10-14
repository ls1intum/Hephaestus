import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent } from "storybook/test";

import { NO_OUTCOMES, outcomes } from "@/stories/practices-across-the-workspace-story-data";

import { type ComparisonRow, WorkspaceComparisonTable } from "./WorkspaceComparisonTable";

const row = (
	key: string,
	name: string,
	count: string,
	counts: ComparisonRow["outcomes"],
): ComparisonRow => ({
	key,
	name,
	subject: <span className="font-medium">{name}</span>,
	count,
	outcomes: counts,
});

const onOpen = fn();

const ROWS: ComparisonRow[] = [
	row("acting", "Acting on review feedback", "2 practices", outcomes([25, 16, 5, 1])),
	row("failure", "Handling failure well", "3 practices", outcomes([4, 9, 9, 0])),
	row("craft", "Writing maintainable code", "1 practice", NO_OUTCOMES),
	row("testing", "Testing your changes", "4 practices", outcomes([10, 17, 2, 4])),
];

/**
 * One table for practice groups and for practices: the caller decides the subject cell, what the
 * count column counts and the row link.
 */
const meta = {
	component: WorkspaceComparisonTable,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
	args: {
		"aria-label": "All practice groups",
		subjectHead: "Practice group",
		countHead: "Practices",
		state: { status: "ready", rows: ROWS },
		rowLink: () => ({ text: "Open group", onOpen }),
		empty: { title: "No practices set up yet", description: "They appear once set up." },
	},
	argTypes: { rowLink: { control: false } },
} satisfies Meta<typeof WorkspaceComparisonTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Each row with its count and its results; a row with none says so instead of four zeros. */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", { name: "All practice groups" })).toBeVisible();
		await expect(canvas.getByRole("columnheader", { name: "Results" })).toBeVisible();
		const testing = canvas
			.getAllByRole("row")
			.find((each) => each.textContent.startsWith("Testing"));
		await expect(testing?.textContent).toContain("4 practices");
		await expect(testing?.textContent).toContain("Not met 17");
		const craft = canvas.getAllByRole("row").find((each) => each.textContent.startsWith("Writing"));
		await expect(craft?.textContent).toContain("No results yet");
		await userEvent.click(canvas.getByRole("button", { name: "Open group Handling failure well" }));
		await expect(onOpen).toHaveBeenCalledOnce();
	},
};

/** The row the reader came from in their Practice profile, marked in words as well. */
export const Marked: Story = {
	args: { markedKey: "failure" },
	play: async ({ canvas }) => {
		const marked = canvas
			.getAllByRole("row")
			.filter((each) => each.getAttribute("aria-current") === "true");
		await expect(marked).toHaveLength(1);
		await expect(marked[0]?.textContent).toContain("where you came from");
	},
};

/** Each loading row in the shape of the row it stands for, its link cell left empty. */
export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table")).toHaveAttribute("aria-busy", "true");
		await expect(canvas.queryAllByRole("button")).toHaveLength(0);
	},
};

export const Empty: Story = {
	args: { state: { status: "ready", rows: [] } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No practices set up yet")).toBeVisible();
	},
};

export const Phone: Story = {
	globals: { viewport: { value: "phone390" } },
};
