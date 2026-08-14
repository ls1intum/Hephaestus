import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, screen, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackListPage } from "./FeedbackListPage";
import { manyFeedback } from "./story-mock-data";
import { reviewHandlers } from "./story-mock-server";
import { StatefulSearch } from "./story-search-harness";

const meta = {
	title: "Workspace admin/Practice reviews/Delivery",
	component: FeedbackListPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
		msw: { handlers: reviewHandlers() },
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		search: { deliveryState: undefined, withheldFamily: undefined, channel: undefined },
		onSearchChange: fn(),
	},
	/** See `ObservationsListPage.stories`: a controlled screen needs somewhere to put its answer. */
	render: (args) => (
		<StatefulSearch initial={args.search}>
			{(search, onSearchChange) => (
				<FeedbackListPage {...args} search={search} onSearchChange={onSearchChange} />
			)}
		</StatefulSearch>
	),
} satisfies Meta<typeof FeedbackListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Eleven pieces of feedback: delivered, queued, failed, replaced, and withheld under each of the
 * four reasons an operator can filter by.
 *
 * The withheld rows are the ones worth looking at together. Each carries its own precise sentence,
 * and no two of them say the same thing — a set where every withheld row read "the work was already
 * merged" is what made the reason column look like decoration.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByText("11 pieces of feedback.");
		for (const name of ["Outcome", "Place", "Why withheld", "Recipient"]) {
			canvas.getByRole("combobox", { name });
		}
		canvas.getByText("Nearly the same as other feedback from the same review.");
		canvas.getByText("Found while reviewing past work, which is measured but never sent.");
		canvas.getByText("The developer has opted out of AI feedback.");
		canvas.getByText("The work was already merged, so a note on it would arrive too late.");
	},
};

/**
 * The long note, in the place it is hardest to show: a row two lines tall.
 *
 * The endpoint sends 320 characters of the stored Markdown, which on any real note lands inside a
 * fenced code block. The row flattens that to prose and marks the cut, so the title reads as the
 * opening of the feedback rather than as `**🔴 …** · \`File.java:118\` You wrote: ```java`.
 */
export const LongFeedbackReadsAsProse: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const row = await canvas.findByRole("link", { name: /2 issues to tighten in this change/ });
		await expect(row).toHaveAccessibleName(expect.stringContaining("…"));
		await expect(row).not.toHaveAccessibleName(expect.stringContaining("```"));
		await expect(canvas.queryByText(/```java/)).not.toBeInTheDocument();
	},
};

/** "Show me the delivery for one person" — the question the toolbar could not previously ask. */
export const FilterToOneRecipient: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("11 pieces of feedback.");
		await userEvent.click(canvas.getByRole("combobox", { name: "Recipient" }));
		const listbox = await screen.findByRole("listbox");
		await userEvent.click(
			await within(listbox).findByRole("option", { name: /Katherine Johnson/ }),
		);
		await canvas.findByRole("combobox", { name: "Recipient: Katherine Johnson" });
		await canvas.findByText("3 pieces of feedback match your filters.");
	},
};

/**
 * Four families instead of fourteen sentences, which is what let the reason filter onto the toolbar
 * at all. A row still shows its own precise sentence; the grouping simplifies the question.
 */
export const WhyWithheldFacetOpen: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Why withheld" }));
		const listbox = await screen.findByRole("listbox");
		await within(listbox).findByRole("option", { name: /The work moved on/ });
		within(listbox).getByRole("option", { name: /The developer's choice/ });
	},
};

/** Filtering by a family returns exactly the rows whose own sentence sits under that heading. */
export const FilterToOneWithholdingFamily: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("11 pieces of feedback.");
		await userEvent.click(canvas.getByRole("combobox", { name: "Why withheld" }));
		const listbox = await screen.findByRole("listbox");
		await userEvent.click(await within(listbox).findByRole("option", { name: /Housekeeping/ }));
		await userEvent.keyboard("{Escape}");
		await canvas.findByText("1 piece of feedback matches your filters.");
		canvas.getByText("Nearly the same as other feedback from the same review.");
	},
};

/**
 * Sixty rows, opened at the second page. See `ObservationsListPage.stories` on why the page is set
 * rather than clicked.
 */
export const MoreThanOnePage: Story = {
	args: {
		search: {
			page: 1,
			deliveryState: undefined,
			withheldFamily: undefined,
			channel: undefined,
		},
	},
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: reviewHandlers({ feedback: manyFeedback(60) }) },
	},
	play: async ({ canvas }) => {
		await canvas.findByText("60 pieces of feedback.");
		const current = await canvas.findByRole("link", { name: "Go to page 2" });
		await expect(current).toHaveAttribute("aria-current", "page");
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		await within(canvasElement).findByText("11 pieces of feedback.");
		await expectNoPageOverflow();
	},
};

export const LoadFailed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get(
					"*/workspaces/:workspaceSlug/practices/reviews/feedback",
					() => new HttpResponse(null, { status: 500 }),
				),
				...reviewHandlers(),
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByText("Couldn't load feedback");
	},
};
