import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
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

/**
 * One row per observation, at every conclusion the model can reach.
 *
 * <p>This used to be two components: a four-column table above `xl` and a card list below it, with
 * different fields in each. A 25-row list whose dominant cell is a sentence gains nothing from a
 * table, and keeping two of them in step is what let the card version drop the severity.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		// The leading icon and the badge both come from `observationResult`, so a shortfall shows its
		// assessment and its severity, and a presence that ends the question shows only itself.
		// Six shortfalls in the fixture, so this is a count and not a lookup.
		expect(canvas.getAllByText("Needs improvement")).toHaveLength(6);
		canvas.getByText("Critical");
		canvas.getByText("Not applicable");
		canvas.getByText("Could not be determined");
		// The origin badge is silent for the ordinary case and speaks for the two that are not.
		expect(canvas.getAllByText("From a review of past work")).toHaveLength(2);
		expect(canvas.getAllByText("Requested by hand")).toHaveLength(2);
		await expect(canvas.queryAllByText("No result")).toHaveLength(0);
	},
};

/** The practice name reaches its definition, and its prose is one hover away. */
export const PracticeOpensItsDefinition: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas }) => {
		// Two observations name this practice; both of them reach the same definition.
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

/** The skeleton draws the row it is standing in for: icon tile, title, meta line, chips. */
export const Loading: Story = {
	args: { state: { status: "loading" } },
	parameters: { chromatic: { viewports: [1440] } },
};

export const Empty: Story = {
	args: { state: { status: "empty", filtered: false } },
	parameters: { chromatic: { viewports: [1440] } },
};

export const FilteredToNothing: Story = {
	args: { state: { status: "empty", filtered: true } },
	parameters: { chromatic: { viewports: [1440] } },
};
