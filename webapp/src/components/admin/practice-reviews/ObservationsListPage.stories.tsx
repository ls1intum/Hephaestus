import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { loadedList } from "@/stories/paged-list";
import { StatefulPatch } from "@/stories/stateful";

import {
	practiceGroups,
	reviewObservations,
	workspaceMembers,
	workspacePractices,
} from "./fixtures";
import { ObservationsListPage } from "./ObservationsListPage";
import { groupFacetOptions, practiceFacetOptions } from "./ResultFilters";
import type { ReviewPeople } from "./ReviewPersonFacet";

const PEOPLE: ReviewPeople = {
	options: workspaceMembers.map((member) => ({
		userId: member.userId,
		label: member.userName ?? `#${member.userId}`,
		secondary: member.userLogin,
	})),
	capped: false,
	isLoading: false,
	isError: false,
};
const GROUPS: FacetSource = {
	options: groupFacetOptions(practiceGroups),
	isLoading: false,
	isError: false,
};
const PRACTICES: FacetSource = {
	options: practiceFacetOptions(workspacePractices, practiceGroups),
	isLoading: false,
	isError: false,
};

/**
 * One problem per row under the shared scope, with the tab's own facets: severity and origin. The rows
 * are what the route loaded; which rows a filter selects is the route test's to prove.
 */
const meta = {
	component: ObservationsListPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		search: { severity: undefined, origin: undefined },
		onSearchChange: fn(),
		observations: loadedList(reviewObservations),
		groups: GROUPS,
		practices: PRACTICES,
		people: PEOPLE,
	},
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, onSearchChange) => (
				<ObservationsListPage
					{...args}
					search={search}
					onSearchChange={(patch) => {
						args.onSearchChange(patch);
						onSearchChange(patch);
					}}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ObservationsListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByText(`${reviewObservations.length} problems.`);
		for (const name of ["Group", "Practice", "Severity", "Origin"]) {
			canvas.getByRole("combobox", { name });
		}
		canvas.getByRole("button", { name: "Recorded" });
	},
};

export const FilterToOneSeverity: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ args, canvas, userEvent }) => {
		const trigger = canvas.getByRole("combobox", { name: "Severity" });
		await userEvent.click(trigger);
		const listbox = await screen.findByRole("listbox", { name: "Severity options" });
		await userEvent.click(await within(listbox).findByRole("option", { name: /Major/u }));
		await userEvent.click(trigger);
		await expect(args.onSearchChange).toHaveBeenLastCalledWith(
			expect.objectContaining({ severity: ["MAJOR"] }),
		);
	},
};

export const Loading: Story = {
	args: { observations: { status: "loading" } },
};

export const Empty: Story = {
	args: { observations: loadedList([]) },
	play: async ({ canvas }) => {
		await canvas.findByText("No problems yet");
	},
};

export const LoadFailed: Story = {
	args: {
		observations: {
			status: "error",
			error: { status: 500, detail: "The observation index is unavailable." },
			onRetry: fn(),
		},
	},
	play: async ({ canvas }) => {
		await expect(await canvas.findByText("We could not load problems")).toBeVisible();
	},
};

const retryRefresh = fn();
export const RefreshFailed: Story = {
	args: {
		observations: loadedList(reviewObservations, {
			refreshFailure: { error: { status: 503 }, pending: false, onRetry: retryRefresh },
		}),
	},
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("These problems may be out of date");
		await expect(canvas.getByRole("table").querySelectorAll("tbody tr").length).toBeGreaterThan(0);
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(retryRefresh).toHaveBeenCalledOnce();
	},
};
