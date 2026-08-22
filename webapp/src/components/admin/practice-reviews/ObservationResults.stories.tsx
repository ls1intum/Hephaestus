import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen } from "storybook/test";
import type { ReviewObservation } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { ObservationResults } from "./ObservationResults";
import { reviewObservations, workspacePractices } from "./story-mock-data";

const longContent = {
	...reviewObservations[0],
	id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
	summary:
		"The review keeps every boundary visible even when the observation needs enough words to wrap across a narrow screen twice over",
	subject: {
		id: 10,
		login: "alexandria-occasional-contributor",
		name: "Alexandria Catherine Montgomery-Worthington",
	},
} satisfies ReviewObservation;

/** Storybook resets a spy that appears in `args` between runs, so one instance is enough. */
const clearFilters = fn();

/** The practice several of the fixture's observations name, and the one the hover card is read on. */
const THIN_CONTROLLERS = workspacePractices[0];

const meta = {
	title: "Workspace admin/Practice reviews/Building blocks/Observation results",
	component: ObservationResults,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 768, 1440] },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		state: { status: "ready", observations: reviewObservations },
		// A row names its practice but carries none of its prose, so the screen hands the list down and
		// each row reads its own record out of it. Handed over as a prop, not fetched: see
		// `ReviewPracticeLink`.
		practices: workspacePractices,
	},
} satisfies Meta<typeof ObservationResults>;

export default meta;
type Story = StoryObj<typeof meta>;

/** One row per observation, at every conclusion the model can reach. */
export const Default: Story = {
	play: async ({ canvas }) => {
		// A shortfall shows its assessment and its severity; a presence that ends the question shows
		// only itself.
		await expect(canvas.getAllByText("Needs improvement")).toHaveLength(6);
		canvas.getByText("Critical");
		canvas.getByText("Not applicable");
		canvas.getByText("Could not be determined");
		await expect(canvas.getAllByText("From a review of past work")).toHaveLength(2);
		await expect(canvas.getAllByText("Requested by hand")).toHaveLength(2);
		await expect(canvas.queryAllByText("No result")).toHaveLength(0);
	},
};

/**
 * The practice on a row does two things: it opens the practice, and it says what the practice is
 * without leaving the list. Both are checked, because the card is the half that goes quiet on its
 * own — a row that stops being handed its practice record still renders a perfectly good link.
 */
export const PracticeOpensItsDefinition: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		// Several observations name this practice, and every one of them reaches the same definition.
		const links = await canvas.findAllByRole("link", { name: /Thin controllers/ });
		for (const link of links) {
			await expect(link).toHaveAttribute("href", "/w/demo/admin/practices/thin-controllers");
		}
		// The card is a portal, so it is looked for on the whole screen rather than in the canvas.
		await userEvent.hover(links[0]);
		await screen.findByText(THIN_CONTROLLERS.whyItMatters ?? "");
		await screen.findByText(THIN_CONTROLLERS.whatGoodLooksLike ?? "");
	},
};

/**
 * A caller that has no practice list — one still loading, or a screen that never fetched it — keeps
 * every link and simply shows no card. Nothing the card holds is load-bearing: the name and the area
 * are on the row, and the rest is a field on the page the link opens.
 */
export const WithoutPracticeRecords: Story = {
	args: { practices: undefined },
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		const link = (await canvas.findAllByRole("link", { name: /Thin controllers/ }))[0];
		await expect(link).toHaveAttribute("href", "/w/demo/admin/practices/thin-controllers");
		await userEvent.hover(link);
		await expect(screen.queryByText(THIN_CONTROLLERS.whyItMatters ?? "")).not.toBeInTheDocument();
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		canvas.getByRole("link", {
			name: "The queue is called the outbox everywhere except in the config",
		});
		await expectNoPageOverflow();
	},
};

export const LongContent: Story = {
	args: { state: { status: "ready", observations: [longContent] } },
	parameters: {
		chromatic: { viewports: [320] },
		viewport: { defaultViewport: "reflow" },
	},
	play: async () => {
		await expectNoPageOverflow();
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	parameters: { chromatic: { viewports: [1440] } },
};

export const Empty: Story = {
	args: { state: { status: "empty", filtered: false } },
	parameters: { chromatic: { viewports: [1440] } },
};

export const FilteredToNothing: Story = {
	args: { state: { status: "empty", filtered: true, onClearFilters: clearFilters } },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Clear all filters" }));
		await expect(clearFilters).toHaveBeenCalled();
	},
};
