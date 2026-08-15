import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, within } from "storybook/test";
import type { ReviewObservation } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { ObservationResults } from "./ObservationResults";
import { reviewObservations, workspacePractices } from "./story-mock-data";

const longContent = {
	...reviewObservations[0],
	id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
	title:
		"The review keeps every boundary visible even when the observation needs enough words to wrap across a narrow screen twice over",
	subject: {
		id: 10,
		login: "alexandria-occasional-contributor",
		name: "Alexandria Catherine Montgomery-Worthington",
	},
} satisfies ReviewObservation;

/** Storybook resets a spy that appears in `args` between runs, so one instance is enough. */
const clearFilters = fn();

const meta = {
	title: "Workspace admin/Practice reviews/Building blocks/Observation results",
	component: ObservationResults,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 768, 1440] },
		// The practice name is a link with the practice's own prose behind it, so the rows own one
		// query between them; every story that renders a row has to answer it.
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices", () =>
					HttpResponse.json(workspacePractices),
				),
			],
		},
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		state: { status: "ready", observations: reviewObservations },
	},
} satisfies Meta<typeof ObservationResults>;

export default meta;
type Story = StoryObj<typeof meta>;

/** One row per observation, at every conclusion the model can reach. */
export const Default: Story = {
	play: async ({ canvas }) => {
		// A shortfall shows its assessment and its severity; a presence that ends the question shows
		// only itself.
		expect(canvas.getAllByText("Needs improvement")).toHaveLength(6);
		canvas.getByText("Critical");
		canvas.getByText("Not applicable");
		canvas.getByText("Could not be determined");
		expect(canvas.getAllByText("From a review of past work")).toHaveLength(2);
		expect(canvas.getAllByText("Requested by hand")).toHaveLength(2);
		await expect(canvas.queryAllByText("No result")).toHaveLength(0);
	},
};

export const PracticeOpensItsDefinition: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas }) => {
		// Several observations name this practice, and every one of them reaches the same definition.
		const links = await canvas.findAllByRole("link", { name: /Thin controllers/ });
		for (const link of links) {
			await expect(link).toHaveAttribute("href", "/w/demo/admin/practices/thin-controllers");
		}
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		within(canvasElement).getByRole("link", {
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
