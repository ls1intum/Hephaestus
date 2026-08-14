import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { fn, screen, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackListPage } from "./FeedbackListPage";
import { reviewFeedback, workspaceMembers } from "./story-mock-data";
import { StatefulSearch } from "./story-search-harness";

const meta = {
	title: "Workspace admin/Practice reviews/Delivery",
	component: FeedbackListPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
					HttpResponse.json({
						content: reviewFeedback,
						page: {
							number: 0,
							size: 25,
							totalElements: reviewFeedback.length,
							totalPages: 1,
						},
					}),
				),
				http.get("*/workspaces/:workspaceSlug/members", () => HttpResponse.json(workspaceMembers)),
			],
		},
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

export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByText(`${reviewFeedback.length} pieces of feedback.`);
		for (const name of ["Outcome", "Place", "Why withheld", "Recipient"]) {
			canvas.getByRole("combobox", { name });
		}
	},
};

/** "Show me the delivery for one person" — the question the toolbar could not previously ask. */
export const FilterToOneRecipient: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText(`${reviewFeedback.length} pieces of feedback.`);
		await userEvent.click(canvas.getByRole("combobox", { name: "Recipient" }));
		const listbox = await screen.findByRole("listbox");
		await userEvent.click(await within(listbox).findByRole("option", { name: /Alan Turing/ }));
		await canvas.findByRole("combobox", { name: "Recipient: Alan Turing" });
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

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		await within(canvasElement).findByText(`${reviewFeedback.length} pieces of feedback.`);
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
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByText("Couldn't load feedback");
	},
};
