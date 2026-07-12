import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, userEvent, within } from "storybook/test";
import { PracticeOverviewPage } from "@/components/practices/PracticeOverviewPage";
import {
	drillDownHandler,
	fullFirstPageRosterHandler,
	healthHandler,
	rosterErrorOnceHandler,
	rosterHandler,
} from "@/components/practices/story-data";

/**
 * The mentor view container: workspace health cards on top, the developers-by-areas matrix
 * below, and the drill-down sheet per developer. All data flows through the generated query
 * options, mocked here with MSW.
 */
const meta = {
	component: PracticeOverviewPage,
	parameters: {
		layout: "fullscreen",
		msw: { handlers: [drillDownHandler, rosterHandler, healthHandler] },
	},
	tags: ["autodocs"],
	args: { workspaceSlug: "nimbus" },
} satisfies Meta<typeof PracticeOverviewPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Health cards, the roster matrix, and the drill-down sheet opening from a row click. */
export const Filled: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// The sheet renders in a portal outside the canvas, so query the canvas's document body.
		const body = within(canvasElement.ownerDocument.body);
		await expect(await canvas.findByText("Priya Raghavan")).toBeInTheDocument();
		// Opening a row loads that developer's cards into the drill-down sheet.
		await userEvent.click(canvas.getByRole("button", { name: /Priya Raghavan/ }));
		await expect(
			await body.findByText("Include tests with the change", undefined, { timeout: 5000 }),
		).toBeInTheDocument();
		await expect(
			body.getByText("Testing your changes: gaps to work on this cycle"),
		).toBeInTheDocument();
	},
};

/** A full first roster page shows the "showing the first 100 developers" note. */
export const FullFirstPage: Story = {
	parameters: {
		msw: { handlers: [drillDownHandler, fullFirstPageRosterHandler, healthHandler] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Showing the first 100 developers.")).toBeInTheDocument();
	},
};

/** The roster request fails once; retrying loads the page. */
export const ErrorWithRetry: Story = {
	parameters: {
		msw: { handlers: [rosterErrorOnceHandler, drillDownHandler, rosterHandler, healthHandler] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			await canvas.findByText("The practice overview could not be loaded right now."),
		).toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Try again" }));
		await expect(
			await canvas.findByText("Priya Raghavan", undefined, { timeout: 5000 }),
		).toBeInTheDocument();
	},
};
