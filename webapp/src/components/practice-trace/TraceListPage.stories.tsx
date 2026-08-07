import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, userEvent, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { tracedArtifactPage, tracedArtifacts } from "./story-mock-data";
import { TraceListPage } from "./TraceListPage";

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

/**
 * A filter that arrives by link has to be visible and clearable even for a kind this build has never
 * heard of, so the picker's choices are its own three plus whatever the page and the URL name.
 */
export const FilteredToOneKind: Story = {
	args: { search: { kind: "scm.issue" }, onSearchChange: fn() },
	parameters: {
		msw: {
			handlers: [
				http.get(TRACE_LIST_URL, () => HttpResponse.json(tracedArtifactPage([tracedArtifacts[2]]))),
			],
		},
	},
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByRole("combobox", { name: "Show" })).toHaveTextContent("Issues");

		await userEvent.click(canvas.getByRole("button", { name: /Reset/ }));
		await expect(args.onSearchChange).toHaveBeenCalledWith({ kind: undefined, page: undefined });
	},
};

/**
 * "Everything" needs a value of its own because Base UI reads "" as no selection at all, and that
 * value must not escape into the URL — a `kind=__all` link filters for a kind nothing ever has.
 */
export const ClearingTheFilterFromThePicker: Story = {
	args: { search: { kind: "scm.issue", page: 3 }, onSearchChange: fn() },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(await canvas.findByRole("combobox", { name: "Show" }));
		await userEvent.click(await within(document.body).findByRole("option", { name: "All work" }));

		// The page number goes with it: page 4 of an unfiltered list is not where the reader was.
		await expect(args.onSearchChange).toHaveBeenCalledWith({ kind: undefined, page: undefined });
	},
};

/** Filtered down to nothing is a fact about the filter, and says how to get back. */
export const NoWorkOfThatKind: Story = {
	args: { search: { kind: "chat.conversation_thread" }, onSearchChange: fn() },
	parameters: {
		msw: { handlers: [http.get(TRACE_LIST_URL, () => HttpResponse.json(tracedArtifactPage([])))] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("No conversations recorded yet")).toBeVisible();
		await expect(canvas.getByText(/Switch back to all work/)).toBeVisible();
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
