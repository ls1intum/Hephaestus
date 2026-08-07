import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { TraceListPage } from "./TraceListPage";
import { tracedArtifactPage, tracedArtifacts } from "./story-mock-data";

const TRACE_LIST_URL = "*/workspaces/:workspaceSlug/practices/trace";

const meta = {
	title: "Practice trace/Review activity list",
	component: TraceListPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
		msw: {
			handlers: [http.get(TRACE_LIST_URL, () => HttpResponse.json(tracedArtifactPage()))],
		},
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		search: {},
		onSearchChange: fn(),
	},
} satisfies Meta<typeof TraceListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			await canvas.findByText(`${tracedArtifacts.length} pieces of work.`),
		).toBeVisible();
		await expect(canvas.getByRole("link", { name: /Member-facing review activity/ })).toBeVisible();
		// Both halves of the signal summary are on screen: what we saw, and how much we reviewed.
		await expect(canvas.getByText("6 signals · 2 reviewed")).toBeVisible();
	},
};

/** An artifact with no number, container or upstream link still lists rather than disappearing. */
export const UnlinkableArtifact: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get(TRACE_LIST_URL, () => HttpResponse.json(tracedArtifactPage([tracedArtifacts[3]]))),
			],
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("1 piece of work.")).toBeVisible();
		await expect(canvas.getByText("1 signal · 0 reviewed")).toBeVisible();
	},
};

/**
 * The empty state has to be true and useful: nothing recorded is a fact about the connection, not a
 * fact about the reader's work.
 */
export const NothingRecorded: Story = {
	parameters: {
		msw: { handlers: [http.get(TRACE_LIST_URL, () => HttpResponse.json(tracedArtifactPage([])))] },
	},
	play: async ({ canvasElement }) => {
		await expect(
			await within(canvasElement).findByText("Nothing has been recorded here yet"),
		).toBeVisible();
	},
};

export const LoadFailed: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get(TRACE_LIST_URL, () =>
					HttpResponse.json(
						{ status: 400, title: "Bad Request", detail: "Unknown artifact kind." },
						{ status: 400, headers: { "Content-Type": "application/problem+json" } },
					),
				),
			],
		},
	},
	play: async ({ canvasElement }) => {
		await expect(
			await within(canvasElement).findByText("Couldn't load review activity"),
		).toBeVisible();
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		await expect(
			await within(canvasElement).findByText(`${tracedArtifacts.length} pieces of work.`),
		).toBeVisible();
		await expectNoPageOverflow();
	},
};
