import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import { StatefulPatch } from "@/stories/stateful";
import { precedes } from "@/test/dom";

import { clearedScope } from "./review-scope";
import { clearedReviewFacets, ReviewFilters } from "./ReviewFilters";
import { STORY_GROUPS, STORY_PEOPLE, STORY_PRACTICES } from "./story-scope-sources";

const meta = {
	component: ReviewFilters,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	tags: ["autodocs"],
	args: {
		search: { status: undefined },
		onPatch: fn(),
		onReset: fn(),
		onClearScope: fn(),
		groups: STORY_GROUPS,
		practices: STORY_PRACTICES,
		people: STORY_PEOPLE,
		total: 7,
	},
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, onPatch) => (
				<ReviewFilters
					{...args}
					search={search}
					onPatch={(patch) => {
						args.onPatch(patch);
						onPatch(patch);
					}}
					onReset={() => {
						args.onReset();
						onPatch(clearedReviewFacets());
					}}
					onClearScope={() => {
						args.onClearScope();
						onPatch(clearedScope());
					}}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ReviewFilters>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Unfiltered: Story = {
	play: async ({ canvas }) => {
		canvas.getByText("7 reviews.");
		await expect(canvas.queryByRole("button", { name: "Reset" })).not.toBeInTheDocument();
	},
};

export const Filtered: Story = {
	args: {
		search: { status: ["COMPLETE"], from: "2026-07-28", to: "2026-07-29" },
		total: 2,
	},
	play: async ({ canvas }) => {
		canvas.getByText("2 reviews match your filters.");
		canvas.getByRole("button", { name: "Created: 28 Jul – 29 Jul 2026" });
		canvas.getByRole("button", { name: /Reset/u });
	},
};

export const OneMatch: Story = {
	args: { search: { status: ["FAILED"] }, total: 1 },
	play: async ({ canvas }) => {
		canvas.getByText("1 review matches your filters.");
	},
};

/**
 * No total yet. The count renders nothing rather than a zero, because a zero here would announce an
 * empty list a moment before the full one arrives.
 */
export const CountNotInYet: Story = {
	args: { total: undefined },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("status")).not.toBeInTheDocument();
	},
};

export const ChoosingStatuses: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const trigger = canvas.getByRole("combobox", { name: "Status" });
		await userEvent.click(trigger);
		const listbox = await screen.findByRole("listbox", { name: "Status options" });
		await userEvent.click(within(listbox).getByRole("option", { name: /Failed/u }));
		await expect(args.onPatch).toHaveBeenLastCalledWith({ status: ["FAILED"] });
		await userEvent.click(within(listbox).getByRole("option", { name: /Partial/u }));
		await expect(args.onPatch).toHaveBeenLastCalledWith({ status: ["FAILED", "PARTIAL"] });
		await userEvent.click(trigger);
		await waitFor(async () => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());
	},
};

export const ResettingKeepsTheScope: Story = {
	args: {
		search: { status: ["FAILED"], from: "2026-07-28", to: "2026-07-29" },
		total: 2,
	},
	play: async ({ args, canvas, userEvent }) => {
		canvas.getByTitle("Status: Failed");
		await userEvent.click(canvas.getByRole("button", { name: /Reset/u }));
		await expect(args.onReset).toHaveBeenCalledTimes(1);
		await expect(canvas.queryByTitle("Status: Failed")).not.toBeInTheDocument();
		await expect(
			canvas.getByRole("button", { name: "Created: 28 Jul – 29 Jul 2026" }),
		).toBeVisible();
		await expect(canvas.queryByRole("button", { name: /Reset/u })).not.toBeInTheDocument();
		canvas.getByText("2 reviews match your filters.");

		await userEvent.click(canvas.getByRole("button", { name: "Clear scope" }));
		await expect(args.onClearScope).toHaveBeenCalledTimes(1);
		await expect(canvas.getByRole("button", { name: "Created" })).toBeVisible();
		canvas.getByText("2 reviews.");
	},
};

export const SelectionApplies: Story = {
	args: {
		search: {
			status: undefined,
			reviewId: "3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84",
			selected: true,
		},
	},
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.queryByText("Not applied on this list")).not.toBeInTheDocument();
		await expect(canvas.queryByText(/3f2b6c1e/u)).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Clear scope" }));
		await expect(args.onClearScope).toHaveBeenCalledOnce();
	},
};

export const ScopeCarriedFromAnotherTab: Story = {
	args: {
		search: {
			status: undefined,
			developerId: STORY_PEOPLE.options[0]?.accountId,
			practiceSlug: [STORY_PRACTICES.options[0]?.value ?? ""],
		},
		total: 2,
	},
	play: async ({ canvas }) => {
		canvas.getByText("2 reviews match your filters.");
		await expect(canvas.getByRole("combobox", { name: /Developer/u })).toHaveTextContent(
			STORY_PEOPLE.options[0]?.label ?? "",
		);
		await expect(canvas.queryByText("Not applied on this list")).not.toBeInTheDocument();
	},
};

function controlsOf(group: HTMLElement) {
	return Array.from(
		group.querySelectorAll("button"),
		(control) => control.getAttribute("aria-label") ?? control.textContent.trim(),
	);
}

export const SharedOrder: Story = {
	play: async ({ canvas }) => {
		const scope = canvas.getByRole("group", { name: "Applies to every tab" });
		const filters = canvas.getByRole("group", { name: "Filters" });
		await expect(controlsOf(scope)).toStrictEqual(["Developer", "Group", "Practice", "Created"]);
		await expect(controlsOf(filters)).toStrictEqual(["Status"]);
		await expect(precedes(scope, filters)).toBe(true);
	},
};
