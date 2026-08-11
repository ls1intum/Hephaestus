import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, screen, userEvent, within } from "storybook/test";
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
	play: async ({ canvas }) => {
		// The five kinds the fixture carries, counted here rather than read back off it: a fixture
		// that loses a row should fail this story instead of quietly agreeing with it.
		await expect(await canvas.findByText("5 pieces of work.")).toBeVisible();
		await expect(canvas.getByRole("link", { name: /Member-facing review activity/ })).toBeVisible();
		await expect(canvas.getByText("6 moments recorded · 2 started a review")).toBeVisible();
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
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("1 piece of work.")).toBeVisible();
		await expect(canvas.getByText("1 moment recorded · 0 started a review")).toBeVisible();
	},
};

/**
 * Every kind this build knows carries a name a person can read. A raw `docs.document` in the picker
 * is the failure this list already fixed once for pull requests, and documents ship reviewable now.
 */
export const EveryKindIsNamed: Story = {
	play: async ({ canvas, userEvent }) => {
		await expect(await canvas.findByText("Onboarding: your first week")).toBeVisible();
		await expect(canvas.queryByText("docs.document")).not.toBeInTheDocument();

		await userEvent.click(canvas.getByRole("combobox", { name: /Show/ }));
		// Base UI portals the listbox outside the story's subtree.
		await expect(await screen.findByRole("option", { name: "Documents" })).toBeVisible();
		await expect(screen.getByRole("option", { name: "Conversations" })).toBeVisible();
	},
};

export const NothingRecorded: Story = {
	parameters: {
		msw: { handlers: [http.get(TRACE_LIST_URL, () => HttpResponse.json(tracedArtifactPage([])))] },
	},
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("Nothing has been recorded here yet")).toBeVisible();
	},
};

export const FilteredToOneKind: Story = {
	args: { search: { kind: "scm.issue" }, onSearchChange: fn() },
	parameters: {
		msw: {
			handlers: [
				http.get(TRACE_LIST_URL, () => HttpResponse.json(tracedArtifactPage([tracedArtifacts[2]]))),
			],
		},
	},
	play: async ({ args, canvas }) => {
		await expect(await canvas.findByRole("combobox", { name: "Show" })).toHaveTextContent("Issues");

		await userEvent.click(canvas.getByRole("button", { name: /Reset/ }));
		await expect(args.onSearchChange).toHaveBeenCalledWith({ kind: undefined, page: undefined });
	},
};

/**
 * "Everything" needs a value of its own (Base UI reads "" as no selection), and that value must not
 * escape into the URL — a `kind=__all` link filters for a kind nothing ever has.
 */
export const ClearingTheFilterFromThePicker: Story = {
	args: { search: { kind: "scm.issue", page: 3 }, onSearchChange: fn() },
	play: async ({ args, canvas }) => {
		await userEvent.click(await canvas.findByRole("combobox", { name: "Show" }));
		await userEvent.click(await within(document.body).findByRole("option", { name: "All work" }));

		// The page number goes with it: a page of the filtered list is not a page of the unfiltered one.
		await expect(args.onSearchChange).toHaveBeenCalledWith({ kind: undefined, page: undefined });
	},
};

export const NoWorkOfThatKind: Story = {
	args: { search: { kind: "chat.conversation_thread" }, onSearchChange: fn() },
	parameters: {
		msw: { handlers: [http.get(TRACE_LIST_URL, () => HttpResponse.json(tracedArtifactPage([])))] },
	},
	play: async ({ canvas }) => {
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
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("Couldn't load review activity")).toBeVisible();
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("5 pieces of work.")).toBeVisible();
		await expectNoPageOverflow();
	},
};
