import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, screen, waitFor, within } from "storybook/test";

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

const NESTED_PATH = "engineering/platform/data-integrations/mobile-review-tooling/registry-alpha";
const OTHER_NESTED_PATH =
	"engineering/platform/data-integrations/mobile-review-tooling/registry-beta";
const nestedRepositories = busyRepositories.map((entry, index) => {
	const issuesOpened = index === 0 ? 9876 : entry.counts.issuesOpened;
	return {
		...entry,
		repository: {
			...entry.repository,
			key: index === 0 ? NESTED_PATH : OTHER_NESTED_PATH,
			name: index === 0 ? "registry-alpha" : "registry-beta",
		},
		counts: {
			...entry.counts,
			issuesOpened,
			comments: index === 0 ? 0 : entry.counts.comments,
			contributions:
				entry.counts.pullRequestsOpened + entry.counts.pullRequestsReviewed + issuesOpened,
		},
		breakdown:
			index === 0
				? {
						...entry.breakdown,
						approvals: entry.counts.pullRequestsReviewed,
						changeRequests: 0,
						commentReviews: 0,
						discussionComments: 0,
						codeComments: 0,
					}
				: entry.breakdown,
	};
});

export const LongRepositoryPath: Story = {
	decorators: [
		withProvider("GITLAB"),
		(Story) => (
			<div className="max-w-sm">
				<Story />
			</div>
		),
	],
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	args: { repositories: nestedRepositories, providerType: "GITLAB" },
	play: async ({ canvas, userEvent }) => {
		await expectNoPageOverflow();
		const table = canvas.getByRole("table", { name: "Repositories" });
		const scroller = table.closest<HTMLElement>("[data-slot='table-container']");
		if (!scroller) {
			throw new Error("The repository table has no scroll region");
		}
		const name = within(table).getByRole("cell", { name: NESTED_PATH });
		const otherName = within(table).getByRole("cell", { name: OTHER_NESTED_PATH });
		const row = name.closest("tr");
		if (!row) {
			throw new Error("The repository identity has no row");
		}
		const issue = within(row).getByRole("img", { name: "9876 issues opened" });
		await expect(scroller.scrollWidth).toBeGreaterThan(scroller.clientWidth);
		await userEvent.tab();
		await expect(scroller).toHaveFocus();
		scroller.scrollLeft = scroller.scrollWidth;
		await waitFor(() => {
			void expect(scroller.scrollLeft).toBeGreaterThan(0);
		});
		await waitFor(() => {
			const left = scroller.getBoundingClientRect().left + scroller.clientLeft;
			void expect(Math.abs(name.getBoundingClientRect().left - left)).toBeLessThanOrEqual(1);
			void expect(Math.abs(otherName.getBoundingClientRect().left - left)).toBeLessThanOrEqual(1);
			void expect(issue.getBoundingClientRect().left).toBeGreaterThanOrEqual(
				name.getBoundingClientRect().right - 1,
			);
			void expect(issue.getBoundingClientRect().right).toBeLessThanOrEqual(
				left + scroller.clientWidth + 1,
			);
		});
		const path = within(name).getByText(NESTED_PATH);
		await expect(path.getBoundingClientRect().height).toBeGreaterThan(
			Number.parseFloat(getComputedStyle(path).lineHeight) * 2,
		);
		await expect(path.scrollHeight).toBeLessThanOrEqual(path.clientHeight + 1);
		await expect(path.scrollWidth).toBeLessThanOrEqual(path.clientWidth + 1);
		await expect(within(issue).getByText("9,876")).toBeVisible();
		const box = issue.getBoundingClientRect();
		await expect(
			issue.contains(document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2)),
		).toBe(true);
		await userEvent.hover(issue);
		await expect(await settledPopup()).toHaveTextContent("9876 issues opened");
		await userEvent.unhover(issue);
		await waitFor(() => {
			void expect(screen.queryByRole("tooltip")).toBeNull();
		});
	},
};
