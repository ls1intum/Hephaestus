import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import type { PracticeStanding } from "@/api/types.gen";
import { PracticePill } from "@/components/practice-vocabulary/PracticePill";
import {
	PracticeTable,
	PracticeTableRow,
	StandingCell,
	SubjectCell,
} from "@/components/practice-vocabulary/PracticeTable";

import { expectFoldOnTableColumns } from "@/stories/fold-columns";

import { SAMPLE_NEVER_APPLIED, SAMPLE_NOT_OBSERVED } from "@/stories/practice-profile-samples";
import {
	type FoldReason,
	FoldedPractices,
	type FoldedPracticesProps,
	FoldedStandingCell,
} from "./FoldedPractices";

const onOpen = fn();

const standingsOf = (
	items: readonly { slug: string; name: string }[],
	standing: PracticeStanding["standing"],
): PracticeStanding[] => items.map((item) => ({ ...item, standing, strengths: [], toWorkOn: [] }));

const SHOWN_ROWS: PracticeStanding[] = [
	{
		slug: "describe-change",
		name: "Describe what changed and why",
		standing: "DEVELOPING",
		direction: "IMPROVING",
		strengths: [],
		toWorkOn: [],
	},
	{
		slug: "small-diff",
		name: "Keep the diff reviewable in one sitting",
		standing: "STRENGTH",
		strengths: [],
		toWorkOn: [],
	},
];
const NEVER_APPLIED_ROWS = standingsOf(SAMPLE_NEVER_APPLIED, "NO_OPPORTUNITY");
const NOT_OBSERVED_ROWS = standingsOf(SAMPLE_NOT_OBSERVED, "NOT_OBSERVED");

function PracticeRow({ practice, folded }: { practice: PracticeStanding; folded?: FoldReason }) {
	return (
		<PracticeTableRow
			link={{
				text: "Open practice",
				name: practice.name,
				onOpen: () => {
					onOpen(practice.slug);
				},
			}}
		>
			{folded ? (
				<FoldedStandingCell scope="practice" reason={folded} />
			) : (
				<StandingCell
					standing={practice.standing}
					direction={practice.direction}
					scope="practice"
				/>
			)}
			<SubjectCell badge={<PracticePill name={practice.name} />} />
		</PracticeTableRow>
	);
}

type FoldStoryArgs = Omit<FoldedPracticesProps, "columns" | "children"> & {
	practices: readonly PracticeStanding[];
};

/**
 * The fold as a group's level draws it: the practice table, and under its rows, in the same table,
 * the fold's toggle row and its rows in their plain state.
 */
function FoldStory({ practices, ...fold }: FoldStoryArgs) {
	return (
		<PracticeTable
			aria-label="Practices in this group"
			sort="desc"
			onSortChange={fn()}
			subjectHead="Practice"
			rows={SHOWN_ROWS}
			rowKey={(practice) => practice.slug}
			renderRow={(practice) => <PracticeRow practice={practice} />}
			empty={{ icon: null, title: "", description: "" }}
			sections={(columns) => (
				<FoldedPractices {...fold} columns={columns}>
					{practices.map((practice) => (
						<PracticeRow
							key={practice.slug}
							practice={practice}
							folded={fold.reason ?? "never-applied"}
						/>
					))}
				</FoldedPractices>
			)}
		/>
	);
}

/**
 * Practices or groups folded into one row at the end of
 * their table instead of reading as a gap. The row counts nothing. Opened, its rows follow it in the
 * same table, on the table's own columns, each in its plain state, and each opens as any row does.
 */
const meta = {
	component: FoldStory,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		count: NEVER_APPLIED_ROWS.length,
		noun: "practice",
		practices: NEVER_APPLIED_ROWS,
	},
	argTypes: { practices: { control: false } },
} satisfies Meta<typeof FoldStory>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Folded: Story = {
	play: async ({ canvas, userEvent }) => {
		const trigger = canvas.getByRole("button", { name: "Does not apply to your work yet" });
		await expect(trigger).toHaveAttribute("aria-expanded", "false");
		// The row names the fold and counts nothing.
		await expect(trigger.closest("tr")?.textContent).not.toMatch(/\d/u);
		// A plain disclosure: at least 32 px high, and no border of its own.
		await expect(trigger.getBoundingClientRect().height).toBeGreaterThanOrEqual(32);
		await expect(getComputedStyle(trigger).borderTopWidth).toBe("0px");
		await userEvent.click(trigger);
		// One table: the fold adds no second one and no second header.
		await expect(canvas.getAllByRole("table")).toHaveLength(1);
		await expect(canvas.getAllByRole("columnheader")).toHaveLength(3);
		const rows = canvas.getByRole("rowgroup", { name: "Does not apply to your work yet" });
		await userEvent.click(
			within(rows).getByRole("button", { name: "Open practice Document the public API" }),
		);
		await expect(onOpen).toHaveBeenCalledWith("write-api-docs");
	},
};

export const Open: Story = {
	args: { defaultOpen: true },
	play: async ({ canvas }) => {
		const rows = canvas.getByRole("rowgroup", { name: "Does not apply to your work yet" });
		// Each row in its plain state, the badge alone: no trend note under it. The badge says what
		// the fold says, as the practice's own level does.
		await expect(within(rows).getAllByText("Does not apply to your work yet")).toHaveLength(3);
		await expect(within(rows).queryByText("Nothing to report yet")).toBeNull();
		await expect(within(rows).queryByText(/compare/u)).toBeNull();
	},
};

export const Dark: Story = { args: { defaultOpen: true }, globals: { theme: "dark" } };

export const Phone: Story = {
	args: { defaultOpen: true },
	globals: { viewport: { value: "phone390" } },
};

// --- Not observed yet ---------------------------------------------------------------------------

const notObservedArgs = {
	count: NOT_OBSERVED_ROWS.length,
	reason: "not-observed",
	practices: NOT_OBSERVED_ROWS,
} as const;

/** A group's practices that no review has observed yet, folded. */
export const NotObservedPracticesFolded: Story = {
	args: notObservedArgs,
	play: async ({ canvas, userEvent }) => {
		const trigger = canvas.getByRole("button", { name: "Practices not observed yet" });
		await expect(trigger).toHaveAttribute("aria-expanded", "false");
		await userEvent.click(trigger);
		await expect(trigger).toHaveAttribute("aria-expanded", "true");
		await expect(canvas.getByText(/No review has observed these practices/u)).toBeVisible();
		const rows = canvas.getByRole("rowgroup", { name: "Practices not observed yet" });
		await expect(within(rows).getAllByText("Not observed yet")).toHaveLength(2);
	},
};

/** Opened, every folded row's cells start where the cells of the rows above start. */
export const NotObservedPracticesOpen: Story = {
	args: { ...notObservedArgs, defaultOpen: true },
	globals: { viewport: { value: "desktop" } },
	play: async ({ canvas }) => {
		const shown = canvas.getAllByRole("row").find((row) => row.textContent.includes("Describe"));
		await expectFoldOnTableColumns(
			shown ?? null,
			canvas.getByRole("rowgroup", { name: "Practices not observed yet" }),
		);
	},
};

export const NotObservedDark: Story = {
	args: { ...notObservedArgs, defaultOpen: true },
	globals: { theme: "dark" },
};

export const NotObservedPhone: Story = {
	args: { ...notObservedArgs, defaultOpen: true },
	globals: { viewport: { value: "phone390" } },
};
