import type { Meta, StoryObj } from "@storybook/react-vite";
import { type ComponentProps, useState } from "react";
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
	...overrides,
});

const automatedReview: CuratedPracticeSummary["automatedReview"] = {
	mode: "LANGUAGE_MODEL",
	evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
};

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
		definition: { name: "Team conventions", icon: "Scale", color: "amber" },
		status: status({ state: "YOURS" }),
	},
	{
		slug: "not-offered",
		position: 2,
		definition: { name: "Legacy conventions" },
		status: status({ offered: false }),
	},
];

const practices: CuratedPracticeSummary[] = [
	{
		slug: "small-focused-prs",
		position: 0,
		name: "Keep a change to one concern",
		artifactKind: "scm.pull_request",
		automatedReview,
		areaSlug: "review-ready-work",
		effectivelyOffered: true,
		status: status(),
	},
	{
		slug: "explains-the-change",
		position: 1,
		name: "Say what changed and why",
		artifactKind: "scm.pull_request",
		automatedReview,
		areaSlug: "review-ready-work",
		effectivelyOffered: true,
		status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
	},
	{
		slug: "reworded-upstream",
		position: 2,
		name: "Respond to each review comment",
		artifactKind: "scm.pull_request",
		automatedReview,
		areaSlug: "review-ready-work",
		effectivelyOffered: true,
		status: status({ state: "UPDATE_WAITING", changeKind: "WORDING" }),
	},
	{
		slug: "our-release-notes",
		position: 0,
		name: "Write the release note with the change",
		artifactKind: "scm.pull_request",
		automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" },
		areaSlug: "house-rules",
		effectivelyOffered: true,
		status: status({ state: "YOURS" }),
	},
	{
		slug: "link-the-issue",
		position: 0,
		name: "Link the issue the change closes",
		artifactKind: "scm.issue",
		automatedReview: {
			mode: "LANGUAGE_MODEL",
			evidenceSufficiency: "DECLARED_EVIDENCE_INSUFFICIENT",
		},
		effectivelyOffered: true,
		status: status({ state: "NO_LONGER_SHIPPED" }),
	},
	{
		slug: "orphaned-practice",
		position: 0,
		name: "Outlived the area it was filed under",
		artifactKind: "scm.pull_request",
		automatedReview,
		areaSlug: "an-area-hephaestus-stopped-shipping",
		effectivelyOffered: false,
		status: status({ state: "NO_LONGER_SHIPPED", offered: false }),
	},
];

const meta = {
	title: "Instance admin/Practice catalog/Overview",
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
		customOrder: false,
		onSearchChange: fn(),
		onPracticeStatusChange: fn(),
		onAreaStatusChange: fn(),
		onReorderAreas: fn(),
		onPlacePractice: fn(),
		onResetOrder: fn(),
	},
	tags: ["autodocs"],
} satisfies Meta<typeof CuratedCatalog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Everything: Story = {};

export const MentoringLimitsAreVisible: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Guidance only")).toBeVisible();
		await expect(canvas.getByText("Human review needed")).toBeVisible();
	},
};

export const CustomOrder: Story = {
	args: { customOrder: true },
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Use Hephaestus order" }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(dialog).toHaveAccessibleDescription(/Definitions and inclusion will not change/);
		await userEvent.click(within(dialog).getByRole("button", { name: "Use Hephaestus order" }));
		await expect(args.onResetOrder).toHaveBeenCalledOnce();
	},
};

export const OnlyIncluded: Story = { args: { search: { status: "OFFERED" } } };

export const APracticeWhoseAreaIsGone: Story = {
	play: async ({ args, canvas }) => {
		await canvas.findByText("Outlived the area it was filed under");
		await canvas.findByText("Area no longer exists");
		await expect(
			canvas.getByRole("switch", {
				name: "Outlived the area it was filed under cannot be included until it is moved out of the missing area",
			}),
		).toHaveAttribute("aria-disabled", "true");
		await userEvent.click(
			canvas.getByRole("button", {
				name: "More actions for Outlived the area it was filed under",
			}),
		);
		await expect(
			await screen.findByRole("menuitem", {
				name: "Move to Unassigned or an included area first",
			}),
		).toHaveAttribute("aria-disabled", "true");
		await userEvent.keyboard("{Escape}");
		await userEvent.click(
			canvas.getByRole("button", {
				name: "More actions for Outlived the area it was filed under",
			}),
		);
		await userEvent.click(await screen.findByRole("menuitemradio", { name: "Unassigned" }));
		expect(args.onPlacePractice).toHaveBeenCalledWith("orphaned-practice", null, 1);
	},
};

