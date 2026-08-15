import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, screen, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { StatefulPatch } from "@/stories/stateful";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackListPage } from "./FeedbackListPage";
import { manyFeedback } from "./story-mock-data";
import { reviewHandlers } from "./story-mock-server";

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
	// The screen is controlled: with a frozen `search` prop every facet reads as dead.
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, onSearchChange) => (
				<FeedbackListPage {...args} search={search} onSearchChange={onSearchChange} />
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof FeedbackListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * The withheld rows are what this set exists for: each carries its own precise sentence, and no two
 * of them say the same thing, which is what keeps the reason from reading as decoration.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByText("11 pieces of feedback.");
		for (const name of ["Outcome", "Place", "Why withheld", "Recipient"]) {
			canvas.getByRole("combobox", { name });
		}
		// "Composed" is neither when the feedback was delivered nor when the observation was made.
		canvas.getByRole("button", { name: "Composed" });
		canvas.getByText("Nearly the same as other feedback from the same review.");
		canvas.getByText("Found while reviewing past work, which is measured but never sent.");
		canvas.getByText("The developer has opted out of AI feedback.");
		canvas.getByText("The work was already merged, so a note on it would arrive too late.");
	},
};

/**
 * The endpoint sends a fixed-character cut of the stored Markdown, which on a real note ends inside
 * a fenced code block. The row has to flatten that to prose and mark the cut, or the title reads as
 * `**🔴 …** · \`File.java:118\` You wrote: ```java`.
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
 * The facet offers families rather than every withholding reason, which is what fits it on the
 * toolbar. A row still shows its own precise sentence; only the question is grouped.
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
		const trigger = canvas.getByRole("combobox", { name: "Why withheld" });
		await userEvent.click(trigger);
		const listbox = await screen.findByRole("listbox", { name: "Why withheld options" });
		await userEvent.click(await within(listbox).findByRole("option", { name: /Housekeeping/ }));
		// Closed again: these facets are multi-select and stay open after a choice, so the next facet
		// would otherwise put a second listbox on screen.
		await userEvent.click(trigger);
		await canvas.findByText("1 piece of feedback matches your filters.");
		canvas.getByText("Nearly the same as other feedback from the same review.");

		// A place nothing under that family went to, so the two filters intersect to nothing.
		await userEvent.click(canvas.getByRole("combobox", { name: "Place" }));
		const places = await screen.findByRole("listbox", { name: "Place options" });
		await userEvent.click(await within(places).findByRole("option", { name: /In conversation/ }));
		await canvas.findByText("No feedback matches these filters");
		await userEvent.click(canvas.getByRole("button", { name: "Clear all filters" }));
		await canvas.findByText("11 pieces of feedback.");
	},
};

export const MoreThanOnePage: Story = {
	// The page is set rather than clicked: pagination is real links, so the page travels through the
	// router, and Storybook mounts this screen under a single bare route.
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
