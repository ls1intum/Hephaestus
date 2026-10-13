import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import { ada, OVERVIEW, readyOverview, WORK_LOG } from "@/stories/activity-story-data";
import { withProvider, withStandardPage } from "@/stories/decorators";

import { ActivityTiles } from "./ActivityTiles";
import { memberRepositoryState, PersonActivitySections } from "./PersonActivitySections";

const meta = {
	component: PersonActivitySections,
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: {
		level: 2,
		period: { kind: "preset", preset: "90d" },
		summary: <ActivityTiles state={readyOverview(OVERVIEW)} providerType="GITHUB" />,
		repositories: memberRepositoryState(readyOverview(OVERVIEW)),
		workLog: {
			status: "ready",
			stale: false,
			items: WORK_LOG,
			hasMore: false,
			isLoadingMore: false,
			onLoadMore: fn(),
			onCopy: fn(async () => {
				/* the copy is the route's */
			}),
		},
		providerType: "GITHUB",
		subject: { people: "one", login: ada.login },
	},
} satisfies Meta<typeof PersonActivitySections>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		const headings = canvas
			.getAllByRole("heading", { level: 2 })
			.map((heading) => heading.textContent);
		await expect(headings).toStrictEqual(["Last 90 days", "Repositories", "Timeline"]);
	},
};

/** The repositories wait for the counts; the period's title and the timeline do not. */
export const Loading: Story = {
	args: {
		summary: <ActivityTiles state={{ status: "loading" }} providerType="GITHUB" />,
		repositories: undefined,
		workLog: { status: "loading" },
	},
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("heading", { name: "Repositories" })).not.toBeInTheDocument();
		await expect(canvas.getByRole("heading", { name: "Timeline" })).toBeVisible();
	},
};

const onRetry = fn();

export const Failed: Story = {
	args: {
		summary: (
			<ActivityTiles
				state={{ status: "error", error: new Error("Network down"), onRetry }}
				providerType="GITHUB"
			/>
		),
		repositories: undefined,
		workLog: { status: "error", error: new Error("Network down"), onRetry },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getAllByRole("alert")).toHaveLength(2);
	},
};

export const GitLab: Story = {
	decorators: [withProvider("GITLAB")],
	args: {
		providerType: "GITLAB",
		summary: <ActivityTiles state={readyOverview(OVERVIEW)} providerType="GITLAB" />,
	},
};

export const GitLabDark: Story = {
	decorators: [withProvider("GITLAB")],
	args: {
		providerType: "GITLAB",
		summary: <ActivityTiles state={readyOverview(OVERVIEW)} providerType="GITLAB" />,
	},
	globals: { theme: "dark" },
};

export const Dark: Story = { globals: { theme: "dark" } };

/** A public projection supplies one real repository and work without private account fields. */
export const PublicReads: Story = {
	args: {
		summary: <p>Published activity in this range</p>,
		repositories: {
			status: "ready",
			stale: false,
			rows: OVERVIEW.repositories.slice(0, 1).map(({ repository, counts }) => ({
				rowKey: String(repository.id),
				repository: { key: repository.key, name: repository.name },
				counts,
			})),
		},
		workLog: {
			status: "ready",
			stale: false,
			items: WORK_LOG.map(({ id, actions, lastOccurredAt, work }) => ({
				id,
				actions,
				lastOccurredAt,
				work: work && {
					type: work.type,
					number: work.number,
					title: work.title,
					state: work.state,
					isDraft: work.isDraft,
					htmlUrl: work.htmlUrl,
					repository: work.repository && { name: work.repository.name },
				},
			})),
			hasMore: false,
			isLoadingMore: false,
			onLoadMore: fn(),
		},
	},
	play: async ({ canvas }) => {
		const repositories = canvas.getByRole("region", { name: "Repositories" });
		await expect(within(repositories).getByRole("table", { name: "Repositories" })).toBeVisible();
		await expect(within(repositories).getAllByRole("row")).toHaveLength(2);
		await expect(canvas.getByRole("heading", { name: "Timeline" })).toBeVisible();
		await expect(
			canvas.queryByRole("button", { name: "Copy as Markdown" }),
		).not.toBeInTheDocument();
	},
};

export const RepositoriesLoading: Story = {
	args: { repositories: { status: "loading" } },
	play: async ({ canvas }) => {
		canvas.getByText("Loading repository counts…");
		await expect(canvas.queryByRole("table", { name: "Repositories" })).not.toBeInTheDocument();
		await expect(canvas.getByRole("heading", { name: "Timeline" })).toBeVisible();
	},
};

export const RepositoriesFailed: Story = {
	args: { repositories: { status: "error", error: new Error("Network down"), onRetry: fn() } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("alert")).toHaveTextContent(
			"We could not load repository counts",
		);
		await expect(canvas.getByRole("heading", { name: "Timeline" })).toBeVisible();
	},
};
