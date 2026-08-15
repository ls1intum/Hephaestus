import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";
import { StatefulPatch } from "@/stories/stateful";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewPage } from "./ReviewPage";
import type { ReviewSectionId } from "./review-sections";

/**
 * The shell only ever receives its section bodies; it never builds them. Standing in for each one is
 * a marker that names itself, which is what lets these stories assert the property the real page
 * depends on: the two sections nobody opened are not in the document, so their data was never asked
 * for.
 *
 * The bodies themselves have their own stories — see `Review autonomy`, `Practice review settings`,
 * `Sweep schedule` and `Backfill` under Workspace admin/Practices.
 */
const sectionBody = (id: ReviewSectionId) => (
	<p className="rounded-md border border-dashed p-6 text-muted-foreground text-sm">
		Section body: {id}
	</p>
);

const sections = {
	"how-much": sectionBody("how-much"),
	"when-and-where": sectionBody("when-and-where"),
	"past-work": sectionBody("past-work"),
};

const readyBinding = { purpose: "PRACTICE_REVIEW", enabled: true, ready: true } as const;

const meta = {
	title: "Workspace admin/Practices/Review/Overview",
	component: ReviewPage,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 1440] },
		viewport: { defaultViewport: "reflow" },
	},
	tags: ["autodocs"],
	args: {
		section: "how-much",
		onSectionChange: fn(),
		running: {
			enabled: true,
			model: { binding: readyBinding, isLoading: false, isError: false },
		},
		sections,
	},
	// The open tab is URL state on the real page, so a story has to write the change back for the
	// tabs to move at all.
	render: (args) => (
		<StatefulPatch initial={{ section: args.section }}>
			{(view, patch) => (
				<ReviewPage
					{...args}
					section={view.section}
					onSectionChange={(section) => {
						args.onSectionChange(section);
						patch({ section });
					}}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ReviewPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const HowMuch: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("tab", { name: "How much" })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		await expect(canvas.getByText("Section body: how-much")).toBeVisible();
		await expectNoPageOverflow();
	},
};

export const WhenAndWhere: Story = {
	args: { section: "when-and-where" },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Section body: when-and-where")).toBeVisible();
		await expectNoPageOverflow();
	},
};

export const PastWork: Story = {
	args: { section: "past-work" },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Section body: past-work")).toBeVisible();
		await expectNoPageOverflow();
	},
};

/** Reviews are on and no model can run them: the settings below are a plan, not a behaviour. */
export const NoModelIsReady: Story = {
	args: { running: { enabled: true, model: { isLoading: false, isError: false } } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("status")).toHaveTextContent("Reviews can't start");
	},
};

/** The workspace is still loading: the tabs are there, and nothing is claimed about them yet. */
export const StateNotKnownYet: Story = {
	args: { running: undefined },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("status")).not.toBeInTheDocument();
		await expect(canvas.getByRole("tablist")).toBeVisible();
	},
};

/**
 * `role="tablist"` is what buys a screen reader the position announcement and the whole list as one
 * arrow-key stop instead of a tab stop each. Every accessible name is the visible label exactly,
 * which is what lets a voice-control user say "When and where" and mean this control (WCAG 2.5.3).
 *
 * The other half of what this asserts is the page's performance contract: only the open panel is
 * in the document, so the section a reader has not opened has not rendered — and therefore has run
 * no hooks and made no request. `-review-route.test.tsx` pins the same fact at the network.
 */
export const SectionsAreRealTabs: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		const list = canvas.getByRole("tablist");
		await expect(
			within(list)
				.getAllByRole("tab")
				.map((tab) => tab.textContent),
		).toEqual(["How much", "When and where", "Past work"]);
		await expect(canvas.getAllByRole("tabpanel")).toHaveLength(1);
		await expect(canvas.queryByText("Section body: when-and-where")).not.toBeInTheDocument();
		await expect(canvas.queryByText("Section body: past-work")).not.toBeInTheDocument();

		await userEvent.click(canvas.getByRole("tab", { name: "When and where" }));
		await expect(args.onSectionChange).toHaveBeenCalledWith("when-and-where");
		await expect(await canvas.findByText("Section body: when-and-where")).toBeVisible();
		await expect(canvas.queryByText("Section body: how-much")).not.toBeInTheDocument();
	},
};
