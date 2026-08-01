import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
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
			noLongerShipped: 0,
		},
		search: {},
		practicesInArea: (areaSlug: string) =>
			practices.filter((practice) => practice.areaSlug === areaSlug).map((p) => p.slug),
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

export const Empty: Story = {
	args: {
		areas: [],
		practices: [],
		practicesInArea: () => [],
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
