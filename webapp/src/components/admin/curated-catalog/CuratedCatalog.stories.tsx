import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { CatalogEntryStatus, CuratedArea, CuratedPracticeSummary } from "@/api/types.gen";
import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { CuratedCatalog } from "./CuratedCatalog";
import type { CuratedCatalogSearch } from "./curated-catalog-search";

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
		position: 0,
		definition: {
			name: "Packaging work for review",
			description: "Make a change cheap to review before you ask for one.",
			icon: "Package",
			color: "sky",
		},
		status: status(),
	},
	{
		slug: "house-rules",
		position: 1,
		definition: { name: "Our own conventions", icon: "Scale", color: "amber" },
		status: status({ state: "YOURS" }),
	},
	{
		slug: "not-offered",
		position: 2,
		definition: { name: "Something we stopped using" },
		status: status({ offered: false, retired: true }),
	},
];

const practices: CuratedPracticeSummary[] = [
	{
		slug: "small-focused-prs",
		position: 0,
		name: "Keep a change to one concern",
		artifactType: "PULL_REQUEST",
		areaSlug: "review-ready-work",
		effectivelyOffered: true,
		status: status(),
	},
	{
		slug: "explains-the-change",
		position: 1,
		name: "Say what changed and why",
		artifactType: "PULL_REQUEST",
		areaSlug: "review-ready-work",
		effectivelyOffered: true,
		status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
	},
	{
		slug: "reworded-upstream",
		position: 2,
		name: "Respond to each review comment",
		artifactType: "PULL_REQUEST",
		areaSlug: "review-ready-work",
		effectivelyOffered: true,
		status: status({ state: "UPDATE_WAITING", changeKind: "WORDING" }),
	},
	{
		slug: "our-release-notes",
		position: 0,
		name: "Write the release note with the change",
		artifactType: "PULL_REQUEST",
		areaSlug: "house-rules",
		effectivelyOffered: true,
		status: status({ state: "YOURS" }),
	},
	{
		slug: "link-the-issue",
		position: 0,
		name: "Link the issue the change closes",
		artifactType: "ISSUE",
		effectivelyOffered: true,
		status: status({ state: "NO_LONGER_SHIPPED" }),
	},
	{
		slug: "orphaned-practice",
		position: 0,
		name: "Outlived the area it was filed under",
		artifactType: "PULL_REQUEST",
		areaSlug: "an-area-hephaestus-stopped-shipping",
		effectivelyOffered: false,
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
			updatesChangingPresentation: 0,
			editedHere: 0,
			yours: 2,
			notOffered: 1,
			noLongerShipped: 1,
		},
		search: {},
		onSearchChange: fn(),
		onPracticeStatusChange: fn(),
		onAreaStatusChange: fn(),
		onReorderAreas: fn(),
		onPlacePractice: fn(),
	},
	tags: ["autodocs"],
} satisfies Meta<typeof CuratedCatalog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Everything: Story = {};

export const OnlyWhatIsOffered: Story = { args: { search: { status: "OFFERED" } } };

export const APracticeWhoseAreaIsGone: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await canvas.findByText("Outlived the area it was filed under");
		await canvas.findByText("Area no longer available");
		await canvas.findByRole("switch", {
			name: "Offer Outlived the area it was filed under after moving it to an available area",
		});
		await userEvent.click(
			canvas.getByRole("button", {
				name: "More actions for Outlived the area it was filed under",
			}),
		);
		await userEvent.click(await screen.findByRole("menuitemradio", { name: "Unassigned" }));
		expect(args.onPlacePractice).toHaveBeenCalledWith("orphaned-practice", null, 1);
		await userEvent.click(
			canvas.getByRole("button", {
				name: "More actions for Outlived the area it was filed under",
			}),
		);
		await userEvent.click(await screen.findByRole("menuitem", { name: "Retire practice" }));
		await screen.findByText(
			"It is already unavailable because its area is not offered. Retiring it keeps it unavailable if the area becomes available again. Workspaces that already have it keep it unchanged.",
		);
	},
};

export const UnavailableMoveDestinationIsNamed: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(
			canvas.getByRole("button", { name: "More actions for Link the issue the change closes" }),
		);
		await screen.findByRole("menuitemradio", {
			name: "Something we stopped using (not offered)",
		});
	},
};

function FilterTransition() {
	const [search, setSearch] = useState<CuratedCatalogSearch>({});
	return <CuratedCatalog {...meta.args} search={search} onSearchChange={setSearch} />;
}

export const FilteringOpensMatchingAreas: Story = {
	render: () => <FilterTransition />,
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const area = canvas.getByRole("button", { name: /^Packaging work for review 3$/ });
		await userEvent.click(area);
		await userEvent.click(canvas.getByRole("combobox", { name: "Filter by work type" }));
		await userEvent.click(await screen.findByRole("option", { name: "Pull or merge request" }));
		await canvas.findByText("Keep a change to one concern");
	},
};

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
			updatesChangingPresentation: 0,
			editedHere: 0,
			yours: 0,
			notOffered: 0,
			noLongerShipped: 0,
		},
	},
};

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
		await within(dialog).findByText("Say what changed and why");
	},
};

export const MobileReflow: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: expectNoPageOverflow,
};

export const Empty: Story = {
	args: {
		areas: [],
		practices: [],
		summary: {
			total: 0,
			updatesChangingDetection: 0,
			updatesChangingWordingOnly: 0,
			updatesChangingPresentation: 0,
			editedHere: 0,
			yours: 0,
			notOffered: 0,
			noLongerShipped: 0,
		},
	},
};
