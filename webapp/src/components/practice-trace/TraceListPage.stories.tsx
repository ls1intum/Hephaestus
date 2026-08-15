import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { StatefulPatch } from "@/stories/stateful";
import { expectSettledVisible } from "@/test/overlay";
import { expectNoPageOverflow } from "@/test/reflow";
import { tracedArtifactPage, tracedArtifacts } from "./story-mock-data";
import { TraceListPage } from "./TraceListPage";

const TRACE_LIST_URL = "*/workspaces/:workspaceSlug/practices/trace";

const meta = {
	title: "Practice trace/Review activity list",
	component: TraceListPage,
	parameters: {
		// One MSW worker answers a whole Docs page, so each story gets its own frame until MSW goes.
		docs: { story: { inline: false, height: "600px" } },
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
	/**
	 * The picker and the pager are controlled, so a story passing only `fn()` could not show a chosen
	 * kind. The harness holds the answer and the spy still records the patch.
	 */
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, patch) => (
				<TraceListPage
					{...args}
					search={search}
					onSearchChange={(next) => {
						args.onSearchChange(next);
						patch(next);
					}}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof TraceListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		// Written out rather than read back off the fixture, so a fixture that loses a row fails here
		// instead of quietly agreeing with the page.
		await expect(await canvas.findByText("5 pieces of work.")).toBeVisible();
		await expect(canvas.getByRole("link", { name: /Member-facing review activity/ })).toBeVisible();
		await expect(canvas.getByText("6 moments recorded · 2 started a review")).toBeVisible();
	},
};

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

export const EveryKindIsNamed: Story = {
	play: async ({ canvas, userEvent }) => {
		await expect(await canvas.findByText("Onboarding: your first week")).toBeVisible();
		await expect(canvas.queryByText("docs.document")).not.toBeInTheDocument();

		await userEvent.click(canvas.getByRole("combobox", { name: /Show/ }));
		// The listbox is portalled, so it is on `screen` rather than in the canvas.
		await expectSettledVisible(await screen.findByRole("option", { name: "Documents" }));
		screen.getByRole("option", { name: "Conversations" });
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

/** The `ALL_KINDS` sentinel must not escape into the URL: it filters for a kind nothing ever has. */
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
