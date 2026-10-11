import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";

import { OVERVIEW } from "@/stories/activity-story-data";
import { withProvider, withStandardPage } from "@/stories/decorators";
import { settledPopup } from "@/stories/overlay";
import { expectNoPageOverflow } from "@/stories/reflow";

import { ActivityRepositoryTable } from "./ActivityRepositoryTable";

const meta = {
	component: ActivityRepositoryTable,
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: { repositories: OVERVIEW.repositories, providerType: "GITHUB" },
} satisfies Meta<typeof ActivityRepositoryTable>;

export default meta;
type Story = StoryObj<typeof meta>;

const busyRepositories = OVERVIEW.repositories.map((entry, index) => ({
	...entry,
	counts:
		index === 0
			? {
					...entry.counts,
					contributions: 1234 + 2048 + entry.counts.issuesOpened,
					pullRequestsOpened: 1234,
					pullRequestsMerged: 1187,
					pullRequestsReviewed: 2048,
					peopleHelped: 1024,
				}
			: { ...entry.counts, pullRequestsOpened: 2, pullRequestsMerged: 0 },
}));

/** The pull request figures of a row: opened, then merged. */
function pullRequestChips(row: HTMLElement | undefined): HTMLElement[] {
	const cell = row && within(row).getAllByRole("cell")[2];
	return cell ? within(cell).getAllByRole("img") : [];
}

/** Most contributions first, by full path, so two repositories with one name stay apart. */
export const Default: Story = {
	args: { repositories: busyRepositories },
	play: async ({ canvas }) => {
		const [, first] = canvas.getAllByRole("row");
		await expect(
			within(first ?? canvas.getByRole("table")).getAllByRole("cell")[0],
		).toHaveTextContent("hephaestus-build/Hephaestus");
		await expect(canvas.getByRole("columnheader", { name: "Pull requests" })).toBeVisible();
		const [opened, merged] = pullRequestChips(first);
		if (!opened || !merged) {
			throw new Error("The pull request figures are not both shown");
		}
		await expect(merged.getBoundingClientRect().left).toBeGreaterThanOrEqual(
			opened.getBoundingClientRect().right,
		);
	},
};

export const GitLab: Story = {
	decorators: [withProvider("GITLAB")],
	args: { providerType: "GITLAB" },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("columnheader", { name: "Merge requests" })).toBeVisible();
	},
};

export const Empty: Story = {
	args: { repositories: [] },
	play: async ({ canvas }) => {
		await expect(canvas.getAllByRole("row")).toHaveLength(1);
	},
};

/** Four-digit pairs stack whole below `sm`; a repository with no merges shows one figure. */
export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	args: { repositories: busyRepositories },
	play: async ({ canvas, userEvent }) => {
		await expectNoPageOverflow();
		await expect(canvas.getByRole("columnheader", { name: "PRs (Pull requests)" })).toBeVisible();
		const [, busy, quiet] = canvas.getAllByRole("row");
		const [opened, merged] = pullRequestChips(busy);
		if (!opened || !merged) {
			throw new Error("The pull request figures are not both shown");
		}
		await expect(within(opened).getByText("1,234")).toBeVisible();
		await expect(within(merged).getByText("1,187")).toBeVisible();
		await expect(merged.getBoundingClientRect().top).toBeGreaterThanOrEqual(
			opened.getBoundingClientRect().bottom - 1,
		);
		await expect(pullRequestChips(quiet)).toHaveLength(1);
		await userEvent.hover(merged);
		await expect(await settledPopup()).toHaveTextContent(merged.getAttribute("aria-label") ?? "");
	},
};

export const Dark: Story = { globals: { theme: "dark" } };

export const GitLabDark: Story = {
	decorators: [withProvider("GITLAB")],
	args: { providerType: "GITLAB" },
	globals: { theme: "dark" },
};