export const UnavailableMoveDestinationIsNamed: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: "More actions for Link the issue the change closes" }),
		);
		await screen.findByRole("menuitemradio", {
			name: "Legacy conventions (excluded)",
		});
	},
};

/** Owns the search term, so filtering is a state transition rather than two separate renders. */
function FilterTransition(props: ComponentProps<typeof CuratedCatalog>) {
	const [search, setSearch] = useState<CuratedCatalogSearch>({});
	return <CuratedCatalog {...props} search={search} onSearchChange={setSearch} />;
}

export const FilteringOpensMatchingAreas: Story = {
	render: (args) => <FilterTransition {...args} />,
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas }) => {
		const area = canvas.getByRole("button", { name: /^Packaging work for review 3$/ });
		await userEvent.click(area);
		await userEvent.click(canvas.getByRole("combobox", { name: "Filter by work type" }));
		await userEvent.click(await screen.findByRole("option", { name: "Pull or merge requests" }));
		await canvas.findByText("Keep a change to one concern");
		await expect(area).toHaveAttribute("aria-expanded", "true");
		await expect(area).toHaveAttribute("aria-disabled", "true");
	},
};

export const SearchOpensTheAreaHoldingTheMatch: Story = {
	args: { search: { q: "release note" } },
	play: async ({ canvas }) => {
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

export const ExcludingAnAreaListsItsPractices: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas }) => {
		await userEvent.click(
			await canvas.findByRole("switch", {
				name: "Include Packaging work for review in new workspaces",
			}),
		);
		const dialog = await screen.findByRole("alertdialog");
		await within(dialog).findByText(/also excludes 3 currently included practices/);
		await within(dialog).findByText("Say what changed and why");
	},
};

export const PracticeInsideExcludedArea: Story = {
	args: {
		areas: [areas[2]],
		practices: [
			{
				...practices[0],
				areaSlug: areas[2].slug,
				effectivelyOffered: false,
			},
			{
				...practices[1],
				areaSlug: areas[2].slug,
				effectivelyOffered: false,
				status: status({ offered: false }),
			},
		],
	},
	play: async ({ canvas }) => {
		const inheritedSwitch = await canvas.findByRole("switch", {
			name: "Keep a change to one concern is excluded because its area is excluded",
		});
		await expect(inheritedSwitch).not.toBeChecked();
		await expect(inheritedSwitch).toHaveAttribute("aria-disabled", "true");
		const directlyExcludedSwitch = await canvas.findByRole("switch", {
			name: "Say what changed and why is excluded from new workspaces",
		});
		await expect(directlyExcludedSwitch).not.toBeChecked();
		await expect(directlyExcludedSwitch).toHaveAttribute("aria-disabled", "true");
	},
};

export const ExcludingAnAreaCountsOnlyIncludedPractices: Story = {
	args: {
		areas: [areas[0]],
		practices: [
			practices[0],
			{
				...practices[1],
				effectivelyOffered: false,
				status: status({ offered: false }),
			},
		],
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas }) => {
		await userEvent.click(
			await canvas.findByRole("switch", {
				name: "Include Packaging work for review in new workspaces",
			}),
		);
		const dialog = await screen.findByRole("alertdialog");
		await within(dialog).findByText(/also excludes 1 currently included practice/);
		await within(dialog).findByText("Keep a change to one concern");
		await expect(within(dialog).queryByText("Say what changed and why")).not.toBeInTheDocument();
	},
};

export const ExcludingAnAreaDoesNotRecountExcludedPractices: Story = {
	args: {
		areas: [areas[0]],
		practices: [
			{
				...practices[0],
				areaSlug: areas[0].slug,
				effectivelyOffered: false,
				status: status({ offered: false }),
			},
		],
		summary: {
			...meta.args.summary,
			total: 2,
			updatesChangingDetection: 0,
			updatesChangingWordingOnly: 0,
			noLongerShipped: 0,
		},
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas }) => {
		await userEvent.click(
			await canvas.findByRole("switch", {
				name: "Include Packaging work for review in new workspaces",
			}),
		);
		await screen.findByText(/No additional practices will be excluded/);
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
