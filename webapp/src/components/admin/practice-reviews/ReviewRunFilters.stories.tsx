import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";
import { StatefulPatch } from "@/stories/stateful";
import { ReviewRunFilters } from "./ReviewRunFilters";
import type { RunsSearch } from "./review-search";

/**
 * The toolbar above the Reviews list: a status, a requested-on window, and the count of what they
 * matched. It owns no data — it reports a patch and is handed the total, so the number beside it
 * always describes the answer on screen rather than the request in flight.
 *
 * The status options wear the same badges the rows do. A dropdown of plain words would make choosing
 * a filter an act of memory: matching a word here to a tag there.
 */
const meta = {
	title: "Workspace admin/Practice reviews/Building blocks/Review run filters",
	component: ReviewRunFilters,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	tags: ["autodocs"],
	args: {
		search: {},
		onPatch: fn(),
		onReset: fn(),
		hasFilter: false,
		total: 7,
	},
	// Controlled, so a frozen `search` would leave every control inert while nothing looks broken.
	render: (args) => (
		<StatefulPatch initial={args.search as RunsSearch}>
			{(search, onPatch) => (
				<ReviewRunFilters
					{...args}
					search={search}
					onPatch={(patch) => {
						args.onPatch(patch);
						onPatch(patch);
					}}
					hasFilter={Boolean(search.status || search.from || search.to)}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ReviewRunFilters>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing chosen: the count says how many reviews exist, and there is nothing to reset. */
export const Unfiltered: Story = {
	play: async ({ canvas }) => {
		canvas.getByText("7 reviews.");
		await expect(canvas.queryByRole("button", { name: "Reset" })).not.toBeInTheDocument();
	},
};

/** Chosen: the count says what survived, and Reset appears to undo all of it at once. */
export const Filtered: Story = {
	args: { search: { status: "COMPLETED", from: "2026-07-28", to: "2026-07-29" }, total: 2 },
	play: async ({ canvas }) => {
		canvas.getByText("2 reviews match your filters.");
		canvas.getByRole("button", { name: "Requested: Jul 28 – Jul 29, 2026" });
		canvas.getByRole("button", { name: /Reset/ });
	},
};

/** One row is still "matches", not "match": the verb agrees with the count, not with the noun. */
export const OneMatch: Story = {
	args: { search: { status: "FAILED" }, total: 1 },
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

/** Choosing a status reports the patch — and resets the page, since page 4 of a new filter is not
 * a page anybody asked for. */
export const ChoosingAStatus: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox"));
		const listbox = await screen.findByRole("listbox");
		await userEvent.click(within(listbox).getByRole("option", { name: /Failed/ }));

		await expect(args.onPatch).toHaveBeenCalledWith({ status: "FAILED", page: 0 });
		await expect(canvas.getByRole("combobox")).toHaveTextContent("Failed");
	},
};
