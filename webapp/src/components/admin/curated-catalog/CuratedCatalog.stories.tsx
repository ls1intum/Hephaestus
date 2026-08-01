import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn, screen, userEvent, within } from "storybook/test";
import type { CatalogEntryStatus, CuratedArea, CuratedPracticeSummary } from "@/api/types.gen";
import { withStandardPage } from "@/stories/decorators";
import { CuratedCatalog } from "./CuratedCatalog";

const status = (overrides: Partial<CatalogEntryStatus> = {}): CatalogEntryStatus => ({
	etag: "tag",
	state: "FROM_HEPHAESTUS",
	changeKind: "NONE",
	offered: true,
	retired: false,
	updatedAt: new Date("2026-07-30T12:00:00Z"),
	...overrides,
});

const areas: CuratedArea[] = [
	{
		slug: "review-ready-work",
		definition: {
			name: "Packaging work for review",
			description: "Make a change cheap to review before you ask for one.",
			displayOrder: 0,
			icon: "Package",
			color: "sky",
		},
		status: status(),
	},
	{
		slug: "house-rules",
		definition: { name: "Our own conventions", displayOrder: 1, icon: "Scale", color: "amber" },
		status: status({ state: "YOURS" }),
	},
	{
		slug: "not-offered",
		definition: { name: "Something we stopped using", displayOrder: 2 },
		status: status({ offered: false, retired: true }),
	},
];

const practices: CuratedPracticeSummary[] = [
	{
		slug: "small-focused-prs",
		name: "Keep a change to one concern",
		artifactType: "PULL_REQUEST",
		areaSlug: "review-ready-work",
		status: status(),
	},
	{
		slug: "explains-the-change",
		name: "Say what changed and why",
		artifactType: "PULL_REQUEST",
		areaSlug: "review-ready-work",
		status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
	},
	{
		slug: "reworded-upstream",
		name: "Respond to each review comment",
		artifactType: "PULL_REQUEST",
		areaSlug: "review-ready-work",
		status: status({ state: "UPDATE_WAITING", changeKind: "WORDING" }),
	},
	{
		slug: "our-release-notes",
		name: "Write the release note with the change",
		artifactType: "PULL_REQUEST",
		areaSlug: "house-rules",
		status: status({ state: "YOURS" }),
	},
	{
		slug: "link-the-issue",
		name: "Link the issue the change closes",
		artifactType: "ISSUE",
		status: status({ state: "NO_LONGER_SHIPPED" }),
	},
	{
		slug: "orphaned-practice",
		name: "Outlived the area it was filed under",
		artifactType: "PULL_REQUEST",
		areaSlug: "an-area-hephaestus-stopped-shipping",
		status: status({ state: "NO_LONGER_SHIPPED" }),
	},
];

const meta = {
	title: "Instance admin/Practice catalog",
	component: CuratedCatalog,
	parameters: { layout: "fullscreen", chromatic: { viewports: [1440] } },
	decorators: [withStandardPage],
	args: {
		areas,
		practices,
		summary: {
			total: areas.length + practices.length,
			updatesChangingDetection: 1,
			updatesChangingWordingOnly: 1,
			editedHere: 0,
			yours: 2,
			retired: 1,
			noLongerShipped: 1,
		},
		search: {},
		onSearchChange: fn(),
		onPracticeStatusChange: fn(),
		onAreaStatusChange: fn(),
	},
	tags: ["autodocs"],
} satisfies Meta<typeof CuratedCatalog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const OnlyWhatIsOffered: Story = {};

export const Everything: Story = { args: { search: { status: "ALL" } } };

/**
 * A practice can outlive its area — the admin edited the practice, a later build dropped both. It
 * must still be reachable, or it cannot be retired.
 */
export const APracticeWhoseAreaIsGone: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await canvas.findByText("Outlived the area it was filed under");
	},
};

/** Searching reveals the area holding the match, already open. */
export const SearchOpensTheAreaHoldingTheMatch: Story = {
	args: { search: { q: "release note" } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await canvas.findByText("Write the release note with the change");
	},
};

export const NothingHasBeenChanged: Story = {
	args: {
		practices: practices.map((practice) => ({ ...practice, status: status() })),
		areas: areas.map((area) => ({ ...area, status: status() })),
		summary: {
			total: areas.length + practices.length,
			updatesChangingDetection: 0,
			updatesChangingWordingOnly: 0,
			editedHere: 0,
			yours: 0,
			retired: 0,
			noLongerShipped: 0,
		},
	},
};

/** The confirmation names every practice the area would take with it. */
export const RetiringAnAreaWithholdsItsPractices: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(
			await canvas.findByRole("switch", {
				name: "Offer Packaging work for review to new workspaces",
			}),
		);
		const dialog = await screen.findByRole("alertdialog");
		await within(dialog).findByText(/3 practices filed under it/);
		// Named, not slugged: the administrator picked them by name everywhere else.
		await within(dialog).findByText("Say what changed and why");
	},
};

export const Empty: Story = {
	args: {
		areas: [],
		practices: [],
		summary: {
			total: 0,
			updatesChangingDetection: 0,
			updatesChangingWordingOnly: 0,
			editedHere: 0,
			yours: 0,
			retired: 0,
			noLongerShipped: 0,
		},
	},
};
