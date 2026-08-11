import type { Meta, StoryObj } from "@storybook/react-vite";
import { http, HttpResponse } from "msw";
import { expect, fn, within } from "storybook/test";
import { buildAutonomyFixture } from "@/components/admin/practices/review-autonomy/story-mock-data";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewPage } from "./ReviewPage";

const fixture = buildAutonomyFixture({
	workspaceDefault: "PROPOSE",
	areas: [
		{
			slug: "hygiene",
			name: "Hygiene",
			practices: [{ name: "States the motivation" }, { name: "Links the issue", override: "OFF" }],
		},
	],
});

const workspace = {
	practicesEnabled: true,
	practiceReviewAutoTriggerEnabled: true,
	practiceReviewManualTriggerEnabled: true,
};

const readyBinding = { purpose: "PRACTICE_REVIEW", enabled: true, ready: true };

const WORKSPACE_URL = "*/workspaces/:workspaceSlug";
const AGENTS_URL = "*/workspaces/:workspaceSlug/agents";

/**
 * Every request all three sections can make. Listed once, because the tabs are the point: a story
 * that only stubbed the open section would pass for the wrong reason the moment a panel stopped
 * unmounting.
 */
const handlers = (overrides: { workspace?: object; agents?: unknown[] } = {}) => [
	http.get(WORKSPACE_URL, () => HttpResponse.json({ ...workspace, ...overrides.workspace })),
	http.get(AGENTS_URL, () => HttpResponse.json(overrides.agents ?? [readyBinding])),
	http.get("*/workspaces/:workspaceSlug/practices/review-settings", () =>
		HttpResponse.json(fixture.settings),
	),
	http.get("*/workspaces/:workspaceSlug/practices/review-tiers", () =>
		HttpResponse.json(fixture.rollup),
	),
	http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json(fixture.practices)),
	http.get("*/workspaces/:workspaceSlug/practices/sweep-schedules", () => HttpResponse.json([])),
	http.get("*/workspaces/:workspaceSlug/practices/backfill-runs", () => HttpResponse.json([])),
];

const meta = {
	title: "Workspace admin/Practices/Review",
	component: ReviewPage,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 1440] },
		viewport: { defaultViewport: "reflow" },
		msw: { handlers: handlers() },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		section: "how-much",
		onSectionChange: fn(),
		overridesOnly: false,
		onOverridesOnlyChange: fn(),
	},
} satisfies Meta<typeof ReviewPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Three sections that used to be three sidebar entries, and the one fact all of them rest on.
 *
 * <p>The model binding is read out above the ladder because it is the precondition for every tier
 * below it — and read-only, because AI models owns it.
 */
export const HowMuch: Story = {
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("tab", { name: "How much" }, { timeout: 5000 }),
		).toHaveAttribute("aria-selected", "true");
		await expect(
			await canvas.findByText(
				"Practice reviews are running in this workspace.",
				{},
				{ timeout: 5000 },
			),
		).toBeVisible();
		await expect(await canvas.findByText("Ready to run reviews.")).toBeVisible();
		await expect(canvas.getByRole("link", { name: "Change on AI models" })).toHaveAttribute(
			"href",
			"/w/demo/admin/models",
		);
		await expectNoPageOverflow();
	},
};

/** What starts a review, and the recurring check that now sits beside the triggers it belongs with. */
export const WhenAndWhere: Story = {
	args: { section: "when-and-where" },
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("heading", { name: "Practice review status" }, { timeout: 5000 }),
		).toBeVisible();
		// Moved out of "past work": a standing check over recent work is a trigger, not a campaign
		// over history, and filing it under past work put a permanent setting behind a one-off heading.
		await expect(canvas.getByRole("heading", { name: "Keep checking new work" })).toBeVisible();
		await expectNoPageOverflow();
	},
};

/** The one-off campaign, alone — the recurring check that used to share this page has moved. */
export const PastWork: Story = {
	args: { section: "past-work" },
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByRole("tab", { name: "Past work" }, { timeout: 5000 }),
		).toHaveAttribute("aria-selected", "true");
		await expect(
			canvas.queryByRole("heading", { name: "Keep checking new work" }),
		).not.toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

/**
 * Every section below is a plausible-looking set of controls that does nothing while the workspace
 * switch is off, and each of them looks like it is working. The header is where that is said once.
 */
export const ReviewsAreOff: Story = {
	parameters: { msw: { handlers: handlers({ workspace: { practicesEnabled: false } }) } },
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByText(
				"Practice reviews are off in this workspace, so nothing below takes effect yet.",
				{},
				{ timeout: 5000 },
			),
		).toBeVisible();
	},
};

/** On, but with nothing to run on — the other half of "is this workspace reviewing anything". */
export const NoModelIsReady: Story = {
	parameters: { msw: { handlers: handlers({ agents: [] }) } },
	play: async ({ canvas }) => {
		await expect(
			await canvas.findByText(
				"Practice reviews are on, but no review model is ready, so none can start.",
				{},
				{ timeout: 5000 },
			),
		).toBeVisible();
		await expect(
			canvas.getByText("No model is bound, so no review can run at any tier below."),
		).toBeVisible();
		await expect(canvas.getByRole("link", { name: "Set up on AI models" })).toBeVisible();
	},
};

/** Three tab labels on a 320px screen wrap rather than dragging the page sideways. */
export const Mobile: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas }) => {
		await canvas.findByRole("tab", { name: "How much" }, { timeout: 5000 });
		await expectNoPageOverflow();
	},
};

/**
 * Real tab semantics, not three links dressed as tabs.
 *
 * <p>`role="tablist"` is what buys a screen reader "tab 1 of 3" and the whole list as one arrow-key
 * stop instead of three tab stops — and each panel is named by the tab that opens it, so the reader
 * who follows one knows where they landed. Every accessible name is the visible label exactly, which
 * is what lets a voice-control user say "When and where" and mean this control (WCAG 2.5.3).
 */
export const SectionsAreRealTabs: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ args, canvas, userEvent }) => {
		await canvas.findByRole("tab", { name: "How much" }, { timeout: 5000 });
		const list = canvas.getByRole("tablist");
		await expect(
			within(list)
				.getAllByRole("tab")
				.map((tab) => tab.textContent),
		).toEqual(["How much", "When and where", "Past work"]);
		// Only the open section is in the document, which is what keeps the other two sections' queries
		// from firing and the autonomy strip from sticking over a panel nobody opened.
		await expect(canvas.getAllByRole("tabpanel")).toHaveLength(1);

		await userEvent.click(canvas.getByRole("tab", { name: "When and where" }));
		await expect(args.onSectionChange).toHaveBeenCalledWith("when-and-where");
	},
};
